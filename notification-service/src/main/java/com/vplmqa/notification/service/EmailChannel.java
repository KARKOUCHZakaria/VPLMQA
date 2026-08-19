package com.vplmqa.notification.service;

import com.vplmqa.notification.enumtype.ChannelEnum;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailChannel implements NotificationChannel {

    private final JavaMailSender mailSender;

    public EmailChannel(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public ChannelEnum channel() {
        return ChannelEnum.EMAIL;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        // Mail transport wiring is intentionally minimal in the scaffold.
        mailSender.createMimeMessage();
    }
}
