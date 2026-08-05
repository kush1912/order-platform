package com.orderplatform.inventory.domain.exception;

public class InventoryPreconditionException extends RuntimeException {

    private final boolean missing;

    public InventoryPreconditionException(String message, boolean missing) {
        super(message);
        this.missing = missing;
    }

    public boolean missing() {
        return missing;
    }
}
