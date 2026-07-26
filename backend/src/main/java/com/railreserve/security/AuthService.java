package com.railreserve.security;

import com.railreserve.common.exception.ConflictException;
import com.railreserve.common.exception.ErrorCode;
import com.railreserve.security.web.dto.LoginRequest;
import com.railreserve.security.web.dto.RefreshRequest;
import com.railreserve.security.web.dto.RegisterRequest;
import com.railreserve.security.web.dto.RegisterResponse;
import com.railreserve.security.web.dto.TokenResponse;
import com.railreserve.user.domain.AppUser;
import com.railreserve.user.domain.UserRole;
import com.railreserve.user.exception.UnauthenticatedException;
import com.railreserve.user.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException(ErrorCode.DUPLICATE_EMAIL, "Email is already registered");
        }
        AppUser user = new AppUser(request.email(), passwordEncoder.encode(request.password()),
                request.fullName(), request.phone(), UserRole.USER);
        userRepository.save(user);
        return new RegisterResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        AppUser user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(RefreshRequest request) {
        Jwt refreshToken = jwtService.parseRefreshToken(request.refreshToken());
        Long userId = Long.valueOf(refreshToken.getSubject());
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthenticatedException("User no longer exists"));
        return issueTokens(user);
    }

    private TokenResponse issueTokens(AppUser user) {
        return new TokenResponse(jwtService.createAccessToken(user), jwtService.createRefreshToken(user),
                "Bearer", jwtService.accessTtlSeconds());
    }
}
