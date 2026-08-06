package com.orderplatform.order.domain.exception;

import java.util.List;

/**
 * Thrown when the synchronous availability check reports one or more requested
 * SKUs do not exist in inventory. Maps to HTTP 422 Unprocessable Entity.
 */
public class UnknownSkuException extends RuntimeException {

    private final List<String> skus;

    public UnknownSkuException(List<String> skus) {
        super("Unknown SKUs: " + String.join(", ", skus));
        this.skus = List.copyOf(skus);
    }

    public List<String> getSkus() {
        return skus;
    }
}
