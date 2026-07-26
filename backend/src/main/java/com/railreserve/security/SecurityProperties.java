package com.railreserve.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * JWT signing configuration.
 *
 * @param secret            HS256 signing secret (must be at least 32 bytes)
 * @param accessTtlSeconds  lifetime of an access token
 * @param refreshTtlSeconds lifetime of a refresh token
 */
@ConfigurationProperties(prefix = "railreserve.jwt")
public record SecurityProperties(
        @DefaultValue("railreserve-development-signing-secret-change-me-please-32b+") String secret,
        @DefaultValue("900") long accessTtlSeconds,
        @DefaultValue("1209600") long refreshTtlSeconds) {
}
