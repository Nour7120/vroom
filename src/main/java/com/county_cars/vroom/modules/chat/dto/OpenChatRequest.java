package com.county_cars.vroom.modules.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request to open or retrieve a chat between the current user and another user")
public class OpenChatRequest {

    @NotNull
    @Schema(description = "The other participant's user profile ID", example = "5")
    private Long otherUserId;

    @Schema(description = "Optional listing ID this chat is about", example = "12")
    private Long listingId;
}

