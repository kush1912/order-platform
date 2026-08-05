package com.orderplatform.order.application.service;

import com.orderplatform.order.domain.entity.OrderEntity;

public record CancelOrderResult(OrderEntity order, boolean replayed) {
}
