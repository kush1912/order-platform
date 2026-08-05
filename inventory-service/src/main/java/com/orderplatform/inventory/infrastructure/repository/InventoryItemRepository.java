package com.orderplatform.inventory.infrastructure.repository;

import com.orderplatform.inventory.domain.entity.InventoryItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;

public interface InventoryItemRepository extends JpaRepository<InventoryItemEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select item
            from InventoryItemEntity item
            where item.sku in :skus
            order by item.sku
            """)
    List<InventoryItemEntity> lockAllBySku(@Param("skus") Collection<String> skus);
}
