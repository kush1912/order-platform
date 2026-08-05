package com.orderplatform.notification.infrastructure.kafka;

import com.orderplatform.notification.domain.entity.NotificationChannel;

import java.util.UUID;

public record NotificationRequestedEvent(
        UUID eventId,
        String clientReference,
        NotificationChannel channel,
        String email,
        String phoneNumber,
        String subject,
        String message,
        String whatsappTemplateName) {
}
