package com.orderplatform.order.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID customerId,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotEmpty @Size(max = 100) List<@Valid OrderItemRequest> items) {

    @AssertTrue(message = "items must contain unique SKUs")
    public boolean hasUniqueSkus() {
        if (items == null) {
            return true;
        }
        Set<String> skus = new HashSet<>();
        return items.stream()
                .map(OrderItemRequest::sku)
                .filter(sku -> sku != null)
                .map(String::trim)
                .allMatch(skus::add);
    }

    public record OrderItemRequest(
            @NotBlank @Size(max = 64) String sku,
            @Min(1) @Max(1000) int quantity,
            @NotNull
            @DecimalMin(value = "0.0001")
            @Digits(integer = 15, fraction = 4)
            BigDecimal unitPrice) {
    }
}
