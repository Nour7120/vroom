package com.county_cars.vroom.modules.user_profile.controller;

import com.county_cars.vroom.modules.user_profile.dto.request.CreateUserProfileRequest;
import com.county_cars.vroom.modules.user_profile.dto.request.UpdateUserProfileRequest;
import com.county_cars.vroom.modules.user_profile.dto.request.UserLocationRequest;
import com.county_cars.vroom.modules.user_profile.dto.response.UserLocationResponse;
import com.county_cars.vroom.modules.user_profile.dto.response.UserProfileResponse;
import com.county_cars.vroom.modules.user_profile.service.UserProfileService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user-profiles")
@RequiredArgsConstructor
@Hidden
@Tag(name = "User Profiles", description = "CRUD operations for user profiles and locations")
public class UserProfileController {

    private final UserProfileService userProfileService;

    @PostMapping
    @Operation(summary = "Create a new user profile")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserProfileResponse> create(@Valid @RequestBody CreateUserProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userProfileService.createUserProfile(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Get my own profile")
    public ResponseEntity<UserProfileResponse> getMyProfile() {
        return ResponseEntity.ok(userProfileService.getMyProfile());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user profile by ID")
    @PreAuthorize("hasRole('ADMIN') or @currentUserService.getCurrentUserProfileId() == #id")
    public ResponseEntity<UserProfileResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userProfileService.getUserProfileById(id));
    }

    @GetMapping
    @Operation(summary = "List all user profiles (admin only)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserProfileResponse>> getAll(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userProfileService.getAllUserProfiles(pageable));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user profile")
    @PreAuthorize("hasRole('ADMIN') or @currentUserService.getCurrentUserProfileId() == #id")
    public ResponseEntity<UserProfileResponse> update(@PathVariable Long id,
                                                      @Valid @RequestBody UpdateUserProfileRequest request) {
        return ResponseEntity.ok(userProfileService.updateUserProfile(id, request));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload / replace profile avatar",
        description = """
            Uploads an image and sets it as the authenticated user's profile photo.

            - Allowed types: jpg, jpeg, png, gif, webp (max 10 MB)
            - If the user already has a profile photo the old file is deleted on success.
            - The updated profile is returned with the new `avatarUrl`.

            **Requires a valid Bearer JWT.**
            """
    )
    public ResponseEntity<UserProfileResponse> uploadAvatar(
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(userProfileService.uploadAvatar(file));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a user profile")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        userProfileService.deleteUserProfile(id);
    }

    // ─── Locations ───────────────────────────────────────────────────────────

    @PostMapping("/{id}/locations")
    @Operation(summary = "Add a location to a user profile")
    public ResponseEntity<UserLocationResponse> addLocation(@PathVariable Long id,
                                                             @Valid @RequestBody UserLocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userProfileService.addLocation(id, request));
    }

    @GetMapping("/{id}/locations")
    @Operation(summary = "Get all locations for a user profile")
    public ResponseEntity<List<UserLocationResponse>> getLocations(@PathVariable Long id) {
        return ResponseEntity.ok(userProfileService.getLocations(id));
    }

    @PutMapping("/{id}/locations/{locationId}")
    @Operation(summary = "Update a user location")
    public ResponseEntity<UserLocationResponse> updateLocation(@PathVariable Long id,
                                                               @PathVariable Long locationId,
                                                               @Valid @RequestBody UserLocationRequest request) {
        return ResponseEntity.ok(userProfileService.updateLocation(id, locationId, request));
    }

    @DeleteMapping("/{id}/locations/{locationId}")
    @Operation(summary = "Delete a user location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLocation(@PathVariable Long id, @PathVariable Long locationId) {
        userProfileService.deleteLocation(id, locationId);
    }
}

