package com.railreserve.booking;

import com.railreserve.AbstractIntegrationTest;
import com.railreserve.booking.domain.Booking;
import com.railreserve.booking.domain.BookingPassenger;
import com.railreserve.booking.domain.BookingStatus;
import com.railreserve.booking.domain.Gender;
import com.railreserve.booking.domain.PassengerStatus;
import com.railreserve.booking.repository.BookingRepository;
import com.railreserve.booking.service.BookingService;
import com.railreserve.booking.web.dto.CancelResponse;
import com.railreserve.booking.web.dto.HoldRequest;
import com.railreserve.booking.web.dto.PassengerRequest;
import com.railreserve.scheduling.domain.Coach;
import com.railreserve.scheduling.domain.Schedule;
import com.railreserve.scheduling.domain.SeatInventory;
import com.railreserve.scheduling.repository.CoachRepository;
import com.railreserve.scheduling.repository.ScheduleRepository;
import com.railreserve.scheduling.repository.SeatInventoryRepository;
import com.railreserve.support.BookableSchedule;
import com.railreserve.support.MockJwt;
import com.railreserve.support.TestDataFactory;
import com.railreserve.user.domain.AppUser;
import com.railreserve.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class BookingLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataFactory data;
    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private SeatInventoryRepository seatInventoryRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private CoachRepository coachRepository;
    @Autowired private AppUserRepository appUserRepository;

    private String confirmBooking(BookableSchedule fixture, int seatIndex) {
        HoldRequest request = new HoldRequest(fixture.scheduleId(), fixture.coachId(),
                List.of(fixture.seatIds().get(seatIndex)),
                List.of(new PassengerRequest("Traveller", 30, Gender.MALE)));
        Long holdId = bookingService.hold(request, fixture.userId(), null).holdId();
        bookingService.confirm(holdId, "tok", fixture.userId());
        return bookingRepository.findById(holdId).orElseThrow().getPnr();
    }

    private Booking createWaitlisted(BookableSchedule fixture, int position, String pnr) {
        AppUser user = appUserRepository.findById(fixture.userId()).orElseThrow();
        Schedule schedule = scheduleRepository.findById(fixture.scheduleId()).orElseThrow();
        Coach coach = coachRepository.findById(fixture.coachId()).orElseThrow();
        Booking booking = new Booking(pnr, user, schedule, coach, BookingStatus.WAITLISTED, new BigDecimal("450.00"), null);
        booking.setWaitlistPosition(position);
        booking.addPassenger(new BookingPassenger(null, "Waitlisted", 40, Gender.FEMALE, PassengerStatus.WAITLISTED));
        return bookingRepository.saveAndFlush(booking);
    }

    @Test
    void listAndFetchOwnBooking() throws Exception {
        BookableSchedule fixture = data.bookableSchedule("life", 2);
        String pnr = confirmBooking(fixture, 0);

        mockMvc.perform(get("/api/v1/bookings").with(MockJwt.user(fixture.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].pnr").value(pnr))
                .andExpect(jsonPath("$.data.content[0].status").value("CONFIRMED"));

        mockMvc.perform(get("/api/v1/bookings/{pnr}", pnr).with(MockJwt.user(fixture.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pnr").value(pnr))
                .andExpect(jsonPath("$.data.passengers[0].seatNumber").isString());
    }

    @Test
    void anotherUserCannotFetchYourBooking() throws Exception {
        BookableSchedule fixture = data.bookableSchedule("life2", 2);
        String pnr = confirmBooking(fixture, 0);
        Long otherUserId = data.user("stranger@example.com").getId();

        mockMvc.perform(get("/api/v1/bookings/{pnr}", pnr).with(MockJwt.user(otherUserId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void cancellingWellBeforeDepartureRefunds90PercentAndReleasesTheSeat() {
        BookableSchedule fixture = data.bookableSchedule("cxl", 2, 2, LocalDate.now().plusDays(10));
        String pnr = confirmBooking(fixture, 0);

        CancelResponse response = bookingService.cancel(pnr, fixture.userId());

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(response.refundAmount()).isEqualByComparingTo("405.00"); // 90% of 450
        SeatInventory inventory = seatInventoryRepository
                .findByScheduleIdAndCoachId(fixture.scheduleId(), fixture.coachId()).orElseThrow();
        assertThat(inventory.getAvailableCount()).isEqualTo(2);
        assertThat(inventory.getBookedCount()).isZero();
    }

    @Test
    void cancelViaDeleteEndpoint() throws Exception {
        BookableSchedule fixture = data.bookableSchedule("cxl2", 2, 2, LocalDate.now().plusDays(10));
        String pnr = confirmBooking(fixture, 0);

        mockMvc.perform(delete("/api/v1/bookings/{pnr}", pnr).with(MockJwt.user(fixture.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.refundAmount").isNumber());
    }

    @Test
    void cancellingAConfirmedBookingPromotesTheWaitlist() {
        BookableSchedule fixture = data.bookableSchedule("wlp", 1);
        String confirmedPnr = confirmBooking(fixture, 0);
        createWaitlisted(fixture, 1, "WLPWAIT00001");

        bookingService.cancel(confirmedPnr, fixture.userId());

        Booking promoted = bookingRepository.findByPnr("WLPWAIT00001").orElseThrow();
        assertThat(promoted.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(promoted.getWaitlistPosition()).isNull();
        assertThat(promoted.getPassengers().get(0).getSeat()).isNotNull();
        assertThat(promoted.getPassengers().get(0).getSeat().getId()).isEqualTo(fixture.seatIds().get(0));
    }
}
