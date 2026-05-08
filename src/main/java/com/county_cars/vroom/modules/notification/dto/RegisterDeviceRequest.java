package com.county_cars.vroom.modules.notification.dto;

import com.county_cars.vroom.modules.notification.entity.DevicePlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request to register or refresh an FCM device token")
public class RegisterDeviceRequest {

    @NotBlank(message = "token must not be blank")
    @Size(max = 1024, message = "token must not exceed 1024 characters")
    @Schema(description = "FCM registration token obtained from the Firebase SDK on the client",
            example = "fH3xY...long-token-string...")
    private String token;

    @NotNull(message = "platform must not be null")
    @Schema(description = "Client platform", example = "ANDROID", allowableValues = {"ANDROID", "IOS", "WEB"})
    private DevicePlatform platform;
}

