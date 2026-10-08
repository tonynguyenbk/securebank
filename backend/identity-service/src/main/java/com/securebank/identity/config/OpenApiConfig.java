package com.securebank.identity.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI identityOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SecureBank Identity Service")
                        .version("v1")
                        .description("Registration, login, refresh-token rotation, logout and the current user profile. "
                                + "Errors use the shared ApiError body (docs/contracts/api.md §0)."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token from POST /api/v1/auth/login (15 min)")));
    }
}
