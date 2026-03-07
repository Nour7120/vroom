package com.county_cars.vroom.modules.registration.controller;

import com.county_cars.vroom.common.exception.ApiErrorResponse;
import com.county_cars.vroom.modules.registration.dto.RegistrationRequest;
import com.county_cars.vroom.modules.registration.dto.RegistrationResponse;
import com.county_cars.vroom.modules.registration.dto.ResendVerificationRequest;
import com.county_cars.vroom.modules.registration.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public registration endpoints — no authentication required.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Registration", description = "Public user registration and email verification endpoints")
public class RegistrationController {

    private final RegistrationService registrationService;

    // ─── POST /api/v1/auth/register ──────────────────────────────────────────────

    @Operation(
        summary = "Register a new user",
        description = """
            Full registration flow:
            1. Creates the user in Keycloak (triggers built-in VERIFY_EMAIL action).
            2. Persists the UserProfile in the database with status PENDING_MAIL_VERIFICATION.
            3. On any DB failure the Keycloak user is automatically deleted (rollback).

            The client does **not** need to send a verification email — Keycloak dispatches it automatically.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User registered successfully",
            content = @Content(schema = @Schema(implementation = RegistrationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Email or display name already taken",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(
            @Valid @RequestBody RegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationService.register(request));
    }

    // ─── POST /api/v1/auth/resend-verification ────────────────────────────────────

    @Operation(
        summary = "Resend email verification",
        description = """
            Re-triggers Keycloak's built-in VERIFY_EMAIL action for an existing
            PENDING_MAIL_VERIFICATION account.

            Rate limits:
            - Minimum **2 minutes** between consecutive requests (configurable via
              `registration.verification.resend-interval-minutes`).
            - Maximum **5 attempts per calendar day** (configurable via
              `registration.verification.daily-max-retries`).
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Verification email dispatched"),
        @ApiResponse(responseCode = "400", description = "Cooldown active, daily cap reached, or account not pending",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "No account found for this email",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        registrationService.resendVerificationEmail(request);
        return ResponseEntity.noContent().build();
    }
}
