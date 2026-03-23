package com.county_cars.vroom.modules.chat.entity;

import com.county_cars.vroom.common.entity.BaseEntity;
import com.county_cars.vroom.modules.user_profile.entity.UserProfile;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A conversation between exactly two users, optionally linked to a marketplace listing.
 *
 * <p>To guarantee a canonical unique pair, the application always stores
 * {@code participantOne.id < participantTwo.id}. The DB CHECK constraint
 * enforces the same invariant at the storage level.</p>
 */
@Entity
@Table(name = "chat")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_one", nullable = false)
    private UserProfile participantOne;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_two", nullable = false)
    private UserProfile participantTwo;

    /** Optional reference to the marketplace listing this conversation was started from. */
    @Column(name = "listing_id")
    private Long listingId;

    /** Timestamp of the most recent message — used to sort conversations. */
    @Column(name = "last_message_at")
    private Instant lastMessageAt;
}

