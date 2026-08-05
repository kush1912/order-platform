package com.orderplatform.inventory.infrastructure.repository;

import com.orderplatform.inventory.domain.entity.InventoryChangeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InventoryChangeRepository extends JpaRepository<InventoryChangeEntity, UUID> {
}
