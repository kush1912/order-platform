package com.orderplatform.inventory.api.mapper;

import com.orderplatform.inventory.api.dto.response.InventoryResponse;
import com.orderplatform.inventory.domain.entity.InventoryItemEntity;
import org.springframework.stereotype.Component;

@Component
public class InventoryMapper {

    public InventoryResponse toResponse(InventoryItemEntity item) {
        return new InventoryResponse(
                item.getSku(),
                item.getOnHandQuantity(),
                item.getReservedQuantity(),
                item.getAvailableQuantity(),
                item.getVersion(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
