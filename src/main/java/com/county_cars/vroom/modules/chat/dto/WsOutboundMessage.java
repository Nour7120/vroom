package com.county_cars.vroom.modules.chat.dto;

import com.county_cars.vroom.modules.chat.entity.MessageStatus;
import com.county_cars.vroom.modules.chat.entity.MessageType;
import com.county_cars.vroom.modules.chat.entity.WsMessageType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Outbound WebSocket frame sent by the server to a client.
 */
@Data
@Builder
public class WsOutboundMessage {

    private WsMessageType type;

    // ── Present on CHAT / DELIVERY frames ────────────────────────────────────

    private Long messageId;
    private Long chatId;
    private Long senderId;
    private String senderDisplayName;
    private String content;
    private MessageType messageType;
    private MessageStatus status;
    private String messageClientId;
    private Instant createdAt;

    // ── Present on STATUS_UPDATE frames ──────────────────────────────────────

    private MessageStatus newStatus;

    // ── Present on ERROR frames ───────────────────────────────────────────────

    private String errorCode;
    private String errorMessage;

    // ── Factory helpers ───────────────────────────────────────────────────────

    public static WsOutboundMessage pong() {
        return WsOutboundMessage.builder().type(WsMessageType.PONG).build();
    }

    public static WsOutboundMessage error(String code, String message) {
        return WsOutboundMessage.builder()
                .type(WsMessageType.ERROR)
                .errorCode(code)
                .errorMessage(message)
                .build();
    }
}

