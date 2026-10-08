package com.securebank.bankingcore.application.admin;

import com.securebank.bankingcore.api.AccountAdminResponse;
import com.securebank.bankingcore.application.BusinessCalendar;
import com.securebank.bankingcore.application.audit.Actor;
import com.securebank.bankingcore.application.audit.AuditActions;
import com.securebank.bankingcore.application.audit.AuditRecorder;
import com.securebank.bankingcore.application.view.AccountViewMapper;
import com.securebank.bankingcore.domain.Account;
import com.securebank.bankingcore.domain.AccountStatus;
import com.securebank.bankingcore.infrastructure.persistence.TransactionLockSettings;
import com.securebank.bankingcore.repository.AccountRepository;
import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import com.securebank.common.events.AccountStatusChangedEvent;
import com.securebank.common.events.Events;
import com.securebank.common.events.Topics;
import com.securebank.common.outbox.OutboxWriter;
import com.securebank.common.security.AuthenticatedUser;
import com.securebank.common.web.Masking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Freeze / unfreeze (spec §16). The account row is locked like in a transfer, so a status change and a
 * concurrent transfer are serialized: a transfer that locked the account first completes, and every later
 * one sees the new status. Each change emits AccountStatusChangedEvent + audit (before/after) via the outbox.
 */
@Service
public class AccountStatusService {

    private static final Logger log = LoggerFactory.getLogger(AccountStatusService.class);

    private final AccountRepository accounts;
    private final TransactionLockSettings lockSettings;
    private final OutboxWriter outbox;
    private final AuditRecorder audit;
    private final AccountViewMapper views;
    private final BusinessCalendar calendar;

    public AccountStatusService(AccountRepository accounts, TransactionLockSettings lockSettings, OutboxWriter outbox,
                                AuditRecorder audit, AccountViewMapper views, BusinessCalendar calendar) {
        this.accounts = accounts;
        this.lockSettings = lockSettings;
        this.outbox = outbox;
        this.audit = audit;
        this.views = views;
        this.calendar = calendar;
    }

    @Transactional
    public AccountAdminResponse freeze(AuthenticatedUser actor, UUID accountId, String reason) {
        return change(actor, accountId, AccountStatus.FROZEN, reason, AuditActions.ACCOUNT_FREEZE);
    }

    @Transactional
    public AccountAdminResponse unfreeze(AuthenticatedUser actor, UUID accountId, String reason) {
        return change(actor, accountId, AccountStatus.ACTIVE, reason, AuditActions.ACCOUNT_UNFREEZE);
    }

    private AccountAdminResponse change(AuthenticatedUser actor, UUID accountId, AccountStatus target, String reason,
                                        String action) {
        lockSettings.applyLockTimeout();
        Account account;
        try {
            account = accounts.findByIdForUpdate(accountId)
                    .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));
        } catch (PessimisticLockingFailureException e) {
            throw new ApiException(ErrorCode.ACCOUNT_BUSY, "The account is busy. Please retry.");
        }
        AccountStatus previous = account.getStatus();
        if (previous == AccountStatus.CLOSED) {
            throw new ApiException(ErrorCode.ACCOUNT_CLOSED);
        }
        if (previous == target) {
            throw new ApiException(ErrorCode.ACCOUNT_STATUS_UNCHANGED);
        }
        String trimmedReason = reason.trim();
        account.changeStatus(target, calendar.now());

        var customer = account.getCustomer();
        outbox.append("ACCOUNT", account.getId(), Topics.ACCOUNT_STATUS_CHANGED, account.getId().toString(),
                new AccountStatusChangedEvent(Events.newId(), AccountStatusChangedEvent.TYPE, Events.VERSION_1,
                        calendar.now(), account.getId(), account.getAccountNumber(), customer.getId(),
                        customer.getUserId(), previous.name(), target.name(), trimmedReason, actor.userId()));

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("status", previous.name());
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", target.name());
        after.put("reason", trimmedReason);
        audit.success(Actor.of(actor), action, AuditActions.RESOURCE_ACCOUNT, account.getId(), before, after);

        log.info("Account {} {} -> {} by user={}", Masking.accountNumber(account.getAccountNumber()), previous, target,
                actor.userId());
        return views.toAdmin(account);
    }
}
