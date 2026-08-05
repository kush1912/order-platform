package com.orderplatform.order.application.service;

import com.orderplatform.order.domain.entity.OrderEntity;

public record CreateOrderResult(
        OrderEntity order,
        boolean replayed) {
}
