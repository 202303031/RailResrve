package com.railreserve.security;

import com.railreserve.user.domain.AppUser;
import com.railreserve.user.exception.UnauthenticatedException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Mints and validates JWTs. Access and refresh tokens are both HS256-signed and carry a
 * {@code type} claim so an access token can never be used as a refresh token (and vice versa):
 * the resource server only accepts {@code type=access}; refresh accepts only {@code type=refresh}.
 */
@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder refreshTokenDecoder;
    private final SecurityProperties properties;

    public JwtService(JwtEncoder jwtEncoder,
                      @Qualifier("refreshTokenDecoder") JwtDecoder refreshTokenDecoder,
                      SecurityProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokenDecoder = refreshTokenDecoder;
        this.properties = properties;
    }

    public String createAccessToken(AppUser user) {
        return create(user, "access", properties.accessTtlSeconds());
    }

    public String createRefreshToken(AppUser user) {
        return create(user, "refresh", properties.refreshTtlSeconds());
    }

    public long accessTtlSeconds() {
        return properties.accessTtlSeconds();
    }

    private String create(AppUser user, String type, long ttlSeconds) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("railreserve")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("type", type)
                .claim("email", user.getEmail())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Jwt parseRefreshToken(String token) {
        Jwt jwt;
        try {
            jwt = refreshTokenDecoder.decode(token);
        } catch (JwtException e) {
            throw new UnauthenticatedException("Invalid or expired refresh token");
        }
        if (!"refresh".equals(jwt.getClaimAsString("type"))) {
            throw new UnauthenticatedException("Provided token is not a refresh token");
        }
        return jwt;
    }
}
