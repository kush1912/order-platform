package com.orderplatform.inventory.domain.exception;

public class InventoryVersionConflictException extends RuntimeException {

    public InventoryVersionConflictException(String sku) {
        super("Inventory item was modified concurrently: " + sku);
    }
}
