package com.orderplatform.order.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.order.domain.entity.OrderEntity;
import com.orderplatform.order.domain.exception.OrderNotFoundException;
import com.orderplatform.order.infrastructure.kafka.InventoryReservationResultEvent;
import com.orderplatform.order.infrastructure.kafka.InventoryReleasedEvent;
import com.orderplatform.order.infrastructure.kafka.NotificationRequestedEvent;
import com.orderplatform.order.infrastructure.kafka.OutboxEventEntity;
import com.orderplatform.order.infrastructure.kafka.OutboxEventRepository;
import com.orderplatform.order.infrastructure.kafka.ProcessedEventEntity;
import com.orderplatform.order.infrastructure.kafka.ProcessedEventRepository;
import com.orderplatform.order.infrastructure.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class OrderSagaService {

    private static final String CONSUMER_NAME = "inventory-reservation-result-v1";
    private static final String RELEASE_CONSUMER_NAME = "inventory-released-v1";
    private static final String NOTIFICATIONS_TOPIC = "notifications.requested.v1";

    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final EtaCalculator etaCalculator;

    public OrderSagaService(
            OrderRepository orderRepository,
            ProcessedEventRepository processedEventRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            EtaCalculator etaCalculator) {
        this.orderRepository = orderRepository;
        this.processedEventRepository = processedEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.etaCalculator = etaCalculator;
    }

    @Transactional
    public void applyReservationResult(InventoryReservationResultEvent event) {
        ProcessedEventEntity.Key inboxKey =
                new ProcessedEventEntity.Key(event.eventId(), CONSUMER_NAME);
        if (processedEventRepository.existsById(inboxKey)) {
            return;
        }

        OrderEntity order = orderRepository.findByIdForUpdate(event.orderId())
                .orElseThrow(() -> new OrderNotFoundException(event.orderId()));
        switch (event.status()) {
            case "RESERVED" -> {
                order.confirm();
                int etaDays = awaitReservedAndEta(order.getId());
                order.applyEta(etaDays);
                enqueueAcceptedNotification(order, etaDays);
            }
            case "REJECTED" -> order.reject(event.reason());
            default -> throw new IllegalArgumentException(
                    "Unsupported inventory reservation status: " + event.status());
        }
        processedEventRepository.save(
                ProcessedEventEntity.create(event.eventId(), CONSUMER_NAME));
    }

    @Transactional
    public void applyInventoryReleased(InventoryReleasedEvent event) {
        ProcessedEventEntity.Key inboxKey =
                new ProcessedEventEntity.Key(event.eventId(), RELEASE_CONSUMER_NAME);
        if (processedEventRepository.existsById(inboxKey)) {
            return;
        }
        OrderEntity order = orderRepository.findByIdForUpdate(event.orderId())
                .orElseThrow(() -> new OrderNotFoundException(event.orderId()));
        order.completeCancellation();
        processedEventRepository.save(
                ProcessedEventEntity.create(event.eventId(), RELEASE_CONSUMER_NAME));
    }

    /**
     * The order is accepted once inventory is reserved. In parallel with the
     * reservation we compute an estimated-delivery ETA, then combine both
     * futures: only once BOTH the reservation and the ETA are ready do we return
     * the ETA used to build the async notification. The ETA future runs on a
     * dedicated executor; the join keeps ETA persistence + notification enqueue
     * inside this same DB transaction (transactional outbox).
     */
    private int awaitReservedAndEta(UUID orderId) {
        CompletableFuture<UUID> reservedFuture = CompletableFuture.completedFuture(orderId);
        CompletableFuture<Integer> etaFuture = etaCalculator.calculateAsync();
        return reservedFuture.thenCombine(etaFuture, (id, eta) -> eta).join();
    }

    private void enqueueAcceptedNotification(OrderEntity order, int etaDays) {
        UUID eventId = UUID.randomUUID();
        NotificationRequestedEvent notification = new NotificationRequestedEvent(
                eventId,
                order.getId().toString(),
                "EMAIL",
                "customer+" + order.getCustomerId() + "@orders.local",
                null,
                "Your order has been confirmed",
                "Order " + order.getId() + " has been accepted. Estimated delivery in "
                        + etaDays + " days.",
                null);
        outboxEventRepository.save(OutboxEventEntity.create(
                eventId,
                order.getId(),
                NOTIFICATIONS_TOPIC,
                order.getId().toString(),
                "NotificationRequested",
                serialize(notification)));
    }

    private String serialize(NotificationRequestedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not serialize NotificationRequested event", exception);
        }
    }
}
