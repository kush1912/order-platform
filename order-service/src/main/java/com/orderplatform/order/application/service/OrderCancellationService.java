package com.orderplatform.order.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.order.domain.entity.OrderEntity;
import com.orderplatform.order.domain.entity.OrderStatus;
import com.orderplatform.order.domain.exception.IdempotencyConflictException;
import com.orderplatform.order.domain.exception.InvalidOrderStateException;
import com.orderplatform.order.domain.exception.OrderNotFoundException;
import com.orderplatform.order.infrastructure.kafka.InventoryReleaseRequestedEvent;
import com.orderplatform.order.infrastructure.kafka.OutboxEventEntity;
import com.orderplatform.order.infrastructure.kafka.OutboxEventRepository;
import com.orderplatform.order.infrastructure.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class OrderCancellationService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OrderCancellationService(
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CancelOrderResult cancel(UUID orderId, String idempotencyKey) {
        OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() == OrderStatus.CANCELLATION_PENDING
                || order.getStatus() == OrderStatus.CANCELLED) {
            if (idempotencyKey.equals(order.getCancellationIdempotencyKey())) {
                return new CancelOrderResult(order, true);
            }
            throw new IdempotencyConflictException();
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(
                    orderId,
                    order.getStatus(),
                    "be cancelled");
        }

        order.requestCancellation(idempotencyKey);
        UUID eventId = UUID.randomUUID();
        InventoryReleaseRequestedEvent event = new InventoryReleaseRequestedEvent(
                eventId,
                "InventoryReleaseRequested",
                OffsetDateTime.now(ZoneOffset.UTC),
                orderId);
        outboxEventRepository.save(OutboxEventEntity.create(
                eventId,
                orderId,
                "inventory.release-requested.v1",
                orderId.toString(),
                event.eventType(),
                serialize(event)));
        return new CancelOrderResult(order, false);
    }

    private String serialize(InventoryReleaseRequestedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not serialize inventory release request",
                    exception);
        }
    }
}
