package com.railreserve.catalog.service;

import com.railreserve.catalog.repository.ScheduleClassAvailability;
import com.railreserve.catalog.repository.SearchScheduleRow;
import com.railreserve.catalog.repository.TrainSearchRepository;
import com.railreserve.catalog.web.dto.ClassAvailability;
import com.railreserve.catalog.web.dto.TrainSearchResult;
import com.railreserve.common.exception.BusinessRuleException;
import com.railreserve.common.exception.ErrorCode;
import com.railreserve.scheduling.domain.ScheduleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TrainSearchService {

    private static final long MINUTES_PER_DAY = Duration.ofDays(1).toMinutes();

    private final TrainSearchRepository searchRepository;

    public TrainSearchService(TrainSearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    @Transactional(readOnly = true)
    public Page<TrainSearchResult> search(String from, String to, LocalDate date, Pageable pageable) {
        if (from.equalsIgnoreCase(to)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
                    "Origin and destination stations must be different");
        }

        Page<SearchScheduleRow> rows = searchRepository.search(
                from.toUpperCase(), to.toUpperCase(), date, ScheduleStatus.SCHEDULED, pageable);

        List<Long> scheduleIds = rows.map(SearchScheduleRow::scheduleId).getContent();
        Map<Long, List<ClassAvailability>> availabilityBySchedule = loadAvailability(scheduleIds);

        return rows.map(row -> toResult(row, availabilityBySchedule.getOrDefault(row.scheduleId(), List.of())));
    }

    private Map<Long, List<ClassAvailability>> loadAvailability(List<Long> scheduleIds) {
        if (scheduleIds.isEmpty()) {
            return Map.of();
        }
        return searchRepository.classAvailability(scheduleIds).stream()
                .collect(Collectors.groupingBy(
                        ScheduleClassAvailability::scheduleId,
                        Collectors.mapping(
                                a -> new ClassAvailability(a.travelClass(),
                                        a.availableSeats() == null ? 0L : a.availableSeats()),
                                Collectors.toList())));
    }

    private TrainSearchResult toResult(SearchScheduleRow row, List<ClassAvailability> availability) {
        long minutes = Duration.between(row.departureTime(), row.arrivalTime()).toMinutes();
        if (minutes < 0) {
            minutes += MINUTES_PER_DAY; // arrival is on the following day
        }
        return new TrainSearchResult(
                row.scheduleId(), row.trainNumber(), row.trainName(), row.trainType(),
                row.journeyDate(), row.departureTime(), row.arrivalTime(),
                row.distanceKm() == null ? 0 : row.distanceKm(), minutes, availability);
    }
}
