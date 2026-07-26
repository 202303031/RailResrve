package com.railreserve.booking.web.dto;

import com.railreserve.booking.domain.Gender;
import com.railreserve.booking.domain.PassengerStatus;

public record PassengerView(
        String name,
        int age,
        Gender gender,
        PassengerStatus status,
        String seatNumber,
        String coachCode) {
}
