package com.county_cars.vroom.modules.chat.dto;

import com.county_cars.vroom.modules.chat.entity.MessageType;
import com.county_cars.vroom.modules.chat.entity.WsMessageType;
import lombok.Data;

/**
 * Inbound WebSocket frame sent by the client.
 *
 * <pre>
 * {
 *   "type"            : "CHAT",
 *   "chatId"          : 42,
 *   "messageClientId" : "uuid-v4",
 *   "messageType"     : "TEXT",
 *   "content"         : "Hello!"
 * }
 * </pre>
 */
@Data
public class WsInboundMessage {

    private WsMessageType type;

    /** Target chat.  Required for CHAT frames; null for PING/PONG. */
    private Long chatId;

    /** Client-generated idempotency key.  Required for CHAT frames. */
    private String messageClientId;

    /** Default TEXT; clients may send IMAGE / FILE (Phase 2). */
    private MessageType messageType = MessageType.TEXT;

    /** Message body.  Must not exceed configured max size. */
    private String content;
}

