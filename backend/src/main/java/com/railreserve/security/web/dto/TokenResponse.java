package com.railreserve.security.web.dto;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {
}
