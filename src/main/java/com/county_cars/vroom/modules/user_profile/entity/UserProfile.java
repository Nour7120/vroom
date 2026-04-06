package com.county_cars.vroom.modules.user_profile.entity;

import com.county_cars.vroom.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

@Entity
@Table(name = "user_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted = false")
public class UserProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keycloak_user_id", nullable = false, unique = true, length = 36)
    private String keycloakUserId;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", unique = true, nullable = false)
    private String displayName;

    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    @Column(name = "avatar_url", length = 1024)
    private String avatarUrl;

    /**
     * ID of the {@link com.county_cars.vroom.modules.attachment.entity.Attachment} record
     * backing the current profile photo.  Nullable — users without a photo have no attachment.
     * Used to delete the old file when the avatar is replaced.
     * Stored as a plain column (no FK constraint) so soft-deleted attachments don't
     * violate referential integrity.
     */
    @Column(name = "avatar_attachment_id")
    private Long avatarAttachmentId;

    @Column(name = "status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    /**
     * Tracks when the last Keycloak verification email was dispatched.
     * Used to enforce the minimum interval between resend attempts.
     */
    @Column(name = "last_verification_email_sent_at")
    private Instant lastVerificationEmailSentAt;

    /**
     * Tracks when the last Keycloak password-reset email was dispatched.
     * Used to enforce the minimum interval between reset attempts.
     */
    @Column(name = "last_password_reset_email_sent_at")
    private Instant lastPasswordResetEmailSentAt;
}
