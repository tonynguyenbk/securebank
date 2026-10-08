package com.securebank.bankingcore.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Body of PUT /admin/accounts/{id}/limits: both limits > 0 with at most 2 decimals, per-transaction <= daily. */
public record UpdateTransferLimitsRequest(
        @Schema(example = "100000000.00") @NotNull @Positive @Digits(integer = 17, fraction = 2)
        BigDecimal perTransactionLimit,
        @Schema(example = "500000000.00") @NotNull @Positive @Digits(integer = 17, fraction = 2)
        BigDecimal dailyLimit) {

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "perTransactionLimit must not exceed dailyLimit")
    public boolean isPerTransactionWithinDaily() {
        return perTransactionLimit == null || dailyLimit == null || perTransactionLimit.compareTo(dailyLimit) <= 0;
    }
}
