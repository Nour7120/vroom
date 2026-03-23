package com.county_cars.vroom.modules.chat.repository;

import com.county_cars.vroom.modules.chat.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {

    /**
     * Find an existing conversation between two users, optionally for a specific listing.
     * participantOne / participantTwo are always stored in ascending ID order.
     */
    @Query("""
            SELECT c FROM Chat c
            WHERE c.participantOne.id = :p1
              AND c.participantTwo.id = :p2
              AND (:listingId IS NULL AND c.listingId IS NULL
                   OR c.listingId = :listingId)
            """)
    Optional<Chat> findByParticipantsAndListing(
            @Param("p1") Long p1,
            @Param("p2") Long p2,
            @Param("listingId") Long listingId);

    /**
     * All conversations for a user, newest message first.
     */
    @Query("""
            SELECT c FROM Chat c
            WHERE c.participantOne.id = :userId
               OR c.participantTwo.id = :userId
            ORDER BY c.lastMessageAt DESC NULLS LAST
            """)
    List<Chat> findAllByParticipant(@Param("userId") Long userId);

    /**
     * Verify a user is a participant of a given chat.
     */
    @Query("""
            SELECT COUNT(c) > 0 FROM Chat c
            WHERE c.id = :chatId
              AND (c.participantOne.id = :userId OR c.participantTwo.id = :userId)
            """)
    boolean isParticipant(@Param("chatId") Long chatId, @Param("userId") Long userId);
}

