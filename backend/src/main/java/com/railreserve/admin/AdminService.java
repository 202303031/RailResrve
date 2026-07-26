package com.railreserve.admin;

import com.railreserve.admin.web.dto.CoachRequest;
import com.railreserve.admin.web.dto.CreateScheduleRequest;
import com.railreserve.admin.web.dto.CreateTrainRequest;
import com.railreserve.admin.web.dto.RouteStopRequest;
import com.railreserve.admin.web.dto.ScheduleResponse;
import com.railreserve.admin.web.dto.TrainResponse;
import com.railreserve.catalog.domain.Station;
import com.railreserve.catalog.domain.Train;
import com.railreserve.catalog.repository.RouteStopRepository;
import com.railreserve.catalog.repository.StationRepository;
import com.railreserve.catalog.repository.TrainRepository;
import com.railreserve.common.exception.ConflictException;
import com.railreserve.common.exception.ErrorCode;
import com.railreserve.common.exception.ResourceNotFoundException;
import com.railreserve.catalog.domain.RouteStop;
import com.railreserve.scheduling.domain.BerthType;
import com.railreserve.scheduling.domain.Coach;
import com.railreserve.scheduling.domain.Schedule;
import com.railreserve.scheduling.domain.Seat;
import com.railreserve.scheduling.domain.SeatInventory;
import com.railreserve.scheduling.repository.CoachRepository;
import com.railreserve.scheduling.repository.ScheduleRepository;
import com.railreserve.scheduling.repository.SeatInventoryRepository;
import com.railreserve.scheduling.repository.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private static final BerthType[] BERTH_CYCLE = {
            BerthType.LOWER, BerthType.MIDDLE, BerthType.UPPER, BerthType.LOWER,
            BerthType.MIDDLE, BerthType.UPPER, BerthType.SIDE_LOWER, BerthType.SIDE_UPPER};

    private final StationRepository stationRepository;
    private final TrainRepository trainRepository;
    private final RouteStopRepository routeStopRepository;
    private final ScheduleRepository scheduleRepository;
    private final CoachRepository coachRepository;
    private final SeatRepository seatRepository;
    private final SeatInventoryRepository seatInventoryRepository;

    public AdminService(StationRepository stationRepository, TrainRepository trainRepository,
                        RouteStopRepository routeStopRepository, ScheduleRepository scheduleRepository,
                        CoachRepository coachRepository, SeatRepository seatRepository,
                        SeatInventoryRepository seatInventoryRepository) {
        this.stationRepository = stationRepository;
        this.trainRepository = trainRepository;
        this.routeStopRepository = routeStopRepository;
        this.scheduleRepository = scheduleRepository;
        this.coachRepository = coachRepository;
        this.seatRepository = seatRepository;
        this.seatInventoryRepository = seatInventoryRepository;
    }

    @Transactional
    public TrainResponse createTrain(CreateTrainRequest request) {
        if (trainRepository.findByNumber(request.number()).isPresent()) {
            throw new ConflictException(ErrorCode.RESOURCE_CONFLICT, "Train number already exists");
        }
        Train train = trainRepository.save(new Train(request.number(), request.name(), request.type()));
        int order = 1;
        for (RouteStopRequest stop : request.stops()) {
            Station station = stationRepository.findByCodeIgnoreCase(stop.stationCode())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STATION_NOT_FOUND,
                            "Station " + stop.stationCode() + " not found"));
            routeStopRepository.save(new RouteStop(train, station, order++,
                    stop.arrivalTime(), stop.departureTime(), stop.distanceKm()));
        }
        return new TrainResponse(train.getId(), train.getNumber(), train.getName(), train.getType());
    }

    @Transactional
    public ScheduleResponse createSchedule(CreateScheduleRequest request) {
        Train train = trainRepository.findById(request.trainId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Train " + request.trainId() + " not found"));
        Schedule schedule = scheduleRepository.save(new Schedule(train, request.journeyDate()));
        int totalSeats = 0;
        for (CoachRequest coachRequest : request.coaches()) {
            Coach coach = coachRepository.save(
                    new Coach(schedule, coachRequest.code(), coachRequest.travelClass(), coachRequest.totalSeats()));
            for (int n = 1; n <= coachRequest.totalSeats(); n++) {
                seatRepository.save(new Seat(coach, String.valueOf(n), BERTH_CYCLE[(n - 1) % BERTH_CYCLE.length]));
            }
            seatInventoryRepository.save(new SeatInventory(schedule, coach, coachRequest.totalSeats()));
            totalSeats += coachRequest.totalSeats();
        }
        return new ScheduleResponse(schedule.getId(), train.getNumber(), schedule.getJourneyDate(),
                request.coaches().size(), totalSeats);
    }
}
