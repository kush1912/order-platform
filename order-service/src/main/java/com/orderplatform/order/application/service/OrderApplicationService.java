package com.orderplatform.order.application.service;

import com.orderplatform.order.domain.entity.OrderEntity;
import com.orderplatform.order.domain.exception.IdempotencyConflictException;
import com.orderplatform.order.domain.exception.OrderNotFoundException;
import com.orderplatform.order.infrastructure.grpc.InventoryAvailabilityClient;
import com.orderplatform.order.infrastructure.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderApplicationService {

    private final OrderRepository orderRepository;
    private final OrderWriter orderWriter;
    private final OrderRequestHasher requestHasher;
    private final InventoryAvailabilityClient inventoryAvailabilityClient;
    private final MeterRegistry meterRegistry;

    public OrderApplicationService(
            OrderRepository orderRepository,
            OrderWriter orderWriter,
            OrderRequestHasher requestHasher,
            InventoryAvailabilityClient inventoryAvailabilityClient,
            MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.orderWriter = orderWriter;
        this.requestHasher = requestHasher;
        this.inventoryAvailabilityClient = inventoryAvailabilityClient;
        this.meterRegistry = meterRegistry;
    }

    public CreateOrderResult create(CreateOrderCommand command, String idempotencyKey) {
        String requestHash = requestHasher.hash(command);
        OrderEntity existing = orderRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            return replay(existing, requestHash);
        }

        inventoryAvailabilityClient.ensureAvailable(command.items());

        try {
            OrderEntity created = orderWriter.create(command, idempotencyKey, requestHash);
            meterRegistry.counter("order.creation", "outcome", "created").increment();
            return new CreateOrderResult(created, false);
        } catch (DataIntegrityViolationException exception) {
            OrderEntity concurrentWinner = orderRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> exception);
            return replay(concurrentWinner, requestHash);
        }
    }

    @Transactional(readOnly = true)
    public OrderEntity get(UUID orderId) {
        return orderRepository.findDetailedById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private CreateOrderResult replay(OrderEntity existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            meterRegistry.counter("order.creation", "outcome", "idempotency_conflict").increment();
            throw new IdempotencyConflictException();
        }
        meterRegistry.counter("order.creation", "outcome", "replayed").increment();
        return new CreateOrderResult(existing, true);
    }
}
