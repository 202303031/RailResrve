package com.railreserve.security.web.dto;

import com.railreserve.user.domain.UserRole;

public record RegisterResponse(Long userId, String email, String fullName, UserRole role) {
}
