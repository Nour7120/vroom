package com.county_cars.vroom.modules.chat.websocket;

import com.county_cars.vroom.modules.chat.config.ChatProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple token-bucket per-user rate limiter.
 *
 * <p>Each user starts with {@code messagesPerSecond} tokens.
 * Tokens refill every second via a scheduled call from the WebSocket handler.
 * A message is rejected when the bucket is empty.</p>
 */
@Component
@RequiredArgsConstructor
public class MessageRateLimiter {

    private final ChatProperties props;

    /** userId → remaining token count within the current window */
    private final Map<String, AtomicInteger> buckets = new ConcurrentHashMap<>();

    /**
     * @return {@code true} if the message is allowed; {@code false} if rate-limited
     */
    public boolean tryConsume(String userId) {
        AtomicInteger bucket = buckets.computeIfAbsent(
                userId, k -> new AtomicInteger(props.getMessagesPerSecond()));
        int remaining = bucket.decrementAndGet();
        return remaining >= 0;
    }

    /** Called once per second (by the WebSocket handler scheduler) to refill buckets. */
    public void refillAll() {
        int max = props.getMessagesPerSecond();
        buckets.forEach((userId, bucket) -> bucket.set(max));
    }

    public void removeUser(String userId) {
        buckets.remove(userId);
    }
}

