package com.vplmqa.notification.service;

import com.vplmqa.notification.enumtype.ChannelEnum;

public interface NotificationChannel {
    ChannelEnum channel();
    void send(String recipient, String subject, String body);
}
