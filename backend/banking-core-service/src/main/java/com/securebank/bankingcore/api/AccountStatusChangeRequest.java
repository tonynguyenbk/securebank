package com.securebank.bankingcore.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of PATCH /admin/accounts/{id}/freeze and /unfreeze. The reason is recorded in the audit trail. */
public record AccountStatusChangeRequest(
        @Schema(description = "Why the status changes (3-255 chars)", example = "Suspected fraud TX202610080007")
        @NotBlank @Size(min = 3, max = 255) String reason) {
}
