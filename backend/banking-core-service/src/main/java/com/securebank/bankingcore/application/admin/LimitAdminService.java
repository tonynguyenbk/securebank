package com.securebank.bankingcore.application.admin;

import com.securebank.bankingcore.api.AccountLimitsResponse;
import com.securebank.bankingcore.api.UpdateTransferLimitsRequest;
import com.securebank.bankingcore.application.BusinessCalendar;
import com.securebank.bankingcore.application.audit.Actor;
import com.securebank.bankingcore.application.audit.AuditActions;
import com.securebank.bankingcore.application.audit.AuditRecorder;
import com.securebank.bankingcore.application.limits.TransferLimitService;
import com.securebank.bankingcore.application.view.AccountViewMapper;
import com.securebank.bankingcore.domain.TransferLimit;
import com.securebank.bankingcore.repository.AccountRepository;
import com.securebank.bankingcore.repository.TransferLimitRepository;
import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import com.securebank.common.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** PUT /admin/accounts/{id}/limits with an audit record holding the before/after values. */
@Service
public class LimitAdminService {

    private final AccountRepository accounts;
    private final TransferLimitRepository limitRepository;
    private final TransferLimitService limits;
    private final AuditRecorder audit;
    private final AccountViewMapper views;
    private final BusinessCalendar calendar;

    public LimitAdminService(AccountRepository accounts, TransferLimitRepository limitRepository,
                             TransferLimitService limits, AuditRecorder audit, AccountViewMapper views,
                             BusinessCalendar calendar) {
        this.accounts = accounts;
        this.limitRepository = limitRepository;
        this.limits = limits;
        this.audit = audit;
        this.views = views;
        this.calendar = calendar;
    }

    @Transactional
    public AccountLimitsResponse update(AuthenticatedUser actor, UUID accountId, UpdateTransferLimitsRequest request) {
        if (!accounts.existsById(accountId)) {
            throw new ApiException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        TransferLimit limit = limitRepository.findByAccountIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalStateException("No transfer limits for account " + accountId));
        Map<String, Object> before = snapshot(limit);
        limit.update(request.perTransactionLimit(), request.dailyLimit(), actor.userId(), calendar.now());
        audit.success(Actor.of(actor), AuditActions.TRANSFER_LIMIT_UPDATE, AuditActions.RESOURCE_ACCOUNT, accountId,
                before, snapshot(limit));
        return views.toLimits(accountId, limits.usage(limit));
    }

    private static Map<String, Object> snapshot(TransferLimit limit) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("perTransactionLimit", limit.getPerTransactionLimit());
        values.put("dailyLimit", limit.getDailyLimit());
        return values;
    }
}
