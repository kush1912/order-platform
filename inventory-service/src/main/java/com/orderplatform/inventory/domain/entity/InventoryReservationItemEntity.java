package com.orderplatform.inventory.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "inventory_reservation_items")
public class InventoryReservationItemEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private InventoryReservationEntity reservation;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false)
    private long quantity;

    protected InventoryReservationItemEntity() {
    }

    static InventoryReservationItemEntity create(
            InventoryReservationEntity reservation,
            String sku,
            long quantity) {
        InventoryReservationItemEntity item = new InventoryReservationItemEntity();
        item.id = UUID.randomUUID();
        item.reservation = reservation;
        item.sku = sku;
        item.quantity = quantity;
        return item;
    }

    public String getSku() {
        return sku;
    }

    public long getQuantity() {
        return quantity;
    }
}
