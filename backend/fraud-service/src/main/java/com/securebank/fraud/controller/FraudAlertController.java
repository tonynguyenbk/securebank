package com.securebank.fraud.controller;

import com.securebank.common.error.ApiError;
import com.securebank.common.security.CurrentUser;
import com.securebank.common.web.PageResponse;
import com.securebank.fraud.application.FraudAlertQueryService;
import com.securebank.fraud.application.FraudAlertReviewService;
import com.securebank.fraud.application.dto.FraudAlertDetail;
import com.securebank.fraud.application.dto.FraudAlertFilter;
import com.securebank.fraud.application.dto.FraudAlertStats;
import com.securebank.fraud.application.dto.FraudAlertSummary;
import com.securebank.fraud.application.dto.ReviewFraudAlertRequest;
import com.securebank.fraud.domain.FraudAlertStatus;
import com.securebank.fraud.domain.RiskLevel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fraud/alerts")
@Tag(name = "Fraud alerts", description = "Alerts raised by the rule engine and their review workflow")
@ApiResponse(responseCode = "401", description = "Missing or invalid access token",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
@ApiResponse(responseCode = "403", description = "Role not allowed (FORBIDDEN_OPERATION)",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class FraudAlertController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "riskScore", "amount", "status");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final FraudAlertQueryService queries;
    private final FraudAlertReviewService reviews;

    public FraudAlertController(FraudAlertQueryService queries, FraudAlertReviewService reviews) {
        this.queries = queries;
        this.reviews = reviews;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BANK_STAFF', 'AUDITOR', 'ADMIN')")
    @Operation(summary = "List fraud alerts",
            description = "Roles: BANK_STAFF, AUDITOR, ADMIN. Filters are optional; default sort createdAt,desc. "
                    + "Sortable: createdAt, riskScore, amount, status (sort by riskScore to order by severity).")
    @ApiResponse(responseCode = "200", description = "Page of alerts")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED (bad filter, page, size or sort)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<FraudAlertSummary> list(
            @RequestParam(required = false) FraudAlertStatus status,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "field,asc|desc", example = "createdAt,desc")
            @RequestParam(required = false) String sort) {
        return queries.list(new FraudAlertFilter(status, riskLevel, customerId),
                PageRequests.of(page, size, sort, SORTABLE, DEFAULT_SORT));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('BANK_STAFF', 'AUDITOR', 'ADMIN')")
    @Operation(summary = "Alert counters for the operations dashboard",
            description = "Roles: BANK_STAFF, AUDITOR, ADMIN. critical/high count alerts that are OPEN or UNDER_REVIEW; "
                    + "createdToday uses the Asia/Ho_Chi_Minh day.")
    public FraudAlertStats stats() {
        return queries.stats();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('BANK_STAFF', 'AUDITOR', 'ADMIN')")
    @Operation(summary = "Alert detail with triggered rules and status timeline",
            description = "Roles: BANK_STAFF, AUDITOR, ADMIN.")
    @ApiResponse(responseCode = "200", description = "Alert detail")
    @ApiResponse(responseCode = "404", description = "FRAUD_ALERT_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public FraudAlertDetail get(@PathVariable UUID id) {
        return queries.get(id);
    }

    @PatchMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('BANK_STAFF', 'ADMIN')")
    @Operation(summary = "Review an alert (status transition)",
            description = "Roles: BANK_STAFF, ADMIN (AUDITOR is read-only). Transitions: OPEN → UNDER_REVIEW | APPROVED | "
                    + "REJECTED | CLOSED; UNDER_REVIEW → APPROVED | REJECTED | CLOSED; APPROVED | REJECTED → CLOSED. "
                    + "note (1–1000) is required for APPROVED, REJECTED and CLOSED. Writes audit FRAUD_ALERT_REVIEW.")
    @ApiResponse(responseCode = "200", description = "Updated alert detail")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED (missing status or note)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "FRAUD_ALERT_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "FRAUD_ALERT_INVALID_TRANSITION (also on a concurrent review)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public FraudAlertDetail review(@PathVariable UUID id, @Valid @RequestBody ReviewFraudAlertRequest request,
                                   HttpServletRequest httpRequest) {
        return reviews.review(id, request, CurrentUser.require(), ClientIp.of(httpRequest));
    }
}
