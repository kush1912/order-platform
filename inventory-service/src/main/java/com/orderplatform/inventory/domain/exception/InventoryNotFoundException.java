package com.orderplatform.inventory.domain.exception;

public class InventoryNotFoundException extends RuntimeException {

    public InventoryNotFoundException(String sku) {
        super("Inventory item not found: " + sku);
    }
}
