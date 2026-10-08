package com.securebank.identity.controller;

import com.securebank.common.error.ApiError;
import com.securebank.common.security.CurrentUser;
import com.securebank.identity.application.auth.AuthenticationService;
import com.securebank.identity.application.auth.RegistrationService;
import com.securebank.identity.application.auth.UserProfileService;
import com.securebank.identity.application.dto.LoginRequest;
import com.securebank.identity.application.dto.RefreshTokenRequest;
import com.securebank.identity.application.dto.RegisterRequest;
import com.securebank.identity.application.dto.TokenResponse;
import com.securebank.identity.application.dto.UserResponse;
import com.securebank.identity.config.OpenApiConfig;
import com.securebank.identity.security.ClientInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Authentication", description = "Registration, login, token refresh/rotation, logout, current user")
public class AuthController {

    private final RegistrationService registration;
    private final AuthenticationService authentication;
    private final UserProfileService profiles;

    public AuthController(RegistrationService registration, AuthenticationService authentication,
                          UserProfileService profiles) {
        this.registration = registration;
        this.authentication = authentication;
        this.profiles = profiles;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new customer",
            description = "Public. Creates a user with role CUSTOMER only and emits UserRegisteredEvent "
                    + "(banking-core opens an empty VND account) plus audit USER_REGISTERED.")
    @ApiResponse(responseCode = "201", description = "User created")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED (with fieldErrors)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "AUTH_USERNAME_TAKEN",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public UserResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return registration.register(request, ClientInfo.from(http));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with username and password",
            description = "Public. Returns a 15-minute access JWT and a 7-day opaque refresh token. "
                    + "5 failed attempts per 5 minutes per username or client IP lock further attempts (429).")
    @ApiResponse(responseCode = "200", description = "Authenticated")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "AUTH_INVALID_CREDENTIALS",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "429", description = "AUTH_LOGIN_RATE_LIMITED",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authentication.login(request, ClientInfo.from(http));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new token pair (rotation)",
            description = "Public. The presented refresh token is consumed; reusing a consumed/revoked token "
                    + "revokes every refresh token of that user.")
    @ApiResponse(responseCode = "200", description = "New token pair")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "AUTH_REFRESH_TOKEN_INVALID",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest http) {
        return authentication.refresh(request.refreshToken(), ClientInfo.from(http));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(summary = "Log out",
            description = "Any authenticated role. Revokes the given refresh token and denylists the current "
                    + "access token in Redis until it expires. Audit LOGOUT.")
    @ApiResponse(responseCode = "204", description = "Logged out")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public void logout(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest http) {
        authentication.logout(CurrentUser.require(), request.refreshToken(), ClientInfo.from(http));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(summary = "Current user profile", description = "Any authenticated role.")
    @ApiResponse(responseCode = "200", description = "The authenticated user")
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public UserResponse me() {
        return profiles.get(CurrentUser.require().userId());
    }
}
