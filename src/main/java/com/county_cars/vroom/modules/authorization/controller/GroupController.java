package com.county_cars.vroom.modules.authorization.controller;

import com.county_cars.vroom.modules.authorization.dto.request.GroupRequest;
import com.county_cars.vroom.modules.authorization.dto.response.GroupResponse;
import com.county_cars.vroom.modules.authorization.service.AuthorizationService;
import io.swagger.v3.oas.annotations.Hidden;
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
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
@Hidden
@Tag(name = "Groups", description = "Manage groups and their permissions")
@PreAuthorize("hasRole('ADMIN')")
public class GroupController {

    private final AuthorizationService authorizationService;

    @PostMapping
    @Operation(summary = "Create a group")
    public ResponseEntity<GroupResponse> create(@Valid @RequestBody GroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authorizationService.createGroup(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get group by ID")
    public ResponseEntity<GroupResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(authorizationService.getGroupById(id));
    }

    @GetMapping
    @Operation(summary = "List all groups")
    public ResponseEntity<Page<GroupResponse>> getAll(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(authorizationService.getAllGroups(pageable));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a group")
    public ResponseEntity<GroupResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody GroupRequest request) {
        return ResponseEntity.ok(authorizationService.updateGroup(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a group")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        authorizationService.deleteGroup(id);
    }
}

