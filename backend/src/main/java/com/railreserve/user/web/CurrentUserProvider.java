package com.railreserve.user.web;

/**
 * Supplies the id of the user making the current request. Phase 4 resolves it from a request
 * header; Phase 6 swaps in an implementation backed by the Spring Security context, without
 * changing any controller code.
 */
public interface CurrentUserProvider {

    Long requireUserId();
}
