package com.orderplatform.inventory.infrastructure.kafka;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryReleasedEvent(
        UUID eventId,
        String eventType,
        OffsetDateTime occurredAt,
        UUID orderId) {
}
