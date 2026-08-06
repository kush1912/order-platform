package com.orderplatform.order.domain.exception;

/**
 * Thrown when the inventory availability check could not be completed (for
 * example the inventory gRPC service is unreachable). Maps to HTTP 503.
 */
public class InventoryUnavailableException extends RuntimeException {

    public InventoryUnavailableException(Throwable cause) {
        super("Inventory availability check is currently unavailable", cause);
    }
}
