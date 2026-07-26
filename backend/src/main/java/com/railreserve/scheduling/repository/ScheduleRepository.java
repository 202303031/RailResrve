package com.railreserve.scheduling.repository;

import com.railreserve.scheduling.domain.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
}
