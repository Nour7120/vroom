package com.county_cars.vroom.modules.chat.websocket;

import com.county_cars.vroom.modules.chat.dto.WsInboundMessage;
import com.county_cars.vroom.modules.chat.dto.WsOutboundMessage;
import com.county_cars.vroom.modules.chat.entity.WsMessageType;
import com.county_cars.vroom.modules.chat.service.ChatService;
import com.county_cars.vroom.modules.user_profile.entity.UserProfile;
import com.county_cars.vroom.modules.user_profile.repository.UserProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Optional;

/**
 * Central WebSocket message handler.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link #afterConnectionEstablished} — registers session, delivers offline messages</li>
 *   <li>{@link #handleTextMessage} — routes CHAT / PING / PONG frames</li>
 *   <li>{@link #afterConnectionClosed} — removes session, cleans up rate-limiter</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatService            chatService;
    private final ChatSessionManager     sessionManager;
    private final MessageRateLimiter     rateLimiter;
    private final ObjectMapper           objectMapper;
    private final UserProfileRepository  userProfileRepository;

    private final Counter errorCounter;

    public ChatWebSocketHandler(ChatService chatService,
                                ChatSessionManager sessionManager,
                                MessageRateLimiter rateLimiter,
                                ObjectMapper objectMapper,
                                UserProfileRepository userProfileRepository,
                                MeterRegistry meterRegistry) {
        this.chatService           = chatService;
        this.sessionManager        = sessionManager;
        this.rateLimiter           = rateLimiter;
        this.objectMapper          = objectMapper;
        this.userProfileRepository = userProfileRepository;

        this.errorCounter = Counter.builder("chat.websocket.errors")
                .description("Total WebSocket handler errors").register(meterRegistry);
    }

    // ── Connection established ────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String keycloakUserId = resolveKeycloakUserId(session);
        if (keycloakUserId == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

        sessionManager.register(keycloakUserId, session);

        // Deliver any messages that arrived while the user was offline
        Optional<UserProfile> profileOpt =
                userProfileRepository.findByKeycloakUserId(keycloakUserId);
        profileOpt.ifPresent(profile ->
                chatService.deliverOfflineMessages(profile.getId(), keycloakUserId));
    }

    // ── Inbound message ───────────────────────────────────────────────────────

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage raw) {
        String keycloakUserId = resolveKeycloakUserId(session);
        if (keycloakUserId == null) {
            sendError(session, "AUTH_ERROR", "Not authenticated");
            return;
        }

        WsInboundMessage inbound;
        try {
            inbound = objectMapper.readValue(raw.getPayload(), WsInboundMessage.class);
        } catch (Exception e) {
            sendError(session, "PARSE_ERROR", "Invalid message format");
            errorCounter.increment();
            return;
        }

        // PING — respond with PONG immediately, no DB access
        if (inbound.getType() == WsMessageType.PING) {
            sessionManager.sendToUser(keycloakUserId, WsOutboundMessage.pong());
            return;
        }

        // PONG — client acknowledged our server-side ping
        if (inbound.getType() == WsMessageType.PONG) {
            sessionManager.recordPong(keycloakUserId);
            return;
        }

        // CHAT — rate-limit, then process
        if (inbound.getType() == WsMessageType.CHAT) {
            if (!rateLimiter.tryConsume(keycloakUserId)) {
                sendError(session, "RATE_LIMITED", "Too many messages. Slow down.");
                errorCounter.increment();
                return;
            }

            UserProfile sender = userProfileRepository.findByKeycloakUserId(keycloakUserId)
                    .orElse(null);
            if (sender == null) {
                sendError(session, "USER_NOT_FOUND", "User profile not found");
                return;
            }

            try {
                WsOutboundMessage response = chatService.processMessage(sender.getId(), inbound);
                // Send ACK back to sender
                WsOutboundMessage ack = WsOutboundMessage.builder()
                        .type(WsMessageType.ACK)
                        .messageId(response.getMessageId())
                        .messageClientId(response.getMessageClientId())
                        .status(response.getStatus())
                        .build();
                sessionManager.sendToUser(keycloakUserId, ack);
            } catch (Exception e) {
                log.error("Error processing CHAT message from userId={}: {}", keycloakUserId, e.getMessage());
                sendError(session, "MESSAGE_ERROR", e.getMessage());
                errorCounter.increment();
            }
            return;
        }

        sendError(session, "UNKNOWN_TYPE", "Unsupported message type: " + inbound.getType());
    }

    // ── Connection closed ─────────────────────────────────────────────────────

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String keycloakUserId = resolveKeycloakUserId(session);
        if (keycloakUserId != null) {
            sessionManager.remove(keycloakUserId);
            rateLimiter.removeUser(keycloakUserId);
        }
        log.info("WebSocket closed: sessionId={} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error on sessionId={}: {}",
                session.getId(), exception.getMessage());
        errorCounter.increment();
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    // ── Rate-limiter refill — runs every second ────────────────────────────────

    @Scheduled(fixedRate = 2000)
    public void refillRateLimitBuckets() {
        rateLimiter.refillAll();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private String resolveKeycloakUserId(WebSocketSession session) {
        Object attr = session.getAttributes().get(ChatHandshakeInterceptor.ATTR_USER_ID);
        return attr instanceof String s ? s : null;
    }

    private void sendError(WebSocketSession session, String code, String message) {
        String keycloakUserId = resolveKeycloakUserId(session);
        if (keycloakUserId != null) {
            sessionManager.sendToUser(keycloakUserId, WsOutboundMessage.error(code, message));
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) session.close(status);
        } catch (Exception e) {
            log.warn("Error closing session {}: {}", session.getId(), e.getMessage());
        }
    }
}

