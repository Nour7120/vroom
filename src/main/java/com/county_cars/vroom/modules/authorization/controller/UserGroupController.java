package com.county_cars.vroom.modules.authorization.controller;

import com.county_cars.vroom.modules.authorization.dto.request.UserGroupRequest;
import com.county_cars.vroom.modules.authorization.dto.response.UserGroupResponse;
import com.county_cars.vroom.modules.authorization.service.AuthorizationService;
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
@RequestMapping("/api/v1/user-groups")
@RequiredArgsConstructor
@Tag(name = "User Groups", description = "Assign users to groups")
@PreAuthorize("hasRole('ADMIN')")
public class UserGroupController {

    private final AuthorizationService authorizationService;

    @PostMapping
    @Operation(summary = "Assign a user to a group")
    public ResponseEntity<UserGroupResponse> assign(@Valid @RequestBody UserGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authorizationService.assignUserToGroup(request));
    }

    @GetMapping("/users/{userProfileId}")
    @Operation(summary = "Get all groups for a user")
    public ResponseEntity<Page<UserGroupResponse>> getUserGroups(
            @PathVariable Long userProfileId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(authorizationService.getUserGroups(userProfileId, pageable));
    }

    @GetMapping("/groups/{groupId}/members")
    @Operation(summary = "Get all members of a group")
    public ResponseEntity<Page<UserGroupResponse>> getGroupMembers(
            @PathVariable Long groupId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(authorizationService.getGroupMembers(groupId, pageable));
    }

    @DeleteMapping("/users/{userProfileId}/groups/{groupId}")
    @Operation(summary = "Remove a user from a group")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long userProfileId, @PathVariable Long groupId) {
        authorizationService.removeUserFromGroup(userProfileId, groupId);
    }
}

