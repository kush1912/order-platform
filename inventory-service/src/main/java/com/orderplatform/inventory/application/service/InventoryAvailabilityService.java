package com.orderplatform.inventory.application.service;

import com.orderplatform.inventory.domain.entity.InventoryItemEntity;
import com.orderplatform.inventory.infrastructure.repository.InventoryItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only, advisory availability check backing the synchronous gRPC endpoint.
 * It never reserves stock; the authoritative reservation stays in the async
 * {@link InventoryReservationService} saga, so a passing check can still be
 * rejected later if another order wins the race.
 */
@Service
public class InventoryAvailabilityService {

    private final InventoryItemRepository itemRepository;

    public InventoryAvailabilityService(InventoryItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    public AvailabilityResult check(Map<String, Long> requestedBySku) {
        if (requestedBySku.isEmpty()) {
            return new AvailabilityResult(true, List.of());
        }

        Map<String, InventoryItemEntity> inventoryBySku =
                itemRepository.findAllById(requestedBySku.keySet()).stream()
                        .collect(Collectors.toMap(
                                InventoryItemEntity::getSku,
                                Function.identity()));

        List<Shortfall> shortfalls = new ArrayList<>();
        requestedBySku.forEach((sku, quantity) -> {
            InventoryItemEntity item = inventoryBySku.get(sku);
            if (item == null) {
                shortfalls.add(new Shortfall(sku, Reason.SKU_NOT_FOUND, quantity, 0));
            } else if (item.getAvailableQuantity() < quantity) {
                shortfalls.add(new Shortfall(
                        sku,
                        Reason.INSUFFICIENT_INVENTORY,
                        quantity,
                        item.getAvailableQuantity()));
            }
        });

        return new AvailabilityResult(shortfalls.isEmpty(), List.copyOf(shortfalls));
    }

    public enum Reason {
        INSUFFICIENT_INVENTORY,
        SKU_NOT_FOUND
    }

    public record Shortfall(String sku, Reason reason, long requested, long available) {
    }

    public record AvailabilityResult(boolean available, List<Shortfall> shortfalls) {
    }
}
