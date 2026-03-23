package com.county_cars.vroom.modules.chat.service.impl;

import com.county_cars.vroom.common.exception.BadRequestException;
import com.county_cars.vroom.common.exception.NotFoundException;
import com.county_cars.vroom.common.exception.UnauthorizedException;
import com.county_cars.vroom.modules.chat.config.ChatProperties;
import com.county_cars.vroom.modules.chat.dto.*;
import com.county_cars.vroom.modules.chat.entity.*;
import com.county_cars.vroom.modules.chat.repository.ChatRepository;
import com.county_cars.vroom.modules.chat.repository.MessageRepository;
import com.county_cars.vroom.modules.chat.service.ChatService;
import com.county_cars.vroom.modules.chat.websocket.ChatSessionManager;
import com.county_cars.vroom.modules.user_profile.entity.UserProfile;
import com.county_cars.vroom.modules.user_profile.repository.UserProfileRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ChatServiceImpl implements ChatService {

    private final ChatRepository         chatRepository;
    private final MessageRepository      messageRepository;
    private final UserProfileRepository  userProfileRepository;
    private final ChatSessionManager     sessionManager;
    private final ChatProperties         props;

    // ── Metrics ───────────────────────────────────────────────────────────────
    private final Counter messagesSentCounter;
    private final Counter messagesDeliveredCounter;
    private final Counter duplicateRejectedCounter;
    private final Timer   deliveryLatencyTimer;

    public ChatServiceImpl(ChatRepository chatRepository,
                           MessageRepository messageRepository,
                           UserProfileRepository userProfileRepository,
                           ChatSessionManager sessionManager,
                           ChatProperties props,
                           MeterRegistry meterRegistry) {
        this.chatRepository        = chatRepository;
        this.messageRepository     = messageRepository;
        this.userProfileRepository = userProfileRepository;
        this.sessionManager        = sessionManager;
        this.props                 = props;

        this.messagesSentCounter       = Counter.builder("chat.messages.sent")
                .description("Total messages persisted").register(meterRegistry);
        this.messagesDeliveredCounter  = Counter.builder("chat.messages.delivered")
                .description("Total messages delivered live").register(meterRegistry);
        this.duplicateRejectedCounter  = Counter.builder("chat.messages.duplicate_rejected")
                .description("Duplicate messages rejected").register(meterRegistry);
        this.deliveryLatencyTimer      = Timer.builder("chat.delivery.latency")
                .description("Latency from persist to live delivery").register(meterRegistry);
    }

    // ── Open / get chat ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public ChatResponse openOrGetChat(Long currentUserId, Long otherUserId, Long listingId) {
        if (currentUserId.equals(otherUserId)) {
            throw new BadRequestException("Cannot open a chat with yourself");
        }

        // Canonical ordering: lower ID is always participantOne
        long p1 = Math.min(currentUserId, otherUserId);
        long p2 = Math.max(currentUserId, otherUserId);

        Chat chat = chatRepository.findByParticipantsAndListing(p1, p2, listingId)
                .orElseGet(() -> {
                    UserProfile up1 = requireUser(p1);
                    UserProfile up2 = requireUser(p2);
                    return chatRepository.save(Chat.builder()
                            .participantOne(up1)
                            .participantTwo(up2)
                            .listingId(listingId)
                            .build());
                });

        return toChatResponse(chat, currentUserId);
    }

    // ── List chats ────────────────────────────────────────────────────────────

    @Override
    public List<ChatResponse> listChats(Long currentUserId) {
        return chatRepository.findAllByParticipant(currentUserId).stream()
                .map(c -> toChatResponse(c, currentUserId))
                .toList();
    }

    // ── Process inbound message ───────────────────────────────────────────────

    @Override
    @Transactional
    public WsOutboundMessage processMessage(Long senderProfileId, WsInboundMessage inbound) {
        // 1. Validate size
        if (inbound.getContent() != null
                && inbound.getContent().getBytes().length > props.getMaxMessageSizeBytes()) {
            throw new BadRequestException("Message exceeds maximum allowed size");
        }

        // 2. Validate chat membership
        if (!chatRepository.isParticipant(inbound.getChatId(), senderProfileId)) {
            throw new UnauthorizedException("You are not a participant of chat " + inbound.getChatId());
        }

        // 3. Deduplication — check before persisting
        if (messageRepository.findBySenderIdAndMessageClientId(
                senderProfileId, inbound.getMessageClientId()).isPresent()) {
            duplicateRejectedCounter.increment();
            log.debug("Duplicate message rejected: senderId={} clientId={}",
                    senderProfileId, inbound.getMessageClientId());
            throw new BadRequestException("Duplicate message: " + inbound.getMessageClientId());
        }

        // 4. Load entities
        Chat        chat   = chatRepository.findById(inbound.getChatId())
                .orElseThrow(() -> new NotFoundException("Chat not found: " + inbound.getChatId()));
        UserProfile sender = requireUser(senderProfileId);

        // 5. Persist message
        Message message;
        try {
            message = messageRepository.save(Message.builder()
                    .chat(chat)
                    .sender(sender)
                    .content(inbound.getContent())
                    .messageType(inbound.getMessageType() != null
                            ? inbound.getMessageType() : MessageType.TEXT)
                    .status(MessageStatus.SENT)
                    .messageClientId(inbound.getMessageClientId())
                    .build());
        } catch (DataIntegrityViolationException e) {
            // Race condition: two identical frames arrived simultaneously
            duplicateRejectedCounter.increment();
            throw new BadRequestException("Duplicate message: " + inbound.getMessageClientId());
        }

        messagesSentCounter.increment();

        // 6. Update chat.lastMessageAt
        chat.setLastMessageAt(message.getCreatedAt());
        chatRepository.save(chat);

        // 7. Build outbound frame
        WsOutboundMessage outbound = toOutboundMessage(message, WsMessageType.CHAT);

        // 8. Attempt live delivery to receiver
        Long receiverId = resolveReceiverId(chat, senderProfileId);
        UserProfile receiver = requireUser(receiverId);

        Instant deliveryStart = Instant.now();
        boolean delivered = sessionManager.sendToUser(receiver.getKeycloakUserId(), outbound);

        if (delivered) {
            message.setStatus(MessageStatus.DELIVERED);
            messageRepository.save(message);
            outbound.setStatus(MessageStatus.DELIVERED);
            messagesDeliveredCounter.increment();
            deliveryLatencyTimer.record(
                    java.time.Duration.between(deliveryStart, Instant.now()));
            log.debug("Message {} delivered live to userId={}", message.getId(), receiverId);
        } else {
            log.debug("Message {} stored for offline delivery to userId={}", message.getId(), receiverId);
        }

        return outbound;
    }

    // ── Message history ───────────────────────────────────────────────────────

    @Override
    public Page<MessageResponse> getMessages(Long chatId, Long currentUserId, Pageable pageable) {
        if (!chatRepository.isParticipant(chatId, currentUserId)) {
            throw new UnauthorizedException("You are not a participant of chat " + chatId);
        }
        return messageRepository.findAllByChatIdOrderByCreatedAtDesc(chatId, pageable)
                .map(this::toMessageResponse);
    }

    // ── Status updates ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void markDelivered(Long chatId, Long userId) {
        int updated = messageRepository.bulkUpdateStatus(
                chatId, userId, MessageStatus.SENT, MessageStatus.DELIVERED);
        log.debug("Marked {} messages as DELIVERED in chatId={} for userId={}", updated, chatId, userId);
    }

    // ── Offline message recovery ──────────────────────────────────────────────

    @Override
    @Transactional
    public void deliverOfflineMessages(Long userId, String keycloakUserId) {
        List<Message> pending = messageRepository.findAllUndeliveredForUser(userId);
        if (pending.isEmpty()) return;

        log.info("Delivering {} offline messages to userId={}", pending.size(), userId);

        for (Message m : pending) {
            WsOutboundMessage frame = toOutboundMessage(m, WsMessageType.DELIVERY);
            boolean sent = sessionManager.sendToUser(keycloakUserId, frame);
            if (sent) {
                m.setStatus(MessageStatus.DELIVERED);
            }
        }
        messageRepository.saveAll(pending);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UserProfile requireUser(Long id) {
        return userProfileRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
    }

    private Long resolveReceiverId(Chat chat, Long senderId) {
        return chat.getParticipantOne().getId().equals(senderId)
                ? chat.getParticipantTwo().getId()
                : chat.getParticipantOne().getId();
    }

    private ChatResponse toChatResponse(Chat chat, Long currentUserId) {
        UserProfile other = chat.getParticipantOne().getId().equals(currentUserId)
                ? chat.getParticipantTwo()
                : chat.getParticipantOne();

        long unread = messageRepository
                .findUndeliveredForUser(chat.getId(), currentUserId).size();

        return ChatResponse.builder()
                .id(chat.getId())
                .otherUserId(other.getId())
                .otherUserDisplayName(other.getDisplayName())
                .listingId(chat.getListingId())
                .lastMessageAt(chat.getLastMessageAt())
                .unreadCount(unread)
                .build();
    }

    private MessageResponse toMessageResponse(Message m) {
        return MessageResponse.builder()
                .id(m.getId())
                .chatId(m.getChat().getId())
                .senderId(m.getSender().getId())
                .senderDisplayName(m.getSender().getDisplayName())
                .content(m.getContent())
                .messageType(m.getMessageType())
                .status(m.getStatus())
                .messageClientId(m.getMessageClientId())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private WsOutboundMessage toOutboundMessage(Message m, WsMessageType type) {
        return WsOutboundMessage.builder()
                .type(type)
                .messageId(m.getId())
                .chatId(m.getChat().getId())
                .senderId(m.getSender().getId())
                .senderDisplayName(m.getSender().getDisplayName())
                .content(m.getContent())
                .messageType(m.getMessageType())
                .status(m.getStatus())
                .messageClientId(m.getMessageClientId())
                .createdAt(m.getCreatedAt())
                .build();
    }
}

