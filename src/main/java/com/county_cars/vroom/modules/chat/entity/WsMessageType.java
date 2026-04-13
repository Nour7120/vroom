package com.county_cars.vroom.modules.chat.entity;

public enum WsMessageType {
    /** A chat message sent by a user */
    CHAT,
    /** Server acknowledges a received message */
    ACK,
    /** Server delivers an offline message to a reconnected user */
    DELIVERY,
    /** Status update: DELIVERED or READ — sent by server to message sender */
    STATUS_UPDATE,
    /** Client notifies the server that it has read all messages in a chat */
    READ_RECEIPT,
    /** Client or server heartbeat ping */
    PING,
    /** Client or server heartbeat pong */
    PONG,
    /** Server-sent error */
    ERROR
}

