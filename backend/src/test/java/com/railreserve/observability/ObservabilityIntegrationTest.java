package com.railreserve.observability;

import com.railreserve.AbstractIntegrationTest;
import com.railreserve.booking.domain.Gender;
import com.railreserve.booking.service.BookingService;
import com.railreserve.booking.web.dto.HoldRequest;
import com.railreserve.booking.web.dto.PassengerRequest;
import com.railreserve.common.web.CorrelationIdFilter;
import com.railreserve.support.BookableSchedule;
import com.railreserve.support.TestDataFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Correlation-id propagation on responses and the custom booking metrics. */
@Transactional
class ObservabilityIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private BookingService bookingService;
    @Autowired private TestDataFactory data;
    @Autowired private MeterRegistry meterRegistry;

    @Test
    void everyResponseCarriesAGeneratedCorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/trains/search")
                        .param("from", "AAA").param("to", "BBB").param("date", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER, matchesPattern("[A-Za-z0-9._-]+")));
    }

    @Test
    void anInboundCorrelationIdIsEchoedBack() throws Exception {
        mockMvc.perform(get("/api/v1/trains/search")
                        .header(CorrelationIdFilter.HEADER, "trace-abc-1")
                        .param("from", "AAA").param("to", "BBB").param("date", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER, "trace-abc-1"));
    }

    @Test
    void confirmingABookingIncrementsTheHeldAndConfirmedCounters() {
        BookableSchedule fixture = data.bookableSchedule("obs", 2);
        double heldBefore = counter("railreserve.bookings.held");
        double confirmedBefore = counter("railreserve.bookings.confirmed");

        HoldRequest request = new HoldRequest(fixture.scheduleId(), fixture.coachId(),
                List.of(fixture.seatIds().get(0)),
                List.of(new PassengerRequest("Metric", 30, Gender.MALE)));
        Long holdId = bookingService.hold(request, fixture.userId(), null).holdId();
        bookingService.confirm(holdId, "tok", fixture.userId());

        assertThat(counter("railreserve.bookings.held") - heldBefore).isEqualTo(1.0);
        assertThat(counter("railreserve.bookings.confirmed") - confirmedBefore).isEqualTo(1.0);
    }

    private double counter(String name) {
        return meterRegistry.get(name).counter().count();
    }
}
