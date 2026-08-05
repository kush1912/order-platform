package com.orderplatform.order.domain.exception;

import com.orderplatform.order.domain.entity.OrderStatus;

import java.util.UUID;

public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(UUID orderId, OrderStatus status, String operation) {
        super("Order " + orderId + " cannot " + operation + " while it is " + status);
    }
}
