package com.railreserve.catalog.web.dto;

import com.railreserve.scheduling.domain.TravelClass;

/** Seats currently available in a given travel class, summarised for search results. */
public record ClassAvailability(TravelClass travelClass, long availableSeats) {
}
