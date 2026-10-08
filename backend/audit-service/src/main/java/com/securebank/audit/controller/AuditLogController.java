package com.securebank.audit.controller;

import com.securebank.audit.application.AuditQueryService;
import com.securebank.audit.application.dto.AuditLogDetailView;
import com.securebank.audit.application.dto.AuditLogFilter;
import com.securebank.audit.application.dto.AuditLogView;
import com.securebank.audit.domain.AuditScope;
import com.securebank.common.error.ApiError;
import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import com.securebank.common.security.CurrentUser;
import com.securebank.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit/logs")
@Tag(name = "Audit logs", description = "Read-only access to the append-only audit trail")
@ApiResponse(responseCode = "401", description = "Missing or invalid access token",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "403", description = "Role not allowed (FORBIDDEN_OPERATION) — CUSTOMER",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class AuditLogController {

    private static final int MAX_SIZE = 100;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final AuditQueryService queries;

    public AuditLogController(AuditQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BANK_STAFF', 'AUDITOR', 'ADMIN')")
    @Operation(summary = "Search audit logs (newest first)",
            description = "Roles: AUDITOR, ADMIN (full); BANK_STAFF (restricted: resource types ACCOUNT, TRANSACTION, "
                    + "FRAUD_ALERT only and ipAddress omitted). from is inclusive, to exclusive (ISO instants).")
    @ApiResponse(responseCode = "200", description = "Page of audit logs")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<AuditLogView> search(
            @Parameter(description = "username contains (case-insensitive)") @RequestParam(required = false) String actor,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @Parameter(example = "2026-10-08T00:00:00Z") @RequestParam(required = false) Instant from,
            @Parameter(example = "2026-10-09T00:00:00Z") @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "page must be >= 0 and size between 1 and 100.");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "from must be before to.");
        }
        var filter = new AuditLogFilter(actor, actorUserId, action, resourceType, resourceId, from, to, correlationId);
        return queries.search(filter, scope(), PageRequest.of(page, size, NEWEST_FIRST));
    }

    @GetMapping("/actions")
    @PreAuthorize("hasAnyRole('BANK_STAFF', 'AUDITOR', 'ADMIN')")
    @Operation(summary = "Distinct recorded actions (filter dropdown)",
            description = "Roles: AUDITOR, ADMIN, BANK_STAFF (BANK_STAFF sees actions of its visible resource types).")
    public List<String> actions() {
        return queries.actions(scope());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('BANK_STAFF', 'AUDITOR', 'ADMIN')")
    @Operation(summary = "Audit log detail with before/after state",
            description = "Roles: AUDITOR, ADMIN; BANK_STAFF only for its visible resource types (others → 404).")
    @ApiResponse(responseCode = "200", description = "Audit log detail")
    @ApiResponse(responseCode = "404", description = "AUDIT_LOG_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public AuditLogDetailView get(@PathVariable UUID id) {
        return queries.get(id, scope());
    }

    private static AuditScope scope() {
        return AuditScope.of(CurrentUser.require());
    }
}
