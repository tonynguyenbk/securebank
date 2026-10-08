package com.securebank.bankingcore.application.audit;

import com.securebank.common.security.AuthenticatedUser;
import com.securebank.common.security.Role;

import java.util.UUID;

/** Who performed an audited action. Always derived from the verified JWT (or the event that triggered it). */
public record Actor(UUID userId, String username, String role) {

    public static Actor of(AuthenticatedUser user) {
        return new Actor(user.userId(), user.username(), user.primaryRole().name());
    }

    public static Actor customer(UUID userId, String username) {
        return new Actor(userId, username, Role.CUSTOMER.name());
    }
}
