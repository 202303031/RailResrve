package com.railreserve.security;

import com.railreserve.AbstractIntegrationTest;
import com.railreserve.booking.domain.Gender;
import com.railreserve.booking.repository.BookingRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The spec-required authorization proof. Two dimensions are enforced:
 * <ul>
 *   <li><b>Ownership</b> — user A can neither read nor cancel user B's booking; the API returns
 *       404 (not 403) so it never even reveals that someone else's booking exists.</li>
 *   <li><b>Role</b> — the admin endpoints reject a USER with 403 and admit an ADMIN with 200,
 *       enforced by both the URL rule and the method-level {@code @PreAuthorize}.</li>
 * </ul>
 */
@Transactional
class AuthorizationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataFactory data;
    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;

    private String confirmBooking(BookableSchedule fixture) {
        HoldRequest request = new HoldRequest(fixture.scheduleId(), fixture.coachId(),
                List.of(fixture.seatIds().get(0)),
                List.of(new PassengerRequest("Owner", 30, Gender.MALE)));
        Long holdId = bookingService.hold(request, fixture.userId(), null).holdId();
        bookingService.confirm(holdId, "tok", fixture.userId());
        return bookingRepository.findById(holdId).orElseThrow().getPnr();
    }

    @Test
    void userCannotReadAnotherUsersBooking() throws Exception {
        BookableSchedule fixture = data.bookableSchedule("azr", 2);
        String pnr = confirmBooking(fixture);
        Long attacker = data.user("attacker-read@example.com").getId();

        mockMvc.perform(get("/api/v1/bookings/{pnr}", pnr).with(MockJwt.user(attacker)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void userCannotCancelAnotherUsersBooking() throws Exception {
        BookableSchedule fixture = data.bookableSchedule("azc", 2);
        String pnr = confirmBooking(fixture);
        Long attacker = data.user("attacker-cancel@example.com").getId();

        mockMvc.perform(delete("/api/v1/bookings/{pnr}", pnr).with(MockJwt.user(attacker)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BOOKING_NOT_FOUND"));

        // and the booking is untouched — the real owner can still see it
        mockMvc.perform(get("/api/v1/bookings/{pnr}", pnr).with(MockJwt.user(fixture.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void aUserRoleCannotReachAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/bookings").with(MockJwt.user(1L)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void anAdminRoleCanReachAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/bookings").with(MockJwt.admin(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void adminEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/bookings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }
}
