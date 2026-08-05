package com.orderplatform.notification.domain.entity;

import java.util.Set;

public record NotificationCommand(
        String clientReference,
        Set<NotificationChannel> channels,
        String email,
        String phoneNumber,
        String subject,
        String message,
        String whatsappTemplateName) {
}
