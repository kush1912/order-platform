package com.orderplatform.inventory.api.dto.response;

import java.time.OffsetDateTime;

public record InventoryResponse(
        String sku,
        long onHandQuantity,
        long reservedQuantity,
        long availableQuantity,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
