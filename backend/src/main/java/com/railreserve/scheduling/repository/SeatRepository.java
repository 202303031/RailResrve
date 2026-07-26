package com.railreserve.scheduling.repository;

import com.railreserve.scheduling.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByCoachId(Long coachId);
}
