package com.county_cars.vroom.modules.chat.dto;

import com.county_cars.vroom.modules.chat.entity.MessageStatus;
import com.county_cars.vroom.modules.chat.entity.MessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@Schema(description = "A single chat message")
public class MessageResponse {

    @Schema(description = "Message ID")
    private Long id;

    @Schema(description = "Chat ID")
    private Long chatId;

    @Schema(description = "Sender user profile ID")
    private Long senderId;

    @Schema(description = "Sender display name")
    private String senderDisplayName;

    @Schema(description = "Message content")
    private String content;

    @Schema(description = "Message type")
    private MessageType messageType;

    @Schema(description = "Delivery status")
    private MessageStatus status;

    @Schema(description = "Client-generated idempotency key")
    private String messageClientId;

    @Schema(description = "When the message was created")
    private Instant createdAt;
}

