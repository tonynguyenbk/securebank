package com.securebank.identity.application.auth;

import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import com.securebank.common.events.AuditEvent;
import com.securebank.common.security.AuthenticatedUser;
import com.securebank.identity.application.audit.IdentityAuditRecorder;
import com.securebank.identity.application.dto.LoginRequest;
import com.securebank.identity.application.dto.TokenResponse;
import com.securebank.identity.application.dto.UserResponse;
import com.securebank.identity.application.token.AccessTokenIssuer;
import com.securebank.identity.application.token.IssuedAccessToken;
import com.securebank.identity.application.token.IssuedRefreshToken;
import com.securebank.identity.application.token.RefreshTokenService;
import com.securebank.identity.application.token.RefreshTokenService.RotationResult;
import com.securebank.identity.domain.User;
import com.securebank.identity.repository.UserRepository;
import com.securebank.identity.security.ClientInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Login, refresh-token rotation and logout.
 * <p>
 * Transactions are explicit ({@link TransactionTemplate}) because failure paths must still commit
 * something before an error is returned: the LOGIN_FAILURE audit row, and the family revocation on
 * refresh-token reuse.
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokens;
    private final RefreshTokenService refreshTokens;
    private final LoginAttemptLimiter limiter;
    private final AccessTokenDenylist denylist;
    private final IdentityAuditRecorder audit;
    private final TransactionTemplate tx;
    /** Compared against when the username does not exist, so response time does not reveal valid usernames. */
    private final String dummyHash;

    public AuthenticationService(UserRepository users, PasswordEncoder passwordEncoder, AccessTokenIssuer accessTokens,
                                 RefreshTokenService refreshTokens, LoginAttemptLimiter limiter,
                                 AccessTokenDenylist denylist, IdentityAuditRecorder audit,
                                 PlatformTransactionManager transactionManager) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.limiter = limiter;
        this.denylist = denylist;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-equalization");
    }

    public TokenResponse login(LoginRequest request, ClientInfo client) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        Optional<User> found = users.findWithRolesByUsername(username);

        try {
            limiter.checkAllowed(username, client.ipAddress());
        } catch (ApiException rateLimited) {
            recordLoginFailure(found.orElse(null), username, "RATE_LIMITED", client);
            log.warn("Login rate limited: user={} ip={}", found.map(u -> u.getId().toString()).orElse("<unknown>"),
                    client.ipAddress());
            throw rateLimited;
        }

        boolean passwordOk = passwordEncoder.matches(request.password(),
                found.map(User::getPasswordHash).orElse(dummyHash));
        if (found.isEmpty() || !passwordOk || !found.get().isEnabled()) {
            limiter.recordFailure(username, client.ipAddress());
            String reason = found.isEmpty() ? "UNKNOWN_USERNAME"
                    : !passwordOk ? "BAD_CREDENTIALS" : "ACCOUNT_DISABLED";
            recordLoginFailure(found.orElse(null), username, reason, client);
            log.info("Login failed: user={} reason={}", found.map(u -> u.getId().toString()).orElse("<unknown>"), reason);
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        User user = found.get();
        limiter.recordSuccess(username);
        TokenResponse response = tx.execute(status -> {
            IssuedRefreshToken refresh = refreshTokens.issue(user.getId(), client);
            audit.record(IdentityAuditRecorder.LOGIN_SUCCESS, AuditEvent.OUTCOME_SUCCESS, user, user.getId(),
                    Map.of(), client.ipAddress());
            return tokenResponse(user, refresh);
        });
        log.info("Login succeeded: userId={}", user.getId());
        return response;
    }

    public TokenResponse refresh(String rawRefreshToken, ClientInfo client) {
        // Commit first (also for reuse detection, whose revocations must persist), then decide the response.
        RefreshOutcome outcome = tx.execute(status -> {
            RotationResult result = refreshTokens.rotate(rawRefreshToken, client);
            if (result instanceof RotationResult.Rotated rotated) {
                Optional<User> user = users.findWithRolesById(rotated.userId()).filter(User::isEnabled);
                if (user.isEmpty()) {
                    status.setRollbackOnly();
                    return new RefreshOutcome(null, null);
                }
                return new RefreshOutcome(user.get(), rotated.token());
            }
            return new RefreshOutcome(null, null);
        });
        if (outcome == null || outcome.user() == null) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }
        return tokenResponse(outcome.user(), outcome.refreshToken());
    }

    /** Denylists the current access token and revokes the given refresh token (if it belongs to the caller). */
    public void logout(AuthenticatedUser principal, String rawRefreshToken, ClientInfo client) {
        denylist.revoke(principal.tokenId(), principal.expiresAt());
        tx.executeWithoutResult(status -> {
            boolean revoked = refreshTokens.revoke(rawRefreshToken, principal.userId());
            User actor = users.findWithRolesById(principal.userId()).orElse(null);
            audit.record(IdentityAuditRecorder.LOGOUT, AuditEvent.OUTCOME_SUCCESS, actor, principal.userId(),
                    Map.of("refreshTokenRevoked", revoked), client.ipAddress());
        });
        log.info("Logout: userId={}", principal.userId());
    }

    private void recordLoginFailure(User user, String attemptedUsername, String reason, ClientInfo client) {
        try {
            tx.executeWithoutResult(status -> audit.record(IdentityAuditRecorder.LOGIN_FAILURE,
                    AuditEvent.OUTCOME_FAILURE, user, user == null ? null : user.getId(),
                    Map.of("attemptedUsername", attemptedUsername, "reason", reason), client.ipAddress()));
        } catch (RuntimeException e) {
            // Never turn a 401/429 into a 500 because the audit row could not be written.
            log.error("Could not record LOGIN_FAILURE audit event", e);
        }
    }

    private TokenResponse tokenResponse(User user, IssuedRefreshToken refresh) {
        IssuedAccessToken access = accessTokens.issue(user.getId(), user.getUsername(), user.roleNames());
        return new TokenResponse(access.token(), TokenResponse.BEARER, accessTokens.ttl().toSeconds(),
                refresh.rawToken(), refreshTokens.ttl().toSeconds(), UserResponse.from(user));
    }

    private record RefreshOutcome(User user, IssuedRefreshToken refreshToken) {
    }
}
