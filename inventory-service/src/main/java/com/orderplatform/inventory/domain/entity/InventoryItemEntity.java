package com.orderplatform.inventory.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "inventory_items")
public class InventoryItemEntity {

    @Id
    @Column(length = 64)
    private String sku;

    @Column(name = "on_hand_quantity", nullable = false)
    private long onHandQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private long reservedQuantity;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected InventoryItemEntity() {
    }

    public static InventoryItemEntity create(String sku, long onHandQuantity) {
        InventoryItemEntity item = new InventoryItemEntity();
        item.sku = sku;
        item.onHandQuantity = onHandQuantity;
        item.reservedQuantity = 0;
        item.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        item.updatedAt = item.createdAt;
        return item;
    }

    public void synchronizeOnHand(long newOnHandQuantity) {
        if (newOnHandQuantity < reservedQuantity) {
            throw new IllegalArgumentException(
                    "On-hand quantity cannot be lower than reserved quantity");
        }
        onHandQuantity = newOnHandQuantity;
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void reserve(long quantity) {
        if (quantity <= 0 || getAvailableQuantity() < quantity) {
            throw new IllegalArgumentException("Insufficient available inventory");
        }
        reservedQuantity += quantity;
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void release(long quantity) {
        if (quantity <= 0 || reservedQuantity < quantity) {
            throw new IllegalArgumentException("Cannot release more inventory than reserved");
        }
        reservedQuantity -= quantity;
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public String getSku() {
        return sku;
    }

    public long getOnHandQuantity() {
        return onHandQuantity;
    }

    public long getReservedQuantity() {
        return reservedQuantity;
    }

    public long getAvailableQuantity() {
        return onHandQuantity - reservedQuantity;
    }

    public long getVersion() {
        return version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
