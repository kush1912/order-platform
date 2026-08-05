package com.orderplatform.order.infrastructure.kafka;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryReservationResultEvent(
        UUID eventId,
        String eventType,
        OffsetDateTime occurredAt,
        UUID orderId,
        String status,
        String reason) {
}
