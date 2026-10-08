package com.securebank.identity.application.auth;

import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import com.securebank.identity.application.dto.UserResponse;
import com.securebank.identity.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserProfileService {

    private final UserRepository users;

    public UserProfileService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID userId) {
        return users.findWithRolesById(userId)
                .filter(u -> u.isEnabled())
                .map(UserResponse::from)
                // The token is valid but the user is gone/disabled: treat as unauthenticated.
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }
}
