package com.county_cars.vroom.modules.user_profile.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request to update an existing user profile")
public class UpdateUserProfileRequest {

    @Size(max = 128)
    @Schema(description = "First name", example = "John")
    private String firstName;

    @Size(max = 128)
    @Schema(description = "Last name", example = "Doe")
    private String lastName;

    @Size(max = 32)
    @Schema(description = "Phone number", example = "+1234567890")
    private String phoneNumber;

    @Size(max = 1024)
    @Schema(description = "Avatar URL")
    private String avatarUrl;
}

