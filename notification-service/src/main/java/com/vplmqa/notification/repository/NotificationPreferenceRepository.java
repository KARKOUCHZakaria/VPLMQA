package com.vplmqa.notification.repository;

import com.vplmqa.notification.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
    Optional<NotificationPreference> findByUserIdAndProjectId(UUID userId, UUID projectId);
    List<NotificationPreference> findByUserId(UUID userId);
}
