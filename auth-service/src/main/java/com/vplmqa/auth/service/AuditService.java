package com.vplmqa.auth.service;

import com.vplmqa.auth.entity.AuditLog;
import com.vplmqa.auth.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for writing audit logs.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Creates the audit service.
     *
     * @param auditLogRepository audit log repository
     */
    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Writes an audit log entry.
     *
     * @param userId user ID
     * @param action action name
     * @param request HTTP request
     * @param success success flag
     */
    public void log(UUID userId, String action, HttpServletRequest request, boolean success) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setIpAddress(resolveIp(request));
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setSuccess(success);
        auditLogRepository.save(log);
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
