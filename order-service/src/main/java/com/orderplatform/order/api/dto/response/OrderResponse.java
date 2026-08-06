package com.orderplatform.order.api.dto.response;

import com.orderplatform.order.domain.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        String reservationFailureReason,
        Integer etaDays,
        String currency,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public record OrderItemResponse(
            String sku,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {
    }
}
