package com.railreserve.catalog.repository;

import com.railreserve.catalog.domain.Train;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrainRepository extends JpaRepository<Train, Long> {

    Optional<Train> findByNumber(String number);
}
