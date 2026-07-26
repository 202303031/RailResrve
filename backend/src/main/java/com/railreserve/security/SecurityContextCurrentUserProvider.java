package com.railreserve.security;

import com.railreserve.user.exception.UnauthenticatedException;
import com.railreserve.user.web.CurrentUserProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the current user's id from the authenticated JWT (its {@code sub} claim, which we set
 * to the user id). Replaces the Phase 4 header-based stub; controllers are unchanged because they
 * depend only on the {@link CurrentUserProvider} interface.
 */
@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Long requireUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthenticatedException("Authentication required");
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            throw new UnauthenticatedException("Invalid authentication principal");
        }
    }
}
