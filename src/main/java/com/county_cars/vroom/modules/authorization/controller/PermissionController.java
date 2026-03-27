package com.county_cars.vroom.modules.authorization.controller;

import com.county_cars.vroom.modules.authorization.dto.request.PermissionRequest;
import com.county_cars.vroom.modules.authorization.dto.response.PermissionResponse;
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
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
@Hidden
@Tag(name = "Permissions", description = "Manage application permissions")
@PreAuthorize("hasRole('ADMIN')")
public class PermissionController {

    private final AuthorizationService authorizationService;

    @PostMapping
    @Operation(summary = "Create a permission")
    public ResponseEntity<PermissionResponse> create(@Valid @RequestBody PermissionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authorizationService.createPermission(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get permission by ID")
    public ResponseEntity<PermissionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(authorizationService.getPermissionById(id));
    }

    @GetMapping
    @Operation(summary = "List all permissions")
    public ResponseEntity<Page<PermissionResponse>> getAll(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(authorizationService.getAllPermissions(pageable));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a permission")
    public ResponseEntity<PermissionResponse> update(@PathVariable Long id,
                                                      @Valid @RequestBody PermissionRequest request) {
        return ResponseEntity.ok(authorizationService.updatePermission(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a permission")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        authorizationService.deletePermission(id);
    }
}

