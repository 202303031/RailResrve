package com.railreserve.catalog;

import com.railreserve.AbstractIntegrationTest;
import com.railreserve.catalog.domain.Station;
import com.railreserve.catalog.domain.Train;
import com.railreserve.catalog.domain.TrainType;
import com.railreserve.scheduling.domain.Coach;
import com.railreserve.scheduling.domain.Schedule;
import com.railreserve.scheduling.domain.TravelClass;
import com.railreserve.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class TrainSearchAndAvailabilityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestDataFactory data;

    private LocalDate journeyDate;
    private Long scheduleId;

    @BeforeEach
    void setUp() {
        Station a = data.station("AAA");
        Station b = data.station("BBB");
        Station c = data.station("CCC");
        Train train = data.train("T1", TrainType.EXPRESS);
        data.routeStop(train, a, 1, null, LocalTime.of(6, 0), 0);
        data.routeStop(train, b, 2, LocalTime.of(8, 0), LocalTime.of(8, 5), 100);
        data.routeStop(train, c, 3, LocalTime.of(10, 0), null, 200);

        journeyDate = LocalDate.now().plusDays(3);
        Schedule schedule = data.schedule(train, journeyDate);
        scheduleId = schedule.getId();

        Coach sleeper = data.coach(schedule, "S1", TravelClass.SLEEPER, 2);
        Coach ac3 = data.coach(schedule, "B1", TravelClass.AC_3_TIER, 1);
        data.seats(sleeper, 2);
        data.seats(ac3, 1);
        data.inventory(schedule, sleeper, 2);
        data.inventory(schedule, ac3, 1);
    }

    @Test
    void searchReturnsMatchingTrainWithPerClassAvailability() throws Exception {
        mockMvc.perform(get("/api/v1/trains/search")
                        .param("from", "AAA").param("to", "BBB").param("date", journeyDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].trainNumber").value("T1"))
                .andExpect(jsonPath("$.data.content[0].distanceKm").value(100))
                .andExpect(jsonPath("$.data.content[0].durationMinutes").value(120))
                .andExpect(jsonPath("$.data.content[0].availability", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].availability[?(@.travelClass=='SL')].availableSeats", contains(2)))
                .andExpect(jsonPath("$.data.content[0].availability[?(@.travelClass=='3A')].availableSeats", contains(1)));
    }

    @Test
    void searchInWrongDirectionReturnsNoResults() throws Exception {
        mockMvc.perform(get("/api/v1/trains/search")
                        .param("from", "CCC").param("to", "AAA").param("date", journeyDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    void searchWithoutDateReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/trains/search").param("from", "AAA").param("to", "BBB"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void searchWithBlankOriginReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/trains/search")
                        .param("from", " ").param("to", "BBB").param("date", journeyDate.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void searchWithSameOriginAndDestinationReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/trains/search")
                        .param("from", "AAA").param("to", "AAA").param("date", journeyDate.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void availabilityReturnsAllCoaches() throws Exception {
        mockMvc.perform(get("/api/v1/schedules/{id}/availability", scheduleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trainNumber").value("T1"))
                .andExpect(jsonPath("$.data.coaches", hasSize(2)))
                .andExpect(jsonPath("$.data.coaches[?(@.coachCode=='S1')].availableCount", contains(2)));
    }

    @Test
    void availabilityCanBeFilteredByTravelClass() throws Exception {
        mockMvc.perform(get("/api/v1/schedules/{id}/availability", scheduleId).param("travelClass", "SL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coaches", hasSize(1)))
                .andExpect(jsonPath("$.data.coaches[0].travelClass").value("SL"));
    }

    @Test
    void availabilityForUnknownScheduleReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/schedules/{id}/availability", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SCHEDULE_NOT_FOUND"));
    }
}
