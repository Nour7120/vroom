package com.county_cars.vroom.modules.verification.controller;

import com.county_cars.vroom.modules.verification.dto.request.CreateVerificationRequest;
import com.county_cars.vroom.modules.verification.dto.request.ReviewVerificationRequest;
import com.county_cars.vroom.modules.verification.dto.response.VerificationResponse;
import com.county_cars.vroom.modules.verification.entity.VerificationStatus;
import com.county_cars.vroom.modules.verification.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/verifications")
@RequiredArgsConstructor
@Tag(name = "Verifications", description = "Manage identity and document verification requests")
public class VerificationController {

    private final VerificationService verificationService;

    @PostMapping("/user-profiles/{userProfileId}")
    @Operation(summary = "Submit a new verification request for a user")
    public ResponseEntity<VerificationResponse> create(
            @PathVariable Long userProfileId,
            @Valid @RequestBody CreateVerificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(verificationService.createVerificationRequest(userProfileId, request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get verification request by ID")
    public ResponseEntity<VerificationResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(verificationService.getById(id));
    }

    @GetMapping("/user-profiles/{userProfileId}")
    @Operation(summary = "Get all verifications for a specific user profile")
    public ResponseEntity<Page<VerificationResponse>> getByUser(
            @PathVariable Long userProfileId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(verificationService.getByUserProfile(userProfileId, pageable));
    }

    @GetMapping
    @Operation(summary = "Get verifications filtered by status (Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<VerificationResponse>> getByStatus(
            @RequestParam(required = false, defaultValue = "PENDING") VerificationStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(verificationService.getByStatus(status, pageable));
    }

    @PutMapping("/{id}/review")
    @Operation(summary = "Review (approve/reject) a verification request (Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VerificationResponse> review(
            @PathVariable Long id,
            @Valid @RequestBody ReviewVerificationRequest request) {
        return ResponseEntity.ok(verificationService.review(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a verification request (Admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        verificationService.deleteVerificationRequest(id);
    }
}

