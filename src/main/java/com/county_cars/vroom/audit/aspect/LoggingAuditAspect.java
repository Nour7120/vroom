package com.county_cars.vroom.audit.aspect;

import com.county_cars.vroom.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * AOP aspect that:
 *  - Logs entry / exit / execution time for every service method (INFO)
 *  - Records audit entries for create / update / delete operations
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LoggingAuditAspect {

    private final AuditLogService auditLogService;

    // ─── Pointcuts ───────────────────────────────────────────────────────────

    /** Matches all methods in service impl packages */
    @Pointcut("execution(* com.county_cars.vroom..service.impl.*ServiceImpl.*(..))")
    public void serviceLayer() {}

    /** Matches create operations */
    @Pointcut("execution(* com.county_cars.vroom..service.impl.*ServiceImpl.create*(..))")
    public void createOps() {}

    /** Matches update operations */
    @Pointcut("execution(* com.county_cars.vroom..service.impl.*ServiceImpl.update*(..))")
    public void updateOps() {}

    /** Matches delete operations */
    @Pointcut("execution(* com.county_cars.vroom..service.impl.*ServiceImpl.delete*(..))")
    public void deleteOps() {}

    // ─── Logging ─────────────────────────────────────────────────────────────

    @Around("serviceLayer()")
    public Object logAroundService(ProceedingJoinPoint pjp) throws Throwable {
        String className  = pjp.getSignature().getDeclaringType().getSimpleName();
        String methodName = pjp.getSignature().getName();

        log.info("[START] {}.{}()", className, methodName);
        long start = System.currentTimeMillis();

        try {
            Object result = pjp.proceed();
            long elapsed = System.currentTimeMillis() - start;
            log.info("[END]   {}.{}() – {}ms", className, methodName, elapsed);
            return result;
        } catch (Throwable ex) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[ERROR] {}.{}() – {}ms – {}", className, methodName, elapsed, ex.getMessage());
            throw ex;
        }
    }

    // ─── Audit – after successful return ─────────────────────────────────────

    @AfterReturning(pointcut = "createOps()", returning = "result")
    public void auditCreate(JoinPoint jp, Object result) {
        writeAudit(jp, result, "CREATE");
    }

    @AfterReturning(pointcut = "updateOps()", returning = "result")
    public void auditUpdate(JoinPoint jp, Object result) {
        writeAudit(jp, result, "UPDATE");
    }

    @After("deleteOps()")
    public void auditDelete(JoinPoint jp) {
        writeAudit(jp, null, "DELETE");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void writeAudit(JoinPoint jp, Object result, String action) {
        try {
            String entityType  = resolveEntityType(jp);
            String entityId    = resolveEntityId(jp, result);
            String performedBy = resolveCurrentUser();

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("method", jp.getSignature().getName());
            metadata.put("args", Arrays.toString(jp.getArgs()));

            auditLogService.record(entityType, entityId, action, performedBy, metadata);
        } catch (Exception ex) {
            log.warn("Audit write failed for {}: {}", jp.getSignature().getName(), ex.getMessage());
        }
    }

    /** Derive entity type from the service class name, e.g. UserProfileServiceImpl → UserProfile */
    private String resolveEntityType(JoinPoint jp) {
        String simpleName = jp.getSignature().getDeclaringType().getSimpleName();
        return simpleName.replace("ServiceImpl", "");
    }

    /**
     * Tries to get entity ID from the return value (if it has an getId() method)
     * or falls back to the first Long argument.
     */
    private String resolveEntityId(JoinPoint jp, Object result) {
        if (result != null) {
            try {
                Object id = result.getClass().getMethod("getId").invoke(result);
                if (id != null) return id.toString();
            } catch (Exception ignored) {}
        }
        for (Object arg : jp.getArgs()) {
            if (arg instanceof Long || arg instanceof Integer) return arg.toString();
        }
        return "unknown";
    }

    private String resolveCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null) ? auth.getName() : "system";
    }
}

