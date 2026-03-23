package com.county_cars.vroom.modules.chat.repository;

import com.county_cars.vroom.modules.chat.entity.Message;
import com.county_cars.vroom.modules.chat.entity.MessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /** Paginated message history for a chat, newest first. */
    Page<Message> findAllByChatIdOrderByCreatedAtDesc(Long chatId, Pageable pageable);

    /** Undelivered messages for a chat — used on reconnect. */
    @Query("""
            SELECT m FROM Message m
            WHERE m.chat.id = :chatId
              AND m.sender.id <> :userId
              AND m.status IN ('SENT', 'DELIVERED')
            ORDER BY m.createdAt ASC
            """)
    List<Message> findUndeliveredForUser(
            @Param("chatId") Long chatId,
            @Param("userId") Long userId);

    /** All SENT messages across all chats for a user — used on reconnect. */
    @Query("""
            SELECT m FROM Message m
            WHERE m.sender.id <> :userId
              AND m.status = 'SENT'
              AND (m.chat.participantOne.id = :userId OR m.chat.participantTwo.id = :userId)
            ORDER BY m.createdAt ASC
            """)
    List<Message> findAllUndeliveredForUser(@Param("userId") Long userId);

    /** Deduplication check. */
    Optional<Message> findBySenderIdAndMessageClientId(Long senderId, String messageClientId);

    /** Bulk-update SENT → DELIVERED for all messages in a chat directed at a user. */
    @Modifying
    @Query("""
            UPDATE Message m SET m.status = :newStatus
            WHERE m.chat.id = :chatId
              AND m.sender.id <> :userId
              AND m.status = :currentStatus
            """)
    int bulkUpdateStatus(
            @Param("chatId") Long chatId,
            @Param("userId") Long userId,
            @Param("currentStatus") MessageStatus currentStatus,
            @Param("newStatus") MessageStatus newStatus);
}

