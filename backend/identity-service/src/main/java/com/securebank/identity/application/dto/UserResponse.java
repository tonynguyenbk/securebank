package com.securebank.identity.application.dto;

import com.securebank.common.security.Role;
import com.securebank.identity.domain.User;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String fullName,
        String email,
        String phone,
        List<Role> roles,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                user.getPhone(), user.roleNames().stream().sorted().toList(), user.getCreatedAt());
    }
}
