package com.railreserve.scheduling.service;

import com.railreserve.booking.domain.HoldStatus;
import com.railreserve.booking.repository.SeatHoldRepository;
import com.railreserve.common.exception.BusinessRuleException;
import com.railreserve.common.exception.ErrorCode;
import com.railreserve.common.exception.ResourceNotFoundException;
import com.railreserve.scheduling.domain.Coach;
import com.railreserve.scheduling.domain.Schedule;
import com.railreserve.scheduling.domain.TravelClass;
import com.railreserve.scheduling.repository.CoachRepository;
import com.railreserve.scheduling.repository.ScheduleRepository;
import com.railreserve.scheduling.repository.SeatInventoryRepository;
import com.railreserve.scheduling.repository.SeatRepository;
import com.railreserve.scheduling.web.dto.AvailabilityResponse;
import com.railreserve.scheduling.web.dto.CoachAvailability;
import com.railreserve.scheduling.web.dto.SeatMapResponse;
import com.railreserve.scheduling.web.dto.SeatView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AvailabilityService {

    private final ScheduleRepository scheduleRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final CoachRepository coachRepository;
    private final SeatRepository seatRepository;
    private final SeatHoldRepository seatHoldRepository;

    public AvailabilityService(ScheduleRepository scheduleRepository,
                               SeatInventoryRepository seatInventoryRepository,
                               CoachRepository coachRepository,
                               SeatRepository seatRepository,
                               SeatHoldRepository seatHoldRepository) {
        this.scheduleRepository = scheduleRepository;
        this.seatInventoryRepository = seatInventoryRepository;
        this.coachRepository = coachRepository;
        this.seatRepository = seatRepository;
        this.seatHoldRepository = seatHoldRepository;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(Long scheduleId, TravelClass travelClass) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SCHEDULE_NOT_FOUND,
                        "Schedule " + scheduleId + " not found"));

        List<CoachAvailability> coaches = seatInventoryRepository.findCoachAvailability(scheduleId);
        if (travelClass != null) {
            coaches = coaches.stream().filter(c -> c.travelClass() == travelClass).toList();
        }

        return new AvailabilityResponse(
                schedule.getId(),
                schedule.getJourneyDate(),
                schedule.getTrain().getNumber(),
                schedule.getTrain().getName(),
                coaches);
    }

    /**
     * The seat grid for one coach: every seat with whether it is currently free. A seat is taken if
     * it has a live claim (an ACTIVE hold or a CONFIRMED booking). This drives the seat-selection UI;
     * the actual overbooking guarantees still come from the database at hold time, so a seat shown as
     * free here can still be lost to a concurrent booker — the hold request handles that cleanly.
     */
    @Transactional(readOnly = true)
    public SeatMapResponse getSeatMap(Long scheduleId, Long coachId) {
        Coach coach = coachRepository.findById(coachId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Coach " + coachId + " not found"));
        if (!coach.getSchedule().getId().equals(scheduleId)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR, "Coach does not belong to the schedule");
        }

        Set<Long> takenSeatIds = new HashSet<>(seatHoldRepository.findLiveHeldSeatIds(
                scheduleId, List.of(HoldStatus.ACTIVE, HoldStatus.CONFIRMED)));

        List<SeatView> seats = seatRepository.findByCoachId(coachId).stream()
                .sorted((a, b) -> a.getSeatNumber().compareTo(b.getSeatNumber()))
                .map(seat -> new SeatView(seat.getId(), seat.getSeatNumber(), seat.getBerthType(),
                        !takenSeatIds.contains(seat.getId())))
                .toList();

        return new SeatMapResponse(scheduleId, coachId, coach.getCoachCode(), coach.getTravelClass(), seats);
    }
}
