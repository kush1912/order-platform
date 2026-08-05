package com.orderplatform.order.api.mapper;

import com.orderplatform.order.api.dto.request.CreateOrderRequest;
import com.orderplatform.order.api.dto.response.OrderResponse;
import com.orderplatform.order.application.service.CreateOrderCommand;
import com.orderplatform.order.domain.entity.OrderEntity;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.Comparator;

@Component
public class OrderMapper {

    public CreateOrderCommand toCommand(CreateOrderRequest request) {
        var items = request.items().stream()
                .map(item -> new CreateOrderCommand.OrderItemCommand(
                        item.sku().trim(),
                        item.quantity(),
                        item.unitPrice().setScale(4, RoundingMode.UNNECESSARY)))
                .sorted(Comparator.comparing(CreateOrderCommand.OrderItemCommand::sku))
                .toList();
        return new CreateOrderCommand(request.customerId(), request.currency(), items);
    }

    public OrderResponse toResponse(OrderEntity order) {
        var items = order.getItems().stream()
                .map(item -> new OrderResponse.OrderItemResponse(
                        item.getSku(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getLineTotal()))
                .sorted(Comparator.comparing(OrderResponse.OrderItemResponse::sku))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getReservationFailureReason(),
                order.getCurrency(),
                order.getTotalAmount(),
                items,
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
