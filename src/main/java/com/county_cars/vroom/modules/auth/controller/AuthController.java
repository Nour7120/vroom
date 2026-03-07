package com.county_cars.vroom.modules.auth.controller;

import com.county_cars.vroom.common.exception.ApiErrorResponse;
import com.county_cars.vroom.modules.auth.dto.ChangePasswordRequest;
import com.county_cars.vroom.modules.auth.dto.ChangePasswordResponse;
import com.county_cars.vroom.modules.auth.dto.ForgotPasswordRequest;
import com.county_cars.vroom.modules.auth.dto.ForgotPasswordResponse;
import com.county_cars.vroom.modules.auth.dto.UserMeResponse;
import com.county_cars.vroom.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints: current user profile, forgot password, change password.
 *
 * <ul>
 *   <li>{@code GET  /api/v1/auth/me}                — protected, returns full current-user profile</li>
 *   <li>{@code POST /api/v1/auth/forgot-password}    — public, no token required</li>
 *   <li>{@code POST /api/v1/account/change-password} — protected, requires valid JWT</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Current user profile and password lifecycle operations")
public class AuthController {

    private final AuthService authService;

    // ─── GET /api/v1/auth/me ─────────────────────────────────────────────────────

    @Operation(
        summary = "Get current user profile",
        description = """
            Returns the full internal profile of the currently authenticated user,
            enriched with minimal Keycloak data:
            - **emailVerified** — whether the email was confirmed in Keycloak
            - **authProviders** — list of linked social identity providers (e.g. google, apple)
            - **hasLocalPassword** — whether a local password credential exists

            **Requires a valid Bearer JWT token.**

            Performance budget: 1 DB query + up to 3 Keycloak admin calls.
            Keycloak failures are non-fatal — safe defaults are returned so the endpoint
            never breaks.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Current user profile",
            content = @Content(schema = @Schema(implementation = UserMeResponse.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "No DB profile found for the authenticated user",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/api/v1/auth/me")
    public ResponseEntity<UserMeResponse> getMe() {
        return ResponseEntity.ok(authService.getMe());
    }

    // ─── POST /api/v1/auth/forgot-password ───────────────────────────────────────

    @Operation(
        summary = "Forgot password",
        description = """
            Triggers Keycloak to send a password-reset email to the provided address.

            **Security:** The response is always `200 OK` with a generic message regardless of
            whether the email exists in the system — prevents user enumeration.

            Rate limits (configurable via `auth.password-reset.*` properties):
            - Minimum **2 minutes** between consecutive requests.
            - Maximum **5 attempts per calendar day**.

            `400 Bad Request` is only returned when a rate limit is exceeded; it does NOT
            reveal whether the account exists.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200",
            description = "Generic success response (email may or may not exist)",
            content = @Content(schema = @Schema(implementation = ForgotPasswordResponse.class))),
        @ApiResponse(responseCode = "400",
            description = "Rate limit exceeded (cooldown or daily cap)",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/api/v1/auth/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    // ─── POST /api/v1/account/change-password ────────────────────────────────────

    @Operation(
        summary = "Change password",
        description = """
            Changes the password for the currently authenticated user.

            **Requires a valid Bearer JWT token.**

            Flow:
            1. Resolves the caller's identity from the JWT.
            2. Validates the account is `ACTIVE`.
            3. Verifies the current password against Keycloak (ROPC grant).
            4. Confirms `newPassword == confirmNewPassword`.
            5. Sets the new password via the Keycloak Admin API.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200",
            description = "Password changed successfully",
            content = @Content(schema = @Schema(implementation = ChangePasswordResponse.class))),
        @ApiResponse(responseCode = "400",
            description = "Current password incorrect or new passwords do not match",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "401",
            description = "Not authenticated or account not active",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/api/v1/account/change-password")
    public ResponseEntity<ChangePasswordResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(authService.changePassword(request));
    }
}
