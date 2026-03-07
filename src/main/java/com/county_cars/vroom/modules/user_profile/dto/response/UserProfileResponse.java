package com.county_cars.vroom.modules.user_profile.dto.response;

import com.county_cars.vroom.modules.user_profile.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "User profile response")
public class UserProfileResponse {
    private Long id;
    private String keycloakUserId;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String avatarUrl;
    private UserStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

