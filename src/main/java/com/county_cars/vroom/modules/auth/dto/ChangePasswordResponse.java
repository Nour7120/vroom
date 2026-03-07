package com.county_cars.vroom.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Response returned after a successful password change.
 */
@Data
@AllArgsConstructor
@Schema(description = "Response returned after a successful password change")
public class ChangePasswordResponse {

    @Schema(description = "Human-readable success message", example = "Password changed successfully.")
    private String message;
}

