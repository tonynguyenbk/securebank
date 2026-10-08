package com.securebank.fraud.application.dto;

import com.securebank.fraud.domain.FraudAlertStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param status target status
 * @param note   required (1–1000 chars) for APPROVED, REJECTED and CLOSED; optional for UNDER_REVIEW
 */
public record ReviewFraudAlertRequest(
        @NotNull @Schema(example = "REJECTED") FraudAlertStatus status,
        @Size(max = 1000) @Schema(example = "Customer confirmed the transfer was not made by them") String note
) {
}
