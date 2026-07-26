package com.railreserve.payment;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.railreserve.TestcontainersConfiguration;
import com.railreserve.booking.domain.BookingStatus;
import com.railreserve.booking.domain.Gender;
import com.railreserve.booking.repository.BookingRepository;
import com.railreserve.booking.service.BookingService;
import com.railreserve.booking.web.dto.HoldRequest;
import com.railreserve.booking.web.dto.PassengerRequest;
import com.railreserve.common.exception.ApiException;
import com.railreserve.common.exception.ErrorCode;
import com.railreserve.payment.domain.Payment;
import com.railreserve.payment.domain.PaymentStatus;
import com.railreserve.payment.repository.PaymentRepository;
import com.railreserve.support.BookableSchedule;
import com.railreserve.support.TestDataFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives the <b>real</b> {@link com.railreserve.payment.gateway.HttpPaymentGateway} against a
 * WireMock server standing in for the external PSP, proving the confirm saga's behaviour on every
 * gateway outcome: approve, decline, timeout, and 5xx — plus that a duplicate confirm never charges
 * twice. This test deliberately does NOT extend {@code AbstractIntegrationTest}: it needs the real
 * HTTP gateway, not the in-process stub that the rest of the suite uses.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, TestDataFactory.class})
@TestPropertySource(properties = {
        "railreserve.booking.scheduler-enabled=false",
        "railreserve.payment.mock-gateway.enabled=false",
        "railreserve.payment.gateway.connect-timeout-ms=600",
        "railreserve.payment.gateway.read-timeout-ms=600"
})
class PaymentSagaIntegrationTest {

    private static final String CHARGES_PATH = "/v1/charges";

    private static final WireMockServer wireMock = new WireMockServer(options().dynamicPort());

    static {
        wireMock.start();
    }

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("railreserve.payment.gateway.base-url", wireMock::baseUrl);
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @Autowired private BookingService bookingService;
    @Autowired private TestDataFactory data;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PaymentRepository paymentRepository;

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    private void stubCharge(String status, String reference) {
        wireMock.stubFor(post(urlPathEqualTo(CHARGES_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"reference\":\"" + reference + "\",\"status\":\"" + status + "\"}")));
    }

    private Long holdOneSeat(String suffix) {
        BookableSchedule fixture = data.bookableSchedule(suffix, 2);
        HoldRequest request = new HoldRequest(fixture.scheduleId(), fixture.coachId(),
                List.of(fixture.seatIds().get(0)),
                List.of(new PassengerRequest("Rider", 30, Gender.MALE)));
        return bookingService.hold(request, fixture.userId(), null).holdId();
    }

    private List<Payment> paymentsFor(Long bookingId) {
        return paymentRepository.findAll().stream()
                .filter(payment -> payment.getBooking().getId().equals(bookingId))
                .toList();
    }

    private BookingStatus statusOf(Long bookingId) {
        return bookingRepository.findById(bookingId).orElseThrow().getStatus();
    }

    @Test
    void anApprovedChargeConfirmsTheBookingAndRecordsASuccessPayment() {
        stubCharge("APPROVED", "ch_ok");
        Long holdId = holdOneSeat("wmok");

        bookingService.confirm(holdId, "tok", data(holdId));

        assertThat(statusOf(holdId)).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(paymentsFor(holdId))
                .singleElement()
                .satisfies(payment -> {
                    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
                    assertThat(payment.getGatewayRef()).isEqualTo("ch_ok");
                });
        wireMock.verify(exactly(1), postRequestedFor(urlPathEqualTo(CHARGES_PATH)));
    }

    @Test
    void aDeclinedChargeLeavesTheBookingHeldAndRecordsAFailedPayment() {
        stubCharge("DECLINED", "ch_dec");
        Long holdId = holdOneSeat("wmdec");

        assertThatThrownBy(() -> bookingService.confirm(holdId, "tok_decline", data(holdId)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_FAILED);

        assertThat(statusOf(holdId)).isEqualTo(BookingStatus.HELD);
        assertThat(paymentsFor(holdId)).singleElement()
                .satisfies(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED));
    }

    @Test
    void aGatewayTimeoutStopsConfirmationAndLeavesTheBookingHeld() {
        wireMock.stubFor(post(urlPathEqualTo(CHARGES_PATH)).willReturn(aResponse()
                .withFixedDelay(1500) // exceeds the 600ms read timeout
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"reference\":\"ch_slow\",\"status\":\"APPROVED\"}")));
        Long holdId = holdOneSeat("wmto");

        assertThatThrownBy(() -> bookingService.confirm(holdId, "tok", data(holdId)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_FAILED);

        // Indeterminate outcome: we did not confirm, and recorded no success payment.
        assertThat(statusOf(holdId)).isEqualTo(BookingStatus.HELD);
        assertThat(paymentsFor(holdId)).noneMatch(p -> p.getStatus() == PaymentStatus.SUCCESS);
    }

    @Test
    void aGatewayServerErrorStopsConfirmation() {
        wireMock.stubFor(post(urlPathEqualTo(CHARGES_PATH))
                .willReturn(aResponse().withStatus(500)));
        Long holdId = holdOneSeat("wm500");

        assertThatThrownBy(() -> bookingService.confirm(holdId, "tok", data(holdId)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_FAILED);

        assertThat(statusOf(holdId)).isEqualTo(BookingStatus.HELD);
    }

    @Test
    void aDuplicateConfirmNeverChargesTwice() {
        stubCharge("APPROVED", "ch_once");
        Long holdId = holdOneSeat("wmdup");
        Long userId = data(holdId);

        bookingService.confirm(holdId, "tok", userId);
        // A second confirm (e.g. a client retry) is an idempotent replay: already CONFIRMED, so the
        // saga short-circuits before charging.
        bookingService.confirm(holdId, "tok", userId);

        assertThat(statusOf(holdId)).isEqualTo(BookingStatus.CONFIRMED);
        wireMock.verify(exactly(1), postRequestedFor(urlEqualTo(CHARGES_PATH)));
        assertThat(paymentsFor(holdId)).singleElement()
                .satisfies(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS));
    }

    /** The owning user id of a booking (holds are created for the fixture's user). */
    private Long data(Long bookingId) {
        return bookingRepository.findById(bookingId).orElseThrow().getUser().getId();
    }
}
