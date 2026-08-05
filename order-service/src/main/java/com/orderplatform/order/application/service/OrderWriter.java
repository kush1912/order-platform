package com.orderplatform.order.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.order.domain.entity.OrderEntity;
import com.orderplatform.order.infrastructure.kafka.OrderPlacedEvent;
import com.orderplatform.order.infrastructure.kafka.OutboxEventEntity;
import com.orderplatform.order.infrastructure.kafka.OutboxEventRepository;
import com.orderplatform.order.infrastructure.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrderWriter {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OrderWriter(
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderEntity create(
            CreateOrderCommand command,
            String idempotencyKey,
            String requestHash) {
        List<OrderEntity.OrderItem> items = command.items().stream()
                .map(item -> new OrderEntity.OrderItem(
                        item.sku(),
                        item.quantity(),
                        item.unitPrice()))
                .toList();
        OrderEntity order = OrderEntity.create(
                command.customerId(),
                command.currency(),
                idempotencyKey,
                requestHash,
                items);
        OrderEntity savedOrder = orderRepository.saveAndFlush(order);
        UUID eventId = UUID.randomUUID();
        OrderPlacedEvent event = new OrderPlacedEvent(
                eventId,
                "OrderPlaced",
                savedOrder.getCreatedAt(),
                savedOrder.getId(),
                savedOrder.getCustomerId(),
                savedOrder.getCurrency(),
                savedOrder.getItems().stream()
                        .map(item -> new OrderPlacedEvent.Item(
                                item.getSku(),
                                item.getQuantity()))
                        .toList());
        outboxEventRepository.save(OutboxEventEntity.create(
                eventId,
                savedOrder.getId(),
                "orders.placed.v1",
                savedOrder.getId().toString(),
                event.eventType(),
                serialize(event)));
        return savedOrder;
    }

    private String serialize(OrderPlacedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize OrderPlaced event", exception);
        }
    }
}
