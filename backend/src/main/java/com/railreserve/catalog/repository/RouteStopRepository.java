package com.railreserve.catalog.repository;

import com.railreserve.catalog.domain.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {
}
