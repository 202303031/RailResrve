package com.railreserve.booking.repository;

import com.railreserve.booking.domain.BookingPassenger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingPassengerRepository extends JpaRepository<BookingPassenger, Long> {
}
