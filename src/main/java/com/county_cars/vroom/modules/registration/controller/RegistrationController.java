package com.county_cars.vroom.modules.registration.controller;

import com.county_cars.vroom.common.exception.ApiErrorResponse;
import com.county_cars.vroom.modules.registration.dto.CompleteRegistrationRequest;
import com.county_cars.vroom.modules.registration.dto.RegistrationRequest;
import com.county_cars.vroom.modules.registration.dto.RegistrationResponse;
import com.county_cars.vroom.modules.registration.dto.ResendVerificationRequest;
import com.county_cars.vroom.modules.registration.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Registration", description = "Public user registration and email verification endpoints")
public class RegistrationController {

    private final RegistrationService registrationService;


    @Operation(summary = "Register a new user")
    @ApiResponse(responseCode = "201", description = "User registered successfully", content = @Content(schema = @Schema(implementation = RegistrationResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Email or display name already taken", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(
            @Valid @RequestBody RegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationService.register(request));
    }

    @Operation(summary = "Register a new user used third party for signing in.")
    @ApiResponse(responseCode = "201", description = "User registered successfully", content = @Content(schema = @Schema(implementation = RegistrationResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Email or display name already taken", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/third-party/complete-register")
    public ResponseEntity<RegistrationResponse> completeRegistration(
            @Valid @RequestBody CompleteRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registrationService.completeThirdPartyRegistration(request));
    }

    @Operation(summary = "Resend email verification")
    @ApiResponse(responseCode = "204", description = "Verification email dispatched")
    @ApiResponse(responseCode = "400", description = "Cooldown active, daily cap reached, or account not pending", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No account found for this email", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        registrationService.resendVerificationEmail(request);
        return ResponseEntity.noContent().build();
    }
}
