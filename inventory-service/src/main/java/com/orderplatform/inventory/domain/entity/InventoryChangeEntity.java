package com.orderplatform.inventory.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "inventory_changes")
public class InventoryChangeEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(name = "previous_on_hand_quantity")
    private Long previousOnHandQuantity;

    @Column(name = "new_on_hand_quantity", nullable = false)
    private long newOnHandQuantity;

    @Column(nullable = false, length = 128)
    private String reason;

    @Column(name = "source_reference", nullable = false, length = 128)
    private String sourceReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected InventoryChangeEntity() {
    }

    public static InventoryChangeEntity create(
            String sku,
            Long previousOnHandQuantity,
            long newOnHandQuantity,
            String reason,
            String sourceReference) {
        InventoryChangeEntity change = new InventoryChangeEntity();
        change.id = UUID.randomUUID();
        change.sku = sku;
        change.previousOnHandQuantity = previousOnHandQuantity;
        change.newOnHandQuantity = newOnHandQuantity;
        change.reason = reason;
        change.sourceReference = sourceReference;
        change.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        return change;
    }
}
