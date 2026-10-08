package com.securebank.audit.application;

import com.securebank.audit.application.dto.AuditLogDetailView;
import com.securebank.audit.application.dto.AuditLogFilter;
import com.securebank.audit.application.dto.AuditLogView;
import com.securebank.audit.domain.AuditLog;
import com.securebank.audit.domain.AuditScope;
import com.securebank.audit.repository.AuditLogRepository;
import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import com.securebank.common.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Audit search. The caller's {@link AuditScope} is applied in the query itself (server-side restriction). */
@Service
@Transactional(readOnly = true)
public class AuditQueryService {

    private final AuditLogRepository logs;

    public AuditQueryService(AuditLogRepository logs) {
        this.logs = logs;
    }

    public PageResponse<AuditLogView> search(AuditLogFilter filter, AuditScope scope, Pageable pageable) {
        return PageResponse.of(logs.findAll(specification(filter, scope), pageable), a -> AuditLogView.from(a, scope));
    }

    /** A record outside the caller's scope is reported as not found, so its existence is not leaked. */
    public AuditLogDetailView get(UUID id, AuditScope scope) {
        AuditLog log = logs.findById(id)
                .filter(a -> scope.canSee(a.getResourceType()))
                .orElseThrow(() -> new ApiException(ErrorCode.AUDIT_LOG_NOT_FOUND));
        return AuditLogDetailView.from(log, scope);
    }

    public List<String> actions(AuditScope scope) {
        return scope == AuditScope.FULL
                ? logs.findDistinctActions()
                : logs.findDistinctActionsForResourceTypes(AuditScope.RESTRICTED_RESOURCE_TYPES);
    }

    static Specification<AuditLog> specification(AuditLogFilter f, AuditScope scope) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (scope == AuditScope.RESTRICTED) {
                p.add(root.get("resourceType").in(AuditScope.RESTRICTED_RESOURCE_TYPES));
            }
            if (hasText(f.actor())) {
                p.add(cb.like(cb.lower(root.get("actorUsername")),
                        "%" + escapeLike(f.actor().strip().toLowerCase(Locale.ROOT)) + "%", '\\'));
            }
            if (f.actorUserId() != null) {
                p.add(cb.equal(root.get("actorUserId"), f.actorUserId()));
            }
            if (hasText(f.action())) {
                p.add(cb.equal(root.get("action"), f.action().strip()));
            }
            if (hasText(f.resourceType())) {
                p.add(cb.equal(root.get("resourceType"), f.resourceType().strip()));
            }
            if (hasText(f.resourceId())) {
                p.add(cb.equal(root.get("resourceId"), f.resourceId().strip()));
            }
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), f.from()));
            }
            if (f.to() != null) {
                p.add(cb.lessThan(root.get("occurredAt"), f.to()));
            }
            if (hasText(f.correlationId())) {
                p.add(cb.equal(root.get("correlationId"), f.correlationId().strip()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
