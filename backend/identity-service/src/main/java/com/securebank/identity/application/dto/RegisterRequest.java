package com.securebank.identity.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Self-registration of a CUSTOMER (api.md §1). */
public record RegisterRequest(
        @Schema(example = "customer9", description = "3–50 chars, lower-case letters, digits, '.', '_' or '-'")
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-z0-9._-]*$", message = "must contain only lower-case letters, digits, '.', '_' or '-'")
        String username,

        @Schema(example = "Customer@123", description = "8–100 chars with upper, lower, digit and symbol")
        @NotBlank
        @Size(min = 8, max = 100)
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).*$",
                message = "must contain an upper-case letter, a lower-case letter, a digit and a symbol")
        String password,

        @Schema(example = "Nguyễn Văn An")
        @NotBlank
        @Size(max = 200)
        String fullName,

        @Schema(example = "customer9@example.com")
        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @Schema(example = "+84901234567", nullable = true)
        @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "must be 9–15 digits, optionally starting with '+'")
        String phone
) {
    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", password=***, fullName=" + fullName
                + ", email=" + email + ", phone=" + phone + "]";
    }
}
