package com.vplmqa.notification.service;

import com.vplmqa.notification.enumtype.ChannelEnum;
import org.springframework.stereotype.Service;

@Service
public class SlackChannel implements NotificationChannel {

    @Override
    public ChannelEnum channel() {
        return ChannelEnum.SLACK;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        // Outbound webhook call is stubbed at the transport boundary for scaffold completeness.
    }
}
