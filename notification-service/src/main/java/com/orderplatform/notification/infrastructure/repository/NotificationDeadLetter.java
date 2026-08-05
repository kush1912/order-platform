package com.orderplatform.notification.infrastructure.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationDeadLetter(
        UUID id,
        String deadLetterTopic,
        int deadLetterPartition,
        long deadLetterOffset,
        String originalTopic,
        String payload,
        String failureClass,
        String failureMessage,
        OffsetDateTime createdAt) {
}
