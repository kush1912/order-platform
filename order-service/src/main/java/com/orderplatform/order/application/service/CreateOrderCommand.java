package com.orderplatform.order.application.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateOrderCommand(
        UUID customerId,
        String currency,
        List<OrderItemCommand> items) {

    public record OrderItemCommand(
            String sku,
            int quantity,
            BigDecimal unitPrice) {
    }
}
