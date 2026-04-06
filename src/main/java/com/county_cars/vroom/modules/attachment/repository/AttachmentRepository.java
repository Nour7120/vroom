package com.county_cars.vroom.modules.attachment.repository;

import com.county_cars.vroom.modules.attachment.entity.Attachment;
import com.county_cars.vroom.modules.attachment.entity.AttachmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    /**
     * Returns all non-deleted attachments with the given status.
     * The {@code @SQLRestriction("is_deleted = false")} on {@link Attachment}
     * automatically filters out deleted rows, so only live records are returned.
     */
    List<Attachment> findAllByStatus(AttachmentStatus status);
}
