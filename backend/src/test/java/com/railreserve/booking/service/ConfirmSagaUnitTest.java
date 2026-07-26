package com.railreserve.booking.service;

import com.railreserve.booking.config.BookingProperties;
import com.railreserve.booking.domain.BookingStatus;
import com.railreserve.booking.web.dto.ConfirmResponse;
import com.railreserve.common.exception.ApiException;
import com.railreserve.common.exception.ErrorCode;
import com.railreserve.observability.BookingMetrics;
import com.railreserve.payment.PaymentProperties;
import com.railreserve.payment.gateway.ChargeOutcome;
import com.railreserve.payment.gateway.ChargeRequest;
import com.railreserve.payment.gateway.ChargeResult;
import com.railreserve.payment.gateway.PaymentGateway;
import com.railreserve.payment.gateway.PaymentGatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deterministic unit test of the confirm <b>saga</b> orchestration in {@link BookingService},
 * with the executor and gateway mocked. It nails down the branches that are awkward to force in a
 * live test: charge declined, gateway technical failure, and the critical
 * "charged-but-could-not-confirm → compensating refund" path.
 */
class ConfirmSagaUnitTest {

    private static final Long HOLD_ID = 42L;
    private static final Long USER_ID = 7L;
    private static final String PNR = "PNR1234567";
    private static final BigDecimal FARE = new BigDecimal("450.00");

    private BookingCommandExecutor executor;
    private PaymentGateway gateway;
    private BookingService service;

    @BeforeEach
    void setUp() {
        executor = mock(BookingCommandExecutor.class);
        gateway = mock(PaymentGateway.class);
        // maxLockRetries = 0 so a finalize failure is not retried and drops straight into compensation.
        BookingProperties bookingProperties = new BookingProperties(600, "optimistic", 0, 15000, false);
        PaymentProperties paymentProperties = new PaymentProperties("INR",
                new PaymentProperties.Gateway("http://gateway", 2000, 4000),
                new PaymentProperties.MockGateway(false));
        BookingMetrics metrics = new BookingMetrics(new SimpleMeterRegistry());
        service = new BookingService(executor, List.of(), bookingProperties, gateway, paymentProperties, metrics);
    }

    @Test
    void approvedChargeConfirmsAndNeverRefunds() {
        when(executor.prepareConfirm(HOLD_ID, USER_ID)).thenReturn(PreparedConfirm.pending(PNR, FARE));
        when(gateway.charge(any())).thenReturn(new ChargeResult(ChargeOutcome.APPROVED, "ch_1"));
        when(executor.finalizeConfirm(HOLD_ID, USER_ID, "ch_1"))
                .thenReturn(new ConfirmResponse(PNR, BookingStatus.CONFIRMED));

        ConfirmResponse response = service.confirm(HOLD_ID, "tok", USER_ID);

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        verify(gateway, never()).refund(anyString());
    }

    @Test
    void alreadyConfirmedShortCircuitsWithoutCharging() {
        when(executor.prepareConfirm(HOLD_ID, USER_ID))
                .thenReturn(PreparedConfirm.alreadyConfirmed(new ConfirmResponse(PNR, BookingStatus.CONFIRMED)));

        ConfirmResponse response = service.confirm(HOLD_ID, "tok", USER_ID);

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        verify(gateway, never()).charge(any());
        verify(executor, never()).finalizeConfirm(anyLong(), anyLong(), anyString());
    }

    @Test
    void declinedChargeIsRecordedAndNeverConfirms() {
        when(executor.prepareConfirm(HOLD_ID, USER_ID)).thenReturn(PreparedConfirm.pending(PNR, FARE));
        when(gateway.charge(any())).thenReturn(new ChargeResult(ChargeOutcome.DECLINED, "ch_2"));

        assertThatThrownBy(() -> service.confirm(HOLD_ID, "tok_decline", USER_ID))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_FAILED);

        verify(executor).recordDeclinedPayment(HOLD_ID);
        verify(executor, never()).finalizeConfirm(anyLong(), anyLong(), anyString());
        verify(gateway, never()).refund(anyString());
    }

    @Test
    void aTechnicalGatewayFailureStopsConfirmationWithoutRefund() {
        when(executor.prepareConfirm(HOLD_ID, USER_ID)).thenReturn(PreparedConfirm.pending(PNR, FARE));
        when(gateway.charge(any())).thenThrow(new PaymentGatewayException("timeout", null));

        assertThatThrownBy(() -> service.confirm(HOLD_ID, "tok", USER_ID))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_FAILED);

        // Nothing was captured (as far as we could tell), so we do not confirm and do not finalize.
        verify(executor, never()).finalizeConfirm(anyLong(), anyLong(), anyString());
    }

    @Test
    void chargedButConfirmFailsTriggersCompensatingRefund() {
        when(executor.prepareConfirm(HOLD_ID, USER_ID)).thenReturn(PreparedConfirm.pending(PNR, FARE));
        when(gateway.charge(any())).thenReturn(new ChargeResult(ChargeOutcome.APPROVED, "ch_3"));
        when(executor.finalizeConfirm(HOLD_ID, USER_ID, "ch_3"))
                .thenThrow(new OptimisticLockingFailureException("racing expiry won"));

        assertThatThrownBy(() -> service.confirm(HOLD_ID, "tok", USER_ID))
                .isInstanceOf(ApiException.class);

        // The compensation: refund the captured charge and record it, so we never keep money for
        // an unconfirmed booking.
        verify(gateway).refund("ch_3");
        verify(executor).recordCompensatingRefund(HOLD_ID, "ch_3", FARE);
    }

    @Test
    void theChargeUsesThePnrAsIdempotencyKey() {
        when(executor.prepareConfirm(HOLD_ID, USER_ID)).thenReturn(PreparedConfirm.pending(PNR, FARE));
        when(gateway.charge(any())).thenReturn(new ChargeResult(ChargeOutcome.APPROVED, "ch_4"));
        when(executor.finalizeConfirm(eq(HOLD_ID), eq(USER_ID), anyString()))
                .thenReturn(new ConfirmResponse(PNR, BookingStatus.CONFIRMED));

        service.confirm(HOLD_ID, "tok", USER_ID);

        org.mockito.ArgumentCaptor<ChargeRequest> captor = org.mockito.ArgumentCaptor.forClass(ChargeRequest.class);
        verify(gateway).charge(captor.capture());
        assertThat(captor.getValue().idempotencyKey()).isEqualTo(PNR);
        assertThat(captor.getValue().amount()).isEqualByComparingTo(FARE);
        assertThat(captor.getValue().currency()).isEqualTo("INR");
    }
}
