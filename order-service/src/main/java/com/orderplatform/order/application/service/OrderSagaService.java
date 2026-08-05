package com.orderplatform.order.application.service;

import com.orderplatform.order.domain.entity.OrderEntity;
import com.orderplatform.order.domain.exception.OrderNotFoundException;
import com.orderplatform.order.infrastructure.kafka.InventoryReservationResultEvent;
import com.orderplatform.order.infrastructure.kafka.InventoryReleasedEvent;
import com.orderplatform.order.infrastructure.kafka.ProcessedEventEntity;
import com.orderplatform.order.infrastructure.kafka.ProcessedEventRepository;
import com.orderplatform.order.infrastructure.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderSagaService {

    private static final String CONSUMER_NAME = "inventory-reservation-result-v1";
    private static final String RELEASE_CONSUMER_NAME = "inventory-released-v1";

    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;

    public OrderSagaService(
            OrderRepository orderRepository,
            ProcessedEventRepository processedEventRepository) {
        this.orderRepository = orderRepository;
        this.processedEventRepository = processedEventRepository;
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
            case "RESERVED" -> order.confirm();
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
}
