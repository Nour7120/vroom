package com.county_cars.vroom.modules.auth.controller;

import com.county_cars.vroom.common.exception.ApiErrorResponse;
import com.county_cars.vroom.modules.auth.dto.TwoFactorStatusResponse;
import com.county_cars.vroom.modules.auth.service.Auth2FAService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Two-Factor Authentication (TOTP) management endpoints.
 *
 * <p>All endpoints require a valid Bearer JWT token.
 * The caller's identity is resolved exclusively from the JWT — no userId parameter accepted.</p>
 *
 * <ul>
 *   <li>{@code POST   /api/v1/auth/2fa/enable}  — add CONFIGURE_TOTP required action</li>
 *   <li>{@code DELETE /api/v1/auth/2fa/disable} — remove OTP credentials and CONFIGURE_TOTP</li>
 *   <li>{@code GET    /api/v1/auth/2fa/status}  — check whether OTP credential is active</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth/2fa")
@RequiredArgsConstructor
@Hidden
@Tag(name = "Two-Factor Authentication", description = "TOTP-based 2FA management for the authenticated user")
public class Auth2FAController {

    private final Auth2FAService auth2FAService;

    // ─── POST /api/v1/auth/2fa/enable ────────────────────────────────────────────

    @Operation(
        summary = "Enable 2FA",
        description = """
            Adds `CONFIGURE_TOTP` to the authenticated user's Keycloak required actions.

            **Requires a valid Bearer JWT token.**

            - Idempotent: calling when already enabled does not duplicate the action.
            - Keycloak will prompt the user to scan a QR code on their next login.
            - Does NOT remove any existing credentials.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200",
            description = "2FA enabled successfully",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = "{\"message\": \"2FA enabled successfully\"}")
            )),
        @ApiResponse(responseCode = "401",
            description = "Missing or invalid JWT",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500",
            description = "Keycloak Admin API error",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/enable")
    public ResponseEntity<Map<String, String>> enable() {
        auth2FAService.enable();
        return ResponseEntity.ok(Map.of("message", "2FA enabled successfully"));
    }

    // ─── DELETE /api/v1/auth/2fa/disable ─────────────────────────────────────────

    @Operation(
        summary = "Disable 2FA",
        description = """
            Removes all OTP credentials from the authenticated user's Keycloak account
            and strips `CONFIGURE_TOTP` from required actions.

            **Requires a valid Bearer JWT token.**

            - Removes every credential of type `otp`.
            - Removes `CONFIGURE_TOTP` from required actions if present.
            - Safe to call even when 2FA is not enabled (no-op).
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200",
            description = "2FA disabled successfully",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = "{\"message\": \"2FA disabled successfully\"}")
            )),
        @ApiResponse(responseCode = "401",
            description = "Missing or invalid JWT",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500",
            description = "Keycloak Admin API error",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping("/disable")
    public ResponseEntity<Map<String, String>> disable() {
        auth2FAService.disable();
        return ResponseEntity.ok(Map.of("message", "2FA disabled successfully"));
    }

    // ─── GET /api/v1/auth/2fa/status ─────────────────────────────────────────────

    @Operation(
        summary = "Get 2FA status",
        description = """
            Returns whether TOTP-based 2FA is currently active for the authenticated user.

            **Requires a valid Bearer JWT token.**

            `enabled` is `true` only when an `otp` credential already exists in Keycloak
            (the user has completed TOTP setup), not just when `CONFIGURE_TOTP` is pending.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200",
            description = "Current 2FA status",
            content = @Content(schema = @Schema(implementation = TwoFactorStatusResponse.class))),
        @ApiResponse(responseCode = "401",
            description = "Missing or invalid JWT",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/status")
    public ResponseEntity<TwoFactorStatusResponse> status() {
        return ResponseEntity.ok(auth2FAService.getStatus());
    }
}

