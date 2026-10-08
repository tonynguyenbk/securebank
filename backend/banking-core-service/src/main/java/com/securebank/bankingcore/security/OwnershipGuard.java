package com.securebank.bankingcore.security;

import com.securebank.bankingcore.domain.Account;
import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import com.securebank.common.security.AuthenticatedUser;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

/**
 * Resource-level authorization for customer endpoints. Roles are enforced by {@code @PreAuthorize}; this
 * helper answers "does this customer own / take part in this resource?" using only the JWT subject — never
 * an ID supplied by the client (spec §23).
 */
@Component
public class OwnershipGuard {

    /** 403 ACCOUNT_NOT_OWNED unless the account belongs to the caller (account endpoints, transfer source). */
    public void requireOwner(AuthenticatedUser user, Account account) {
        if (!isOwner(user.userId(), account.getCustomer().getUserId())) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_OWNED);
        }
    }

    public void requireOwner(AuthenticatedUser user, UUID ownerUserId) {
        if (!isOwner(user.userId(), ownerUserId)) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_OWNED);
        }
    }

    public boolean isOwner(UUID callerUserId, UUID ownerUserId) {
        return callerUserId != null && callerUserId.equals(ownerUserId);
    }

    /**
     * A customer may read a transaction only as a party: as sender always, as recipient only when it moved
     * money (REJECTED attempts are private to the sender). Non-parties get 404 so existence is not leaked.
     */
    public boolean canViewTransaction(Collection<UUID> callerAccountIds, UUID sourceAccountId,
                                      UUID destinationAccountId, boolean rejected) {
        if (callerAccountIds.contains(sourceAccountId)) {
            return true;
        }
        return !rejected && destinationAccountId != null && callerAccountIds.contains(destinationAccountId);
    }
}
