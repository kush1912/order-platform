package com.orderplatform.inventory.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateInventoryRequest(
        @Min(0) @Max(1_000_000_000) long onHandQuantity,
        @NotBlank @Size(max = 128) String reason,
        @NotBlank @Size(max = 128) String sourceReference) {
}
