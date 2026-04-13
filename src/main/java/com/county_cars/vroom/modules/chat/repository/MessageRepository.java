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

    /** Bulk-update status for all messages in a chat directed at a user. */
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

    /**
     * Count of unread (SENT or DELIVERED) messages per chat for a user — used in list view.
     * Returns List of [chatId (Long), count (Long)] tuples. Fixes N+1 in listChats().
     */
    @Query("""
            SELECT m.chat.id, COUNT(m)
            FROM Message m
            WHERE m.sender.id <> :userId
              AND m.status IN ('SENT', 'DELIVERED')
              AND m.chat.id IN :chatIds
            GROUP BY m.chat.id
            """)
    List<Object[]> countUnreadByChatIds(
            @Param("userId") Long userId,
            @Param("chatIds") List<Long> chatIds);

    /**
     * Count of unread (SENT or DELIVERED) messages in a single chat for a user.
     * Used for single-chat response in openOrGetChat().
     */
    @Query("""
            SELECT COUNT(m)
            FROM Message m
            WHERE m.chat.id = :chatId
              AND m.sender.id <> :userId
              AND m.status IN ('SENT', 'DELIVERED')
            """)
    long countUndeliveredForUser(@Param("chatId") Long chatId, @Param("userId") Long userId);

    /**
     * All DELIVERED messages in a chat that were sent by someone other than :userId
     * (i.e. messages the given user has received but not yet READ).
     * Fetches sender eagerly to avoid LazyInitializationException in markRead().
     */
    @Query("""
            SELECT m FROM Message m
            JOIN FETCH m.sender
            WHERE m.chat.id = :chatId
              AND m.sender.id <> :userId
              AND m.status = 'DELIVERED'
            ORDER BY m.createdAt ASC
            """)
    List<Message> findDeliveredMessagesForReader(
            @Param("chatId") Long chatId,
            @Param("userId") Long userId);
}

