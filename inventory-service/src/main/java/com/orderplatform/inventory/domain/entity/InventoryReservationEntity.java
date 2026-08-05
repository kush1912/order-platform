package com.orderplatform.inventory.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inventory_reservations")
public class InventoryReservationEntity {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status;

    @Column(name = "failure_reason", length = 256)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryReservationItemEntity> items = new ArrayList<>();

    protected InventoryReservationEntity() {
    }

    public static InventoryReservationEntity reserved(
            UUID orderId,
            List<ReservationItem> items) {
        InventoryReservationEntity reservation = create(orderId, Status.RESERVED, null);
        for (ReservationItem item : items) {
            reservation.items.add(InventoryReservationItemEntity.create(
                    reservation,
                    item.sku(),
                    item.quantity()));
        }
        return reservation;
    }

    public static InventoryReservationEntity rejected(UUID orderId, String reason) {
        return create(orderId, Status.REJECTED, reason);
    }

    private static InventoryReservationEntity create(
            UUID orderId,
            Status status,
            String failureReason) {
        InventoryReservationEntity reservation = new InventoryReservationEntity();
        reservation.orderId = orderId;
        reservation.status = status;
        reservation.failureReason = failureReason;
        reservation.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        reservation.updatedAt = reservation.createdAt;
        return reservation;
    }

    public void markReleased() {
        if (status != Status.RESERVED) {
            throw new IllegalStateException("Only a reserved order can be released");
        }
        status = Status.RELEASED;
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Status getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public List<InventoryReservationItemEntity> getItems() {
        return Collections.unmodifiableList(items);
    }

    public enum Status {
        RESERVED,
        REJECTED,
        RELEASED
    }

    public record ReservationItem(String sku, long quantity) {
    }
}
