package com.vplmqa.notification.repository;

import com.vplmqa.notification.entity.RetryQueue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RetryQueueRepository extends JpaRepository<RetryQueue, UUID> {
    List<RetryQueue> findByNextAttemptAtBefore(java.time.Instant now);
}
