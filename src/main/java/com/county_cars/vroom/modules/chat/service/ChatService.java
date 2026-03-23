package com.county_cars.vroom.modules.chat.service;

import com.county_cars.vroom.modules.chat.dto.ChatResponse;
import com.county_cars.vroom.modules.chat.dto.MessageResponse;
import com.county_cars.vroom.modules.chat.dto.WsInboundMessage;
import com.county_cars.vroom.modules.chat.dto.WsOutboundMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ChatService {

    /** Open or retrieve a chat between the current user and another user. */
    ChatResponse openOrGetChat(Long currentUserId, Long otherUserId, Long listingId);

    /** List all chats for the current user, sorted by lastMessageAt DESC. */
    List<ChatResponse> listChats(Long currentUserId);

    /**
     * Process an inbound CHAT frame:
     * persist the message, attempt live delivery, return the outbound frame.
     */
    WsOutboundMessage processMessage(Long senderProfileId, WsInboundMessage inbound);

    /** Paginated message history for a chat (newest first). */
    Page<MessageResponse> getMessages(Long chatId, Long currentUserId, Pageable pageable);

    /** Mark all SENT messages in a chat as DELIVERED for the given user. */
    void markDelivered(Long chatId, Long userId);

    /** Deliver all pending offline messages to a user who just reconnected. */
    void deliverOfflineMessages(Long userId, String keycloakUserId);
}

