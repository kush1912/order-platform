package com.orderplatform.inventory.application.service;

import com.orderplatform.inventory.domain.entity.InventoryChangeEntity;
import com.orderplatform.inventory.domain.entity.InventoryItemEntity;
import com.orderplatform.inventory.domain.exception.InsufficientOnHandQuantityException;
import com.orderplatform.inventory.domain.exception.InventoryNotFoundException;
import com.orderplatform.inventory.domain.exception.InventoryVersionConflictException;
import com.orderplatform.inventory.infrastructure.repository.InventoryChangeRepository;
import com.orderplatform.inventory.infrastructure.repository.InventoryItemRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryApplicationService {

    private final InventoryItemRepository itemRepository;
    private final InventoryChangeRepository changeRepository;
    private final MeterRegistry meterRegistry;

    public InventoryApplicationService(
            InventoryItemRepository itemRepository,
            InventoryChangeRepository changeRepository,
            MeterRegistry meterRegistry) {
        this.itemRepository = itemRepository;
        this.changeRepository = changeRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public InventoryItemEntity create(
            String sku,
            long onHandQuantity,
            String reason,
            String sourceReference) {
        if (itemRepository.existsById(sku)) {
            throw new InventoryVersionConflictException(sku);
        }

        try {
            InventoryItemEntity item = itemRepository.saveAndFlush(
                    InventoryItemEntity.create(sku, onHandQuantity));
            changeRepository.save(InventoryChangeEntity.create(
                    sku,
                    null,
                    onHandQuantity,
                    reason,
                    sourceReference));
            meterRegistry.counter("inventory.synchronization", "outcome", "created").increment();
            return item;
        } catch (DataIntegrityViolationException exception) {
            throw new InventoryVersionConflictException(sku);
        }
    }

    @Transactional
    public InventoryItemEntity update(
            String sku,
            long expectedVersion,
            long onHandQuantity,
            String reason,
            String sourceReference) {
        InventoryItemEntity item = itemRepository.findById(sku)
                .orElseThrow(() -> new InventoryNotFoundException(sku));
        if (item.getVersion() != expectedVersion) {
            meterRegistry.counter("inventory.synchronization", "outcome", "version_conflict")
                    .increment();
            throw new InventoryVersionConflictException(sku);
        }

        long previousQuantity = item.getOnHandQuantity();
        try {
            item.synchronizeOnHand(onHandQuantity);
        } catch (IllegalArgumentException exception) {
            throw new InsufficientOnHandQuantityException(sku);
        }

        try {
            InventoryItemEntity updated = itemRepository.saveAndFlush(item);
            changeRepository.save(InventoryChangeEntity.create(
                    sku,
                    previousQuantity,
                    onHandQuantity,
                    reason,
                    sourceReference));
            meterRegistry.counter("inventory.synchronization", "outcome", "updated").increment();
            return updated;
        } catch (ObjectOptimisticLockingFailureException exception) {
            meterRegistry.counter("inventory.synchronization", "outcome", "version_conflict")
                    .increment();
            throw new InventoryVersionConflictException(sku);
        }
    }

    @Transactional(readOnly = true)
    public InventoryItemEntity get(String sku) {
        return itemRepository.findById(sku)
                .orElseThrow(() -> new InventoryNotFoundException(sku));
    }
}
