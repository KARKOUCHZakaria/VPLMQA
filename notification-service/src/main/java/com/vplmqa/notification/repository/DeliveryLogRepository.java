package com.vplmqa.notification.repository;

import com.vplmqa.notification.entity.DeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryLogRepository extends JpaRepository<DeliveryLog, UUID> {
    List<DeliveryLog> findByUserId(UUID userId);
}
