package com.railreserve.scheduling;

import com.railreserve.AbstractIntegrationTest;
import com.railreserve.booking.domain.Gender;
import com.railreserve.booking.service.BookingService;
import com.railreserve.booking.web.dto.HoldRequest;
import com.railreserve.booking.web.dto.PassengerRequest;
import com.railreserve.support.BookableSchedule;
import com.railreserve.support.MockJwt;
import com.railreserve.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class SeatMapIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataFactory data;
    @Autowired private BookingService bookingService;

    @Test
    void seatMapMarksHeldSeatsUnavailable() throws Exception {
        BookableSchedule fixture = data.bookableSchedule("seatmap", 3);
        Long heldSeat = fixture.seatIds().get(0);
        bookingService.hold(new HoldRequest(fixture.scheduleId(), fixture.coachId(), List.of(heldSeat),
                List.of(new PassengerRequest("Taken", 30, Gender.MALE))), fixture.userId(), null);

        mockMvc.perform(get("/api/v1/schedules/{id}/coaches/{coachId}/seats",
                        fixture.scheduleId(), fixture.coachId())
                        .with(MockJwt.user(fixture.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seats", hasSize(3)))
                .andExpect(jsonPath("$.data.seats[?(@.available==false)]", hasSize(1)))
                .andExpect(jsonPath("$.data.seats[?(@.available==true)]", hasSize(2)))
                .andExpect(jsonPath("$.data.coachCode").value("C1"));
    }

    @Test
    void seatMapRequiresAuthentication() throws Exception {
        BookableSchedule fixture = data.bookableSchedule("seatmap2", 2);
        mockMvc.perform(get("/api/v1/schedules/{id}/coaches/{coachId}/seats",
                        fixture.scheduleId(), fixture.coachId()))
                .andExpect(status().isUnauthorized());
    }
}
