package com.securebank.common.security;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Identity derived from a verified access token. This is the only trusted source of the
 * current user's ID and roles — request bodies are never trusted for these values (spec §23).
 */
public record AuthenticatedUser(UUID userId, String username, Set<Role> roles, String tokenId, Instant expiresAt) {

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean hasAnyRole(Role... candidates) {
        for (Role r : candidates) {
            if (roles.contains(r)) {
                return true;
            }
        }
        return false;
    }

    /** Highest-privilege role, used as the "actorRole" in audit events. */
    public Role primaryRole() {
        for (Role r : new Role[]{Role.ADMIN, Role.AUDITOR, Role.BANK_STAFF, Role.CUSTOMER}) {
            if (roles.contains(r)) {
                return r;
            }
        }
        return Role.CUSTOMER;
    }
}
