package com.orderplatform.order.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(name = "reservation_failure_reason", length = 256)
    private String reservationFailureReason;

    @Column(name = "eta_days")
    private Integer etaDays;

    @Column(name = "cancellation_idempotency_key", length = 128)
    private String cancellationIdempotencyKey;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemEntity> items = new ArrayList<>();

    protected OrderEntity() {
    }

    public static OrderEntity create(
            UUID customerId,
            String currency,
            String idempotencyKey,
            String requestHash,
            List<OrderItem> items) {
        OrderEntity order = new OrderEntity();
        order.id = UUID.randomUUID();
        order.customerId = customerId;
        order.status = OrderStatus.PENDING;
        order.currency = currency;
        order.idempotencyKey = idempotencyKey;
        order.requestHash = requestHash;
        order.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        order.updatedAt = order.createdAt;

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            OrderItemEntity entity = OrderItemEntity.create(
                    order,
                    item.sku(),
                    item.quantity(),
                    item.unitPrice());
            order.items.add(entity);
            total = total.add(entity.getLineTotal());
        }
        order.totalAmount = total;
        return order;
    }

    public UUID getId() {
        return id;
    }

    public void confirm() {
        requireStatus(OrderStatus.PENDING);
        status = OrderStatus.CONFIRMED;
        reservationFailureReason = null;
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void reject(String reason) {
        requireStatus(OrderStatus.PENDING);
        status = OrderStatus.REJECTED;
        reservationFailureReason = reason;
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void applyEta(int days) {
        requireStatus(OrderStatus.CONFIRMED);
        etaDays = days;
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void requestCancellation(String idempotencyKey) {
        requireStatus(OrderStatus.CONFIRMED);
        status = OrderStatus.CANCELLATION_PENDING;
        cancellationIdempotencyKey = idempotencyKey;
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void completeCancellation() {
        requireStatus(OrderStatus.CANCELLATION_PENDING);
        status = OrderStatus.CANCELLED;
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    private void requireStatus(OrderStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Order " + id + " must be " + expected + " but is " + status);
        }
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getReservationFailureReason() {
        return reservationFailureReason;
    }

    public Integer getEtaDays() {
        return etaDays;
    }

    public String getCancellationIdempotencyKey() {
        return cancellationIdempotencyKey;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItemEntity> getItems() {
        return Collections.unmodifiableList(items);
    }

    public record OrderItem(
            String sku,
            int quantity,
            BigDecimal unitPrice) {
    }
}
