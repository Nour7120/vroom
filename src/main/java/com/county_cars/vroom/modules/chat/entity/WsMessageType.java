package com.county_cars.vroom.modules.chat.entity;

public enum WsMessageType {
    /** A chat message sent by a user */
    CHAT,
    /** Server acknowledges a received message */
    ACK,
    /** Server delivers an offline message to a reconnected user */
    DELIVERY,
    /** Status update: DELIVERED or READ */
    STATUS_UPDATE,
    /** Client or server heartbeat ping */
    PING,
    /** Client or server heartbeat pong */
    PONG,
    /** Server-sent error */
    ERROR
}

