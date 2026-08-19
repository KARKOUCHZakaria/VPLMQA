package com.vplmqa.notification.dto;

import com.vplmqa.notification.enumtype.ChannelEnum;
import com.vplmqa.notification.enumtype.DeliveryStatusEnum;

import java.time.Instant;
import java.util.UUID;

public record DeliveryLogResponse(
        UUID id,
        UUID userId,
        ChannelEnum channel,
        String subject,
        String body,
        DeliveryStatusEnum status,
        Instant sentAt,
        String failureReason,
        int attempts
) {
}
