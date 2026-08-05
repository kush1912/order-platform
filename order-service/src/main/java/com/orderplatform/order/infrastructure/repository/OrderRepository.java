package com.orderplatform.order.infrastructure.repository;

import com.orderplatform.order.domain.entity.OrderEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    @EntityGraph(attributePaths = "items")
    @Query("select order from OrderEntity order where order.id = :id")
    Optional<OrderEntity> findDetailedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = "items")
    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "items")
    @Query("select order from OrderEntity order where order.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") UUID id);
}
