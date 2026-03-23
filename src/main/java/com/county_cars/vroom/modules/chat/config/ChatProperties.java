package com.county_cars.vroom.modules.chat.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class ChatProperties {

    @Value("${chat.websocket.heartbeat-interval-seconds:30}")
    private int heartbeatIntervalSeconds;

    @Value("${chat.websocket.max-missed-heartbeats:2}")
    private int maxMissedHeartbeats;

    @Value("${chat.message.max-size-bytes:5120}")
    private int maxMessageSizeBytes;

    @Value("${chat.rate-limit.messages-per-second:10}")
    private int messagesPerSecond;
}

