package com.railreserve.support;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Test helper that stamps a MockMvc request with an authenticated JWT, so tests exercise the
 * real security filter chain without minting and signing a token. The {@code sub} claim carries
 * the user id (exactly as production tokens do), which
 * {@link com.railreserve.security.SecurityContextCurrentUserProvider} reads back as the caller.
 */
public final class MockJwt {

    private MockJwt() {
    }

    /** Authenticate the request as a regular USER with the given id. */
    public static RequestPostProcessor user(Long id) {
        return jwt(id, "USER");
    }

    /** Authenticate the request as an ADMIN with the given id. */
    public static RequestPostProcessor admin(Long id) {
        return jwt(id, "ADMIN");
    }

    private static RequestPostProcessor jwt(Long id, String role) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(builder -> builder.subject(String.valueOf(id)).claim("role", role))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
