package com.railreserve.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunables for the booking engine.
 *
 * @param holdTtlSeconds how long a seat hold survives before the sweep job expires it
 * @param lockStrategy   which {@code SeatLockStrategy} bean to use ("optimistic" | "pessimistic")
 * @param maxLockRetries how many times to retry a whole hold on an optimistic-lock conflict
 * @param expirySweepMs  how often the expiry sweep runs
 * @param schedulerEnabled whether the background expiry sweep runs (disabled in tests)
 */
@ConfigurationProperties(prefix = "railreserve.booking")
public record BookingProperties(
        @DefaultValue("600") int holdTtlSeconds,
        @DefaultValue("optimistic") String lockStrategy,
        @DefaultValue("8") int maxLockRetries,
        @DefaultValue("15000") long expirySweepMs,
        @DefaultValue("true") boolean schedulerEnabled) {
}
