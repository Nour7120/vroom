package com.county_cars.vroom.modules.chat.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Validates the JWT token during the WebSocket handshake.
 *
 * <p>The client must supply the token either as:
 * <ul>
 *   <li>Query param: {@code /ws/chat?token=<JWT>}</li>
 *   <li>Header: {@code Authorization: Bearer <JWT>}</li>
 * </ul>
 *
 * On success, the resolved Keycloak user ID (sub claim) is stored in the
 * handshake attributes map under key {@code "userId"} so the WebSocket handler
 * can read it without repeating validation.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_USER_ID = "userId";

    private final JwtDecoder jwtDecoder;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        String token = extractToken(request);
        if (token == null) {
            log.warn("WebSocket handshake rejected: no token supplied");
            return false;
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            String userId = jwt.getSubject(); // Keycloak UUID
            attributes.put(ATTR_USER_ID, userId);
            log.debug("WebSocket handshake authenticated: userId={}", userId);
            return true;
        } catch (JwtException e) {
            log.warn("WebSocket handshake rejected: invalid JWT — {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // no-op
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractToken(ServerHttpRequest request) {
        // 1. Try Authorization header
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 2. Fall back to query parameter ?token=
        String query = request.getURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    return param.substring(6);
                }
            }
        }
        return null;
    }
}


