package com.vplmqa.notification.service;

import com.vplmqa.notification.dto.NotificationPreferenceRequest;
import com.vplmqa.notification.dto.NotificationPreferenceResponse;
import com.vplmqa.notification.entity.DeliveryLog;
import com.vplmqa.notification.entity.NotificationPreference;
import com.vplmqa.notification.entity.RetryQueue;
import com.vplmqa.notification.enumtype.ChannelEnum;
import com.vplmqa.notification.enumtype.DeliveryStatusEnum;
import com.vplmqa.notification.repository.DeliveryLogRepository;
import com.vplmqa.notification.repository.NotificationPreferenceRepository;
import com.vplmqa.notification.repository.RetryQueueRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final DeliveryLogRepository deliveryLogRepository;
    private final RetryQueueRepository retryQueueRepository;
    private final List<NotificationChannel> channels;

    public NotificationService(NotificationPreferenceRepository preferenceRepository,
                               DeliveryLogRepository deliveryLogRepository,
                               RetryQueueRepository retryQueueRepository,
                               List<NotificationChannel> channels) {
        this.preferenceRepository = preferenceRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.retryQueueRepository = retryQueueRepository;
        this.channels = channels;
    }

    @Transactional(readOnly = true)
    public NotificationPreferenceResponse getPreferences(UUID userId, UUID projectId) {
        return preferenceRepository.findByUserIdAndProjectId(userId, projectId)
                .map(this::mapPreference)
                .orElseGet(() -> defaultPreference(userId, projectId));
    }

    @Transactional
    public NotificationPreferenceResponse updatePreferences(NotificationPreferenceRequest request) {
        NotificationPreference preference = preferenceRepository.findByUserIdAndProjectId(request.userId(), request.projectId())
                .orElseGet(NotificationPreference::new);
        preference.setUserId(request.userId());
        preference.setProjectId(request.projectId());
        preference.setEmailEnabled(request.emailEnabled());
        preference.setSlackEnabled(request.slackEnabled());
        preference.setSlackWebhook(request.slackWebhook());
        preference.setEmailAddress(request.emailAddress());
        return mapPreference(preferenceRepository.save(preference));
    }

    @Transactional(readOnly = true)
    public List<DeliveryLog> getLogs(UUID userId) {
        if (userId == null) {
            return deliveryLogRepository.findAll();
        }
        return deliveryLogRepository.findByUserId(userId);
    }

    @Transactional
    public void recordAndSend(UUID userId, ChannelEnum channel, String subject, String body, String recipient) {
        DeliveryLog log = new DeliveryLog();
        log.setUserId(userId);
        log.setChannel(channel);
        log.setSubject(subject);
        log.setBody(body);
        log.setStatus(DeliveryStatusEnum.PENDING);
        log.setAttempts(0);
        DeliveryLog saved = deliveryLogRepository.save(log);
        send(channel, recipient, subject, body, saved);
    }

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void processRetryQueue() {
        Instant now = Instant.now();
        for (RetryQueue retryQueue : retryQueueRepository.findByNextAttemptAtBefore(now)) {
            DeliveryLog log = retryQueue.getDeliveryLog();
            if (log.getAttempts() >= 5) {
                log.setStatus(DeliveryStatusEnum.FAILED);
                deliveryLogRepository.save(log);
                retryQueueRepository.delete(retryQueue);
                continue;
            }
            log.setAttempts(log.getAttempts() + 1);
            log.setStatus(DeliveryStatusEnum.PENDING);
            deliveryLogRepository.save(log);
            retryQueue.setNextAttemptAt(now.plusSeconds((long) Math.pow(2, log.getAttempts()) * 60));
            retryQueueRepository.save(retryQueue);
        }
    }

    private NotificationPreferenceResponse mapPreference(NotificationPreference preference) {
        return new NotificationPreferenceResponse(preference.getId(), preference.getUserId(), preference.getProjectId(), preference.isEmailEnabled(), preference.isSlackEnabled(), preference.getSlackWebhook(), preference.getEmailAddress(), preference.getCreatedAt(), preference.getUpdatedAt());
    }

    private NotificationPreferenceResponse defaultPreference(UUID userId, UUID projectId) {
        return new NotificationPreferenceResponse(null, userId, projectId, true, false, null, null, null, null);
    }

    private void send(ChannelEnum channel, String recipient, String subject, String body, DeliveryLog log) {
        NotificationChannel notificationChannel = channels.stream().filter(c -> c.channel() == channel).findFirst().orElseThrow();
        try {
            notificationChannel.send(recipient, subject, body);
            log.setStatus(DeliveryStatusEnum.SENT);
        } catch (Exception ex) {
            log.setStatus(DeliveryStatusEnum.FAILED);
            log.setFailureReason(ex.getMessage());
            RetryQueue retryQueue = new RetryQueue();
            retryQueue.setDeliveryLog(log);
            retryQueue.setNextAttemptAt(Instant.now().plus(Duration.ofMinutes(1)));
            retryQueueRepository.save(retryQueue);
        }
        deliveryLogRepository.save(log);
    }

    /**
     * Observer pattern: notifies all applicable channels based on user preferences.
     */
    @Transactional
    public void notifyAllChannels(UUID userId, UUID projectId, String subject, String body) {
        preferenceRepository.findByUserIdAndProjectId(userId, projectId).ifPresent(pref -> {
            for (NotificationChannel channel : channels) {
                if (channel.channel() == ChannelEnum.EMAIL && pref.isEmailEnabled() && pref.getEmailAddress() != null) {
                    recordAndSend(userId, ChannelEnum.EMAIL, subject, body, pref.getEmailAddress());
                } else if (channel.channel() == ChannelEnum.SLACK && pref.isSlackEnabled() && pref.getSlackWebhook() != null) {
                    recordAndSend(userId, ChannelEnum.SLACK, subject, body, pref.getSlackWebhook());
                }
            }
        });
    }
}
