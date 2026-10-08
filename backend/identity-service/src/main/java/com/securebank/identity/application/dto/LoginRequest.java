package com.securebank.identity.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(example = "customer1") @NotBlank @Size(max = 100) String username,
        @Schema(example = "Customer@123") @NotBlank @Size(max = 200) String password
) {
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
