package com.securebank.bankingcore.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info = @Info(title = "SecureBank Banking Core API", version = "v1",
                description = """
                        Customers, accounts, transfers, double-entry ledger, limits and staff operations.
                        Transfers are ACID (one PostgreSQL transaction with pessimistic row locks), idempotent \
                        (Idempotency-Key header) and publish events through a transactional outbox.
                        Errors always use the ApiError body; `code` is a stable ErrorCode name."""),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
        description = "Access token from POST /api/v1/auth/login (identity-service)")
public class OpenApiConfig {
}
