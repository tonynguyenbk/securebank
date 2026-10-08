package com.securebank.audit.domain;

import com.securebank.common.security.AuthenticatedUser;
import com.securebank.common.security.Role;

import java.util.Set;

/**
 * What a caller may see (api.md §5): AUDITOR and ADMIN see everything; BANK_STAFF sees only operational
 * resource types and never the client IP address.
 */
public enum AuditScope {
    FULL,
    RESTRICTED;

    public static final Set<String> RESTRICTED_RESOURCE_TYPES = Set.of("ACCOUNT", "TRANSACTION", "FRAUD_ALERT");

    public static AuditScope of(AuthenticatedUser user) {
        if (user.hasAnyRole(Role.AUDITOR, Role.ADMIN)) {
            return FULL;
        }
        if (user.hasRole(Role.BANK_STAFF)) {
            return RESTRICTED;
        }
        throw new IllegalStateException("caller has no audit-reading role");
    }

    public boolean canSee(String resourceType) {
        return this == FULL || RESTRICTED_RESOURCE_TYPES.contains(resourceType);
    }

    public boolean showsIpAddress() {
        return this == FULL;
    }
}
