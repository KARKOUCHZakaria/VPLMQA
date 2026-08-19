package com.vplmqa.auth.repository;

import com.vplmqa.auth.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for audit logs.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
