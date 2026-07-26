package com.railreserve.booking;

import com.railreserve.AbstractIntegrationTest;
import com.railreserve.booking.domain.Gender;
import com.railreserve.booking.web.dto.ConfirmRequest;
import com.railreserve.booking.web.dto.HoldRequest;
import com.railreserve.booking.web.dto.PassengerRequest;
import com.railreserve.support.BookableSchedule;
import com.railreserve.support.MockJwt;
import com.railreserve.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class BookingApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TestDataFactory data;

    private BookableSchedule fixture;

    @BeforeEach
    void setUp() {
        fixture = data.bookableSchedule("api", 5);
    }

    private HoldRequest oneSeatHold() {
        return new HoldRequest(fixture.scheduleId(), fixture.coachId(),
                List.of(fixture.seatIds().get(0)),
                List.of(new PassengerRequest("Asha", 28, Gender.FEMALE)));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private long holdIdOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/holdId").longValue();
    }

    @Test
    void holdThenConfirmSucceeds() throws Exception {
        MvcResult held = mockMvc.perform(post("/api/v1/bookings/hold")
                        .with(MockJwt.user(fixture.userId()))
                        .contentType(APPLICATION_JSON).content(json(oneSeatHold())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.holdId").isNumber())
                .andExpect(jsonPath("$.data.expiresAt").isString())
                .andExpect(jsonPath("$.data.totalFare").isNumber())
                .andReturn();

        long holdId = holdIdOf(held);

        mockMvc.perform(post("/api/v1/bookings/confirm")
                        .with(MockJwt.user(fixture.userId()))
                        .contentType(APPLICATION_JSON)
                        .content(json(new ConfirmRequest(holdId, "tok_demo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.pnr").isString());
    }

    @Test
    void repeatingHoldWithSameIdempotencyKeyReturnsTheSameHold() throws Exception {
        String body = json(oneSeatHold());
        long first = holdIdOf(mockMvc.perform(post("/api/v1/bookings/hold")
                        .with(MockJwt.user(fixture.userId())).header("Idempotency-Key", "key-123")
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn());
        long second = holdIdOf(mockMvc.perform(post("/api/v1/bookings/hold")
                        .with(MockJwt.user(fixture.userId())).header("Idempotency-Key", "key-123")
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn());

        assertThat(second).isEqualTo(first);
    }

    @Test
    void holdWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/hold")
                        .contentType(APPLICATION_JSON).content(json(oneSeatHold())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void holdWithNoSeatsReturns400() throws Exception {
        HoldRequest bad = new HoldRequest(fixture.scheduleId(), fixture.coachId(), List.of(),
                List.of(new PassengerRequest("Asha", 28, Gender.FEMALE)));
        mockMvc.perform(post("/api/v1/bookings/hold")
                        .with(MockJwt.user(fixture.userId()))
                        .contentType(APPLICATION_JSON).content(json(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void confirmUnknownHoldReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/confirm")
                        .with(MockJwt.user(fixture.userId()))
                        .contentType(APPLICATION_JSON)
                        .content(json(new ConfirmRequest(9_999_999L, "tok"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("HOLD_NOT_FOUND"));
    }
}
