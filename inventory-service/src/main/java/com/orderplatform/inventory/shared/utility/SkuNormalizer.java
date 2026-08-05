package com.orderplatform.inventory.shared.utility;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class SkuNormalizer {

    public String normalize(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }
}
