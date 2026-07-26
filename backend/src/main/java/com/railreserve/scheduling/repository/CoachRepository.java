package com.railreserve.scheduling.repository;

import com.railreserve.scheduling.domain.Coach;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoachRepository extends JpaRepository<Coach, Long> {

    List<Coach> findByScheduleId(Long scheduleId);
}
