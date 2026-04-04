package com.county_cars.vroom.modules.attachment.entity;

/**
 * Represents the lifecycle state of an {@link Attachment}.
 *
 * <ul>
 *   <li>{@code UPLOADED}  – file has been uploaded but not yet associated with any domain object</li>
 *   <li>{@code LINKED}    – file is actively referenced by at least one domain object</li>
 *   <li>{@code DELETED}   – file has been logically deleted and is pending physical removal</li>
 * </ul>
 *
 * Persisted as a {@code VARCHAR(20)} column via {@code @Enumerated(EnumType.STRING)}.
 */
public enum AttachmentStatus {
    UPLOADED,
    LINKED,
    DELETED
}

