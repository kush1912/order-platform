package com.orderplatform.order.domain.exception;

import java.util.List;

/**
 * Thrown when the synchronous availability check reports one or more requested
 * SKUs cannot be fulfilled from current stock. Maps to HTTP 409 Conflict.
 */
public class InsufficientInventoryException extends RuntimeException {

    private final List<String> skus;

    public InsufficientInventoryException(List<String> skus) {
        super("Insufficient inventory for SKUs: " + String.join(", ", skus));
        this.skus = List.copyOf(skus);
    }

    public List<String> getSkus() {
        return skus;
    }
}
