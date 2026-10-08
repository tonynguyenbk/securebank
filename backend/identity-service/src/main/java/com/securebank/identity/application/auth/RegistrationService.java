package com.securebank.identity.application.auth;

import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import com.securebank.common.events.AuditEvent;
import com.securebank.common.events.Events;
import com.securebank.common.events.Topics;
import com.securebank.common.events.UserRegisteredEvent;
import com.securebank.common.outbox.OutboxWriter;
import com.securebank.common.security.Role;
import com.securebank.identity.application.audit.IdentityAuditRecorder;
import com.securebank.identity.application.dto.RegisterRequest;
import com.securebank.identity.application.dto.UserResponse;
import com.securebank.identity.domain.RoleEntity;
import com.securebank.identity.domain.User;
import com.securebank.identity.repository.RoleRepository;
import com.securebank.identity.repository.UserRepository;
import com.securebank.identity.security.ClientInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Customer self-registration: user row, UserRegisteredEvent and USER_REGISTERED audit commit together. */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final OutboxWriter outbox;
    private final IdentityAuditRecorder audit;
    private final Clock clock;

    public RegistrationService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                               OutboxWriter outbox, IdentityAuditRecorder audit, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.outbox = outbox;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public UserResponse register(RegisterRequest request, ClientInfo client) {
        String username = request.username();
        if (users.existsByUsername(username)) {
            throw new ApiException(ErrorCode.AUTH_USERNAME_TAKEN);
        }
        RoleEntity customer = roles.findByName(Role.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Role CUSTOMER missing (Flyway V1)"));
        String phone = request.phone() == null || request.phone().isBlank() ? null : request.phone();
        User user = new User(UUID.randomUUID(), username, passwordEncoder.encode(request.password()),
                request.fullName().trim(), request.email().trim(), phone, Set.of(customer), clock.instant());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent registration of the same username.
            throw new ApiException(ErrorCode.AUTH_USERNAME_TAKEN);
        }

        UserRegisteredEvent event = new UserRegisteredEvent(Events.newId(), UserRegisteredEvent.TYPE,
                Events.VERSION_1, Events.now(), user.getId(), user.getUsername(), user.getFullName(),
                user.getEmail(), user.getPhone());
        outbox.append("USER", user.getId(), Topics.USER_REGISTERED, user.getId().toString(), event);
        audit.record(IdentityAuditRecorder.USER_REGISTERED, AuditEvent.OUTCOME_SUCCESS, user, user.getId(),
                Map.of("username", user.getUsername(), "roles", user.roleNames().stream().map(Enum::name).toList()),
                client.ipAddress());

        log.info("User registered: userId={} role=CUSTOMER", user.getId());
        return UserResponse.from(user);
    }
}
