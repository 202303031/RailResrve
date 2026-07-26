package com.railreserve.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Payment configuration.
 *
 * @param currency    ISO currency the gateway charges in
 * @param gateway     how to reach the external payment provider
 * @param mockGateway the co-located mock provider used for local/dev runs
 */
@ConfigurationProperties(prefix = "railreserve.payment")
public record PaymentProperties(
        @DefaultValue("INR") String currency,
        @DefaultValue Gateway gateway,
        @DefaultValue MockGateway mockGateway) {

    /**
     * @param baseUrl          base URL of the provider (defaults to this app's own mock service)
     * @param connectTimeoutMs how long to wait establishing a connection
     * @param readTimeoutMs    how long to wait for the response (a slow gateway must not hold a
     *                         request thread — or a DB transaction — open indefinitely)
     */
    public record Gateway(
            @DefaultValue("http://localhost:8080/mock-gateway") String baseUrl,
            @DefaultValue("2000") int connectTimeoutMs,
            @DefaultValue("4000") int readTimeoutMs) {
    }

    /** @param enabled whether the co-located mock provider endpoints are exposed */
    public record MockGateway(@DefaultValue("true") boolean enabled) {
    }
}
