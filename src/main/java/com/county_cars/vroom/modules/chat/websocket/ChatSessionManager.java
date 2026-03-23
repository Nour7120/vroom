package com.county_cars.vroom.modules.chat.websocket;

import com.county_cars.vroom.modules.chat.config.ChatProperties;
import com.county_cars.vroom.modules.chat.dto.WsOutboundMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe registry that maps a Keycloak userId (String) to its active
 * {@link WebSocketSession}.
 *
 * <p>One user = one active session at a time.  A new connection from the same
 * user silently closes the previous one.</p>
 *
 * <p>Also runs the heartbeat scheduler that pings each session every
 * {@code chat.websocket.heartbeat-interval-seconds} seconds and evicts
 * sessions that miss more than {@code chat.websocket.max-missed-heartbeats}.</p>
 */
@Slf4j
@Component
public class ChatSessionManager {

    private static final String ATTR_LAST_PONG = "lastPongAt";
    private static final String ATTR_MISSED    = "missedHeartbeats";

    /** userId (Keycloak sub) → active session */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** Per-session send lock — avoids synchronized(session) on local variable */
    private final Map<String, Object> sessionLocks = new ConcurrentHashMap<>();

    private final ObjectMapper        objectMapper;
    private final ChatProperties      props;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ws-heartbeat");
                t.setDaemon(true);
                return t;
            });

    public ChatSessionManager(ObjectMapper objectMapper,
                              ChatProperties props,
                              MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.props        = props;

        // Expose active connection count to Micrometer / Prometheus
        Gauge.builder("chat.websocket.active_connections", sessions, Map::size)
                .description("Number of active WebSocket connections")
                .register(meterRegistry);

        startHeartbeatScheduler();
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    public void register(String userId, WebSocketSession session) {
        WebSocketSession previous = sessions.put(userId, session);
        sessionLocks.put(userId, new Object());
        if (previous != null && previous.isOpen()) {
            log.info("Replacing previous session for userId={}", userId);
            closeQuietly(previous);
        }
        session.getAttributes().put(ATTR_LAST_PONG, Instant.now());
        session.getAttributes().put(ATTR_MISSED, 0);
        log.info("Session registered: userId={} sessionId={}", userId, session.getId());
    }

    public void remove(String userId) {
        sessions.remove(userId);
        sessionLocks.remove(userId);
        log.info("Session removed: userId={}", userId);
    }

    public boolean isOnline(String userId) {
        WebSocketSession s = sessions.get(userId);
        return s != null && s.isOpen();
    }

    public Optional<WebSocketSession> getSession(String userId) {
        return Optional.ofNullable(sessions.get(userId));
    }

    public int activeConnectionCount() {
        return sessions.size();
    }

    // ── Outbound delivery ─────────────────────────────────────────────────────

    /**
     * Sends a message to a user if they are online.
     *
     * @return {@code true} if the message was sent successfully
     */
    public boolean sendToUser(String userId, WsOutboundMessage message) {
        return getSession(userId).map(session -> {
            if (!session.isOpen()) return false;
            try {
                String json = objectMapper.writeValueAsString(message);
                Object lock = sessionLocks.computeIfAbsent(userId, k -> new Object());
                synchronized (lock) {
                    session.sendMessage(new TextMessage(json));
                }
                return true;
            } catch (IOException e) {
                log.error("Failed to send message to userId={}: {}", userId, e.getMessage());
                return false;
            }
        }).orElse(false);
    }

    // ── Pong acknowledgement ──────────────────────────────────────────────────

    public void recordPong(String userId) {
        getSession(userId).ifPresent(s -> {
            s.getAttributes().put(ATTR_LAST_PONG, Instant.now());
            s.getAttributes().put(ATTR_MISSED, 0);
        });
    }

    // ── Heartbeat ─────────────────────────────────────────────────────────────

    private void startHeartbeatScheduler() {
        long intervalSeconds = props.getHeartbeatIntervalSeconds();
        scheduler.scheduleAtFixedRate(
                this::pingAllSessions,
                intervalSeconds,
                intervalSeconds,
                TimeUnit.SECONDS);
        log.info("WebSocket heartbeat scheduler started: interval={}s maxMissed={}",
                intervalSeconds, props.getMaxMissedHeartbeats());
    }

    private void pingAllSessions() {
        WsOutboundMessage ping = WsOutboundMessage.builder()
                .type(com.county_cars.vroom.modules.chat.entity.WsMessageType.PING)
                .build();

        sessions.forEach((userId, session) -> {
            if (!session.isOpen()) {
                evict(userId);
                return;
            }

            int missed = (int) session.getAttributes().getOrDefault(ATTR_MISSED, 0);
            if (missed >= props.getMaxMissedHeartbeats()) {
                log.warn("Evicting unresponsive session: userId={} missedHeartbeats={}", userId, missed);
                closeQuietly(session);
                evict(userId);
                return;
            }

            session.getAttributes().put(ATTR_MISSED, missed + 1);
            sendToUser(userId, ping);
        });
    }

    private void evict(String userId) {
        sessions.remove(userId);
        sessionLocks.remove(userId);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) session.close();
        } catch (IOException e) {
            log.warn("Error closing session {}: {}", session.getId(), e.getMessage());
        }
    }
}





