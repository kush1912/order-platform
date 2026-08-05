package com.orderplatform.order.infrastructure.kafka;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryReleaseRequestedEvent(
        UUID eventId,
        String eventType,
        OffsetDateTime occurredAt,
        UUID orderId) {
}
