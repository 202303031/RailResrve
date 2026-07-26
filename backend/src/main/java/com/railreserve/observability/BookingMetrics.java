package com.railreserve.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Custom business metrics for the booking engine, published through Micrometer (scrapeable at
 * {@code /actuator/prometheus}). These answer operational questions plain request metrics can't:
 * how fast are we selling, how much contention are we retrying through, and are payments failing?
 * Rates (bookings/sec, failure rate) are derived from these counters by the monitoring system.
 */
@Component
public class BookingMetrics {

    private final Counter holdsCreated;
    private final Counter bookingsConfirmed;
    private final Counter holdsExpired;
    private final Counter lockRetries;
    private final Counter paymentFailures;

    public BookingMetrics(MeterRegistry registry) {
        this.holdsCreated = Counter.builder("railreserve.bookings.held")
                .description("Seat holds successfully created").register(registry);
        this.bookingsConfirmed = Counter.builder("railreserve.bookings.confirmed")
                .description("Bookings confirmed after a successful payment").register(registry);
        this.holdsExpired = Counter.builder("railreserve.holds.expired")
                .description("Holds expired by the sweep and returned to inventory").register(registry);
        this.lockRetries = Counter.builder("railreserve.lock.retries")
                .description("Optimistic-lock conflicts retried during hold/confirm").register(registry);
        this.paymentFailures = Counter.builder("railreserve.payments.failed")
                .description("Charges that were declined or failed technically").register(registry);
    }

    public void holdCreated() {
        holdsCreated.increment();
    }

    public void bookingConfirmed() {
        bookingsConfirmed.increment();
    }

    public void holdsExpired(int count) {
        if (count > 0) {
            holdsExpired.increment(count);
        }
    }

    public void lockRetry() {
        lockRetries.increment();
    }

    public void paymentFailed() {
        paymentFailures.increment();
    }
}
