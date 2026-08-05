package com.orderplatform.order.infrastructure.kafka;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderPlacedEvent(
        UUID eventId,
        String eventType,
        OffsetDateTime occurredAt,
        UUID orderId,
        UUID customerId,
        String currency,
        List<Item> items) {

    public record Item(String sku, int quantity) {
    }
}
