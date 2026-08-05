package com.orderplatform.inventory.domain.exception;

public class InsufficientOnHandQuantityException extends RuntimeException {

    public InsufficientOnHandQuantityException(String sku) {
        super("On-hand quantity cannot be lower than reserved quantity for SKU " + sku);
    }
}
