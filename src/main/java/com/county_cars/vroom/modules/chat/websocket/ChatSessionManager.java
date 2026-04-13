package com.county_cars.vroom.modules.chat.websocket;

import com.county_cars.vroom.modules.chat.config.ChatProperties;
import com.county_cars.vroom.modules.chat.dto.WsOutboundMessage;
import com.county_cars.vroom.modules.chat.entity.WsMessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.Put;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe registry mapping a Keycloak userId to <em>all</em> of its active
 * {@link WebSocketSession}s (multi-device support).
 *
 * <p>Each device connects independently. Incoming messages are fanned out to every
 * open session belonging to the target user. The heartbeat scheduler tracks missed
 * pings per individual session, not per user.</p>
 */
@Slf4j
@Component
public class ChatSessionManager {

    static final String ATTR_LAST_PONG = "lastPongAt";
    static final String ATTR_MISSED    = "missedHeartbeats";

    /** userId (Keycloak sub) → list of active sessions (one per device) */
    private final Map<String, CopyOnWriteArrayList<WebSocketSession>> sessions =
            new ConcurrentHashMap<>();

    /**
     * Per-session send lock — keyed by {@link WebSocketSession#getId()} to prevent
     * concurrent writes on the same socket from multiple threads.
     */
    private final Map<String, Object> sessionLocks = new ConcurrentHashMap<>();

    private final ObjectMapper   objectMapper;
    private final ChatProperties props;
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

        // Expose total active connection count (all sessions, all devices)
        Gauge.builder("chat.websocket.active_connections", this, ChatSessionManager::activeConnectionCount)
                .description("Number of active WebSocket connections (all devices)")
                .register(meterRegistry);

        startHeartbeatScheduler();
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    /**
     * Registers a new session for {@code userId}.  Any existing sessions for
     * the same user (other devices) are kept open — multi-device is fully supported.
     */
    public void register(String userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(session);
        sessionLocks.put(session.getId(), new Object());
        session.getAttributes().put(ATTR_LAST_PONG, Instant.now());
        session.getAttributes().put(ATTR_MISSED, 0);
        log.info("Session registered: userId={} sessionId={} (total sessions for user: {})",
                userId, session.getId(),
                sessions.getOrDefault(userId, new CopyOnWriteArrayList<>()).size());
    }

    /**
     * Removes a specific session (device disconnect).  If this was the last
     * session for the user the user-level entry is cleaned up as well.
     */
    public void remove(String userId, WebSocketSession session) {
        CopyOnWriteArrayList<WebSocketSession> list = sessions.get(userId);
        if (list != null) {
            list.remove(session);
            if (list.isEmpty()) {
                sessions.remove(userId);
            }
        }
        sessionLocks.remove(session.getId());
        log.info("Session removed: userId={} sessionId={}", userId, session.getId());
    }

    /** @return {@code true} if the user has at least one open session on any device */
    public boolean isOnline(String userId) {
        CopyOnWriteArrayList<WebSocketSession> list = sessions.get(userId);
        if (list == null) return false;
        return list.stream().anyMatch(WebSocketSession::isOpen);
    }

    /** Total number of open WebSocket sessions across all users and devices. */
    public int activeConnectionCount() {
        return sessions.values().stream()
                .mapToInt(list -> (int) list.stream().filter(WebSocketSession::isOpen).count())
                .sum();
    }

    // ── Outbound delivery ─────────────────────────────────────────────────────

    /**
     * Sends {@code message} to <em>all</em> open sessions belonging to {@code userId}.
     *
     * @return {@code true} if the message was sent successfully to at least one session
     */
    public boolean sendToUser(String userId, WsOutboundMessage message) {
        CopyOnWriteArrayList<WebSocketSession> list = sessions.get(userId);
        if (list == null || list.isEmpty()) return false;

        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Failed to serialise message for userId={}: {}", userId, e.getMessage());
            return false;
        }

        boolean sentToAny = false;
        for (WebSocketSession session : list) {
            if (!session.isOpen()) continue;
            sentToAny |= sendToSession(session, json);
        }
        return sentToAny;
    }

    // ── Pong acknowledgement ──────────────────────────────────────────────────

    /**
     * Records a PONG from a specific session, resetting that session's missed-heartbeat counter.
     * Call this from the handler with the actual session that sent the PONG.
     */
    public void recordPong(WebSocketSession session) {
        session.getAttributes().put(ATTR_LAST_PONG, Instant.now());
        session.getAttributes().put(ATTR_MISSED, 0);
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
                .type(WsMessageType.PING)
                .build();

        String pingJson;
        try {
            pingJson = objectMapper.writeValueAsString(ping);
        } catch (Exception e) {
            log.error("Failed to serialise PING frame: {}", e.getMessage());
            return;
        }

        for (Map.Entry<String, CopyOnWriteArrayList<WebSocketSession>> entry : sessions.entrySet()) {
            String userId = entry.getKey();
            CopyOnWriteArrayList<WebSocketSession> sessionList = entry.getValue();

            List<WebSocketSession> toEvict = new ArrayList<>();

            for (WebSocketSession session : sessionList) {
                if (!session.isOpen()) {
                    toEvict.add(session);
                    continue;
                }

                int missed = (int) session.getAttributes().getOrDefault(ATTR_MISSED, 0);
                if (missed >= props.getMaxMissedHeartbeats()) {
                    log.warn("Evicting unresponsive session: userId={} sessionId={} missedHeartbeats={}",
                            userId, session.getId(), missed);
                    closeQuietly(session);
                    toEvict.add(session);
                    continue;
                }

                session.getAttributes().put(ATTR_MISSED, missed + 1);
                sendToSession(session, pingJson);
            }

            // clean up evicted sessions
            if (!toEvict.isEmpty()) {
                toEvict.forEach(s -> {
                    sessionList.remove(s);
                    sessionLocks.remove(s.getId());
                });
                if (sessionList.isEmpty()) {
                    sessions.remove(userId, sessionList);
                }
            }
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Sends a pre-serialised JSON string to a specific session using a per-session lock. */
    private boolean sendToSession(WebSocketSession session, String json) {
        Object lock = sessionLocks.computeIfAbsent(session.getId(), k -> new Object());
        synchronized (lock) {
            try {
                session.sendMessage(new TextMessage(json));
                return true;
            } catch (IOException e) {
                log.error("Failed to send to sessionId={}: {}", session.getId(), e.getMessage());
                return false;
            }
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) session.close();
        } catch (IOException e) {
            log.warn("Error closing session {}: {}", session.getId(), e.getMessage());
        }
    }
}
