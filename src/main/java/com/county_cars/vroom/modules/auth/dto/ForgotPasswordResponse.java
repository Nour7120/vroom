package com.county_cars.vroom.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Generic response returned by the forgot-password endpoint.
 *
 * <p>A deliberately vague message is used so that the response does <b>not</b>
 * reveal whether the email is registered in the system (prevents user enumeration).</p>
 */
@Data
@AllArgsConstructor
@Schema(description = "Response returned after a forgot-password request")
public class ForgotPasswordResponse {

    @Schema(
        description = "Generic status message — intentionally non-specific to prevent user enumeration",
        example = "If an account exists, a password reset email has been sent."
    )
    private String message;
}

