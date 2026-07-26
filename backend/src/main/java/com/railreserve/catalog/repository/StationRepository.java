package com.railreserve.catalog.repository;

import com.railreserve.catalog.domain.Station;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StationRepository extends JpaRepository<Station, Long> {

    Optional<Station> findByCodeIgnoreCase(String code);
}
