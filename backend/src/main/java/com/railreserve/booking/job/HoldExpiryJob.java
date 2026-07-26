package com.railreserve.booking.job;

import com.railreserve.booking.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically returns the seats of expired holds to inventory. Disabled in tests
 * (which call {@code BookingService.expireStaleHolds()} directly) via the
 * {@code railreserve.booking.scheduler-enabled} property.
 */
@Component
@ConditionalOnProperty(name = "railreserve.booking.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);

    private final BookingService bookingService;

    public HoldExpiryJob(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelayString = "${railreserve.booking.expiry-sweep-ms:15000}")
    public void sweep() {
        int expired = bookingService.expireStaleHolds();
        if (expired > 0) {
            log.info("Hold expiry sweep released {} stale booking hold(s)", expired);
        }
    }
}
