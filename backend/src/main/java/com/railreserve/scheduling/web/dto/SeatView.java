package com.railreserve.scheduling.web.dto;

import com.railreserve.scheduling.domain.BerthType;

/** One seat in a coach with whether it can currently be held. */
public record SeatView(Long seatId, String seatNumber, BerthType berthType, boolean available) {
}
