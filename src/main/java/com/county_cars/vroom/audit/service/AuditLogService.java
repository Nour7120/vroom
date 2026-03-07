package com.county_cars.vroom.audit.service;

import com.county_cars.vroom.audit.entity.AuditLog;
import com.county_cars.vroom.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Writes immutable audit records. Calls are async so they never block the main request thread.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void record(String entityType, String entityId, String action,
                       String performedBy, Map<String, Object> metadata) {
        try {
            AuditLog entry = AuditLog.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(action)
                    .performedBy(performedBy)
                    .metadata(metadata)
                    .build();
            auditLogRepository.save(entry);
            log.debug("Audit recorded: action={} entity={}#{} by={}", action, entityType, entityId, performedBy);
        } catch (Exception ex) {
            log.error("Failed to record audit log", ex);
        }
    }
}

