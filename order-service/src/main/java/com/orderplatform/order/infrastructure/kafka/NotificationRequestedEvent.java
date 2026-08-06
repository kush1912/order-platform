package com.orderplatform.order.infrastructure.kafka;

import java.util.UUID;

/**
 * Payload published to {@code notifications.requested.v1} once an order has been
 * created and accepted (inventory reserved / order confirmed). The field shape
 * matches the notification-service consumer contract.
 */
public record NotificationRequestedEvent(
        UUID eventId,
        String clientReference,
        String channel,
        String email,
        String phoneNumber,
        String subject,
        String message,
        String whatsappTemplateName) {
}
