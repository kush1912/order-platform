package com.orderplatform.notification.domain.entity;

public record DeliveryResult(
        NotificationChannel channel,
        DeliveryStatus status,
        String providerMessageId,
        String failureCode) {
}
