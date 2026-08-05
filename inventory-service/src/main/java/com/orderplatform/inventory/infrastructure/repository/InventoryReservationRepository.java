package com.orderplatform.inventory.infrastructure.repository;

import com.orderplatform.inventory.domain.entity.InventoryReservationEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface InventoryReservationRepository
        extends JpaRepository<InventoryReservationEntity, UUID> {

    @EntityGraph(attributePaths = "items")
    Optional<InventoryReservationEntity> findDetailedByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "items")
    @Query("""
            select reservation
            from InventoryReservationEntity reservation
            where reservation.orderId = :orderId
            """)
    Optional<InventoryReservationEntity> findDetailedByOrderIdForUpdate(
            @Param("orderId") UUID orderId);
}
