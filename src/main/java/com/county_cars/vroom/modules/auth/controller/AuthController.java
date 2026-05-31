package com.county_cars.vroom.modules.auth.controller;

import com.county_cars.vroom.common.exception.ApiErrorResponse;
import com.county_cars.vroom.modules.auth.dto.*;
import com.county_cars.vroom.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Current user profile and password lifecycle operations")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Get current user profile")
    @ApiResponse(responseCode = "200", description = "Current user profile", content = @Content(schema = @Schema(implementation = UserMeResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No DB profile found for the authenticated user", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/me")
    public ResponseEntity<UserMeResponse> getMe() {
        return ResponseEntity.ok(authService.getMe());
    }


    @Operation(summary = "Forgot password")
    @ApiResponse(responseCode = "200", description = "Generic success response (email may or may not exist)", content = @Content(schema = @Schema(implementation = ForgotPasswordResponse.class)))
    @ApiResponse(responseCode = "400", description = "Rate limit exceeded (cooldown or daily cap)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @Operation(summary = "Change password")
    @ApiResponse(responseCode = "200", description = "Password changed successfully", content = @Content(schema = @Schema(implementation = ChangePasswordResponse.class)))
    @ApiResponse(responseCode = "400", description = "Current password incorrect or new passwords do not match", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated or account not active", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping("/change-password")
    public ResponseEntity<ChangePasswordResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(authService.changePassword(request));
    }
}
