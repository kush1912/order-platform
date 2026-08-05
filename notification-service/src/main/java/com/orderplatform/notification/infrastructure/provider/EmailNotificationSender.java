package com.orderplatform.notification.infrastructure.provider;

import com.orderplatform.notification.application.service.NotificationSender;
import com.orderplatform.notification.domain.entity.NotificationChannel;
import com.orderplatform.notification.domain.entity.NotificationCommand;
import com.orderplatform.notification.domain.exception.NotificationProviderException;
import com.orderplatform.notification.infrastructure.configuration.NotificationProviderProperties;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final NotificationProviderProperties properties;

    public EmailNotificationSender(
            JavaMailSender mailSender,
            NotificationProviderProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public String send(NotificationCommand command) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.email().from());
        message.setTo(command.email());
        message.setSubject(command.subject());
        message.setText(command.message());

        try {
            mailSender.send(message);
            return command.clientReference();
        } catch (MailAuthenticationException exception) {
            throw new NotificationProviderException(
                    channel(),
                    false,
                    "EMAIL_AUTHENTICATION_FAILED",
                    "Email provider authentication failed",
                    exception);
        } catch (MailException exception) {
            throw new NotificationProviderException(
                    channel(),
                    true,
                    "EMAIL_PROVIDER_ERROR",
                    "Email provider call failed",
                    exception);
        }
    }
}
