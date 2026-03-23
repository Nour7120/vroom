package com.county_cars.vroom.modules.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@Schema(description = "Chat summary for the conversation list")
public class ChatResponse {

    @Schema(description = "Chat ID")
    private Long id;

    @Schema(description = "Other participant's profile ID")
    private Long otherUserId;

    @Schema(description = "Other participant's display name")
    private String otherUserDisplayName;

    @Schema(description = "Listing ID this chat was started from, if any")
    private Long listingId;

    @Schema(description = "Timestamp of the most recent message")
    private Instant lastMessageAt;

    @Schema(description = "Number of unread messages for the current user")
    private long unreadCount;
}

