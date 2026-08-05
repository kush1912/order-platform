package com.orderplatform.inventory.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.inventory.domain.entity.InventoryItemEntity;
import com.orderplatform.inventory.domain.entity.InventoryReservationEntity;
import com.orderplatform.inventory.infrastructure.kafka.InventoryReservationResultEvent;
import com.orderplatform.inventory.infrastructure.kafka.InventoryReleasedEvent;
import com.orderplatform.inventory.infrastructure.kafka.InventoryReleaseRequestedEvent;
import com.orderplatform.inventory.infrastructure.kafka.OrderPlacedEvent;
import com.orderplatform.inventory.infrastructure.kafka.OutboxEventEntity;
import com.orderplatform.inventory.infrastructure.kafka.OutboxEventRepository;
import com.orderplatform.inventory.infrastructure.kafka.ProcessedEventEntity;
import com.orderplatform.inventory.infrastructure.kafka.ProcessedEventRepository;
import com.orderplatform.inventory.infrastructure.repository.InventoryItemRepository;
import com.orderplatform.inventory.infrastructure.repository.InventoryReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryReservationService {

    private static final String CONSUMER_NAME = "order-placed-reservation-v1";
    private static final String RELEASE_CONSUMER_NAME = "inventory-release-request-v1";

    private final InventoryItemRepository itemRepository;
    private final InventoryReservationRepository reservationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public InventoryReservationService(
            InventoryItemRepository itemRepository,
            InventoryReservationRepository reservationRepository,
            ProcessedEventRepository processedEventRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.itemRepository = itemRepository;
        this.reservationRepository = reservationRepository;
        this.processedEventRepository = processedEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void reserve(OrderPlacedEvent event) {
        ProcessedEventEntity.Key inboxKey =
                new ProcessedEventEntity.Key(event.eventId(), CONSUMER_NAME);
        if (processedEventRepository.existsById(inboxKey)) {
            return;
        }

        Map<String, Long> requested = aggregateItems(event.items());
        List<InventoryItemEntity> lockedItems =
                itemRepository.lockAllBySku(requested.keySet());
        Map<String, InventoryItemEntity> inventoryBySku = lockedItems.stream()
                .collect(Collectors.toMap(
                        InventoryItemEntity::getSku,
                        Function.identity()));

        String rejectionReason = findRejectionReason(requested, inventoryBySku);
        if (rejectionReason == null) {
            requested.forEach((sku, quantity) ->
                    inventoryBySku.get(sku).reserve(quantity));
            reservationRepository.save(InventoryReservationEntity.reserved(
                    event.orderId(),
                    requested.entrySet().stream()
                            .map(entry -> new InventoryReservationEntity.ReservationItem(
                                    entry.getKey(),
                                    entry.getValue()))
                            .toList()));
            enqueueResult(event.orderId(), "RESERVED", null);
        } else {
            reservationRepository.save(
                    InventoryReservationEntity.rejected(event.orderId(), rejectionReason));
            enqueueResult(event.orderId(), "REJECTED", rejectionReason);
        }

        processedEventRepository.save(
                ProcessedEventEntity.create(event.eventId(), CONSUMER_NAME));
    }

    @Transactional
    public void release(InventoryReleaseRequestedEvent event) {
        ProcessedEventEntity.Key inboxKey =
                new ProcessedEventEntity.Key(event.eventId(), RELEASE_CONSUMER_NAME);
        if (processedEventRepository.existsById(inboxKey)) {
            return;
        }

        InventoryReservationEntity reservation =
                reservationRepository.findDetailedByOrderIdForUpdate(event.orderId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Inventory reservation not found for order " + event.orderId()));
        if (reservation.getStatus() == InventoryReservationEntity.Status.RESERVED) {
            Map<String, Long> reservedBySku = reservation.getItems().stream()
                    .sorted((left, right) -> left.getSku().compareTo(right.getSku()))
                    .collect(Collectors.toMap(
                            item -> item.getSku(),
                            item -> item.getQuantity(),
                            Math::addExact,
                            LinkedHashMap::new));
            Map<String, InventoryItemEntity> inventoryBySku =
                    itemRepository.lockAllBySku(reservedBySku.keySet()).stream()
                            .collect(Collectors.toMap(
                                    InventoryItemEntity::getSku,
                                    Function.identity()));
            if (inventoryBySku.size() != reservedBySku.size()) {
                throw new IllegalStateException(
                        "Reserved inventory item is missing for order " + event.orderId());
            }
            reservedBySku.forEach((sku, quantity) ->
                    inventoryBySku.get(sku).release(quantity));
            reservation.markReleased();
        } else if (reservation.getStatus() != InventoryReservationEntity.Status.RELEASED) {
            throw new IllegalStateException(
                    "Rejected reservation cannot be released for order " + event.orderId());
        }

        enqueueReleased(event.orderId());
        processedEventRepository.save(
                ProcessedEventEntity.create(event.eventId(), RELEASE_CONSUMER_NAME));
    }

    private Map<String, Long> aggregateItems(List<OrderPlacedEvent.Item> items) {
        Map<String, Long> aggregated = new LinkedHashMap<>();
        items.stream()
                .sorted((left, right) -> left.sku().compareTo(right.sku()))
                .forEach(item -> aggregated.merge(item.sku(), item.quantity(), Math::addExact));
        return aggregated;
    }

    private String findRejectionReason(
            Map<String, Long> requested,
            Map<String, InventoryItemEntity> inventoryBySku) {
        for (Map.Entry<String, Long> entry : requested.entrySet()) {
            InventoryItemEntity inventory = inventoryBySku.get(entry.getKey());
            if (inventory == null) {
                return "SKU_NOT_FOUND:" + entry.getKey();
            }
            if (inventory.getAvailableQuantity() < entry.getValue()) {
                return "INSUFFICIENT_INVENTORY:" + entry.getKey();
            }
        }
        return null;
    }

    private void enqueueResult(UUID orderId, String status, String reason) {
        UUID eventId = UUID.randomUUID();
        InventoryReservationResultEvent result = new InventoryReservationResultEvent(
                eventId,
                status.equals("RESERVED") ? "InventoryReserved" : "InventoryRejected",
                OffsetDateTime.now(ZoneOffset.UTC),
                orderId,
                status,
                reason);
        outboxEventRepository.save(OutboxEventEntity.create(
                eventId,
                orderId,
                "inventory.reservation-results.v1",
                orderId.toString(),
                result.eventType(),
                serialize(result)));
    }

    private void enqueueReleased(UUID orderId) {
        UUID eventId = UUID.randomUUID();
        InventoryReleasedEvent result = new InventoryReleasedEvent(
                eventId,
                "InventoryReleased",
                OffsetDateTime.now(ZoneOffset.UTC),
                orderId);
        outboxEventRepository.save(OutboxEventEntity.create(
                eventId,
                orderId,
                "inventory.released.v1",
                orderId.toString(),
                result.eventType(),
                serialize(result)));
    }

    private String serialize(InventoryReservationResultEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not serialize inventory reservation result",
                    exception);
        }
    }

    private String serialize(InventoryReleasedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not serialize inventory released event",
                    exception);
        }
    }
}
