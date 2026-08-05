package com.orderplatform.inventory.api.controller;

import com.orderplatform.inventory.api.dto.request.UpdateInventoryRequest;
import com.orderplatform.inventory.api.dto.response.InventoryResponse;
import com.orderplatform.inventory.api.mapper.InventoryMapper;
import com.orderplatform.inventory.application.service.InventoryApplicationService;
import com.orderplatform.inventory.domain.entity.InventoryItemEntity;
import com.orderplatform.inventory.domain.exception.InventoryPreconditionException;
import com.orderplatform.inventory.shared.utility.SkuNormalizer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.regex.Matcher;

@Validated
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private static final java.util.regex.Pattern ETAG =
            java.util.regex.Pattern.compile("^\"(\\d+)\"$");

    private final InventoryApplicationService inventoryService;
    private final InventoryMapper inventoryMapper;
    private final SkuNormalizer skuNormalizer;

    public InventoryController(
            InventoryApplicationService inventoryService,
            InventoryMapper inventoryMapper,
            SkuNormalizer skuNormalizer) {
        this.inventoryService = inventoryService;
        this.inventoryMapper = inventoryMapper;
        this.skuNormalizer = skuNormalizer;
    }

    @PutMapping("/{sku}")
    public ResponseEntity<InventoryResponse> synchronize(
            @PathVariable
            @Size(max = 64)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
            String sku,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
            @Valid @RequestBody UpdateInventoryRequest request,
            UriComponentsBuilder uriBuilder) {
        String normalizedSku = skuNormalizer.normalize(sku);
        validateExclusivePreconditions(ifMatch, ifNoneMatch);

        if ("*".equals(ifNoneMatch)) {
            InventoryItemEntity created = inventoryService.create(
                    normalizedSku,
                    request.onHandQuantity(),
                    request.reason(),
                    request.sourceReference());
            URI location = uriBuilder.path("/api/v1/inventory/{sku}")
                    .buildAndExpand(normalizedSku)
                    .toUri();
            return ResponseEntity.created(location)
                    .eTag(etag(created.getVersion()))
                    .body(inventoryMapper.toResponse(created));
        }

        if (ifMatch == null) {
            throw new InventoryPreconditionException(
                    "If-Match or If-None-Match header is required",
                    true);
        }

        InventoryItemEntity updated = inventoryService.update(
                normalizedSku,
                parseEtag(ifMatch),
                request.onHandQuantity(),
                request.reason(),
                request.sourceReference());
        return ResponseEntity.ok()
                .eTag(etag(updated.getVersion()))
                .body(inventoryMapper.toResponse(updated));
    }

    @GetMapping("/{sku}")
    public ResponseEntity<InventoryResponse> get(
            @PathVariable
            @Size(max = 64)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
            String sku) {
        InventoryItemEntity item = inventoryService.get(skuNormalizer.normalize(sku));
        return ResponseEntity.ok()
                .eTag(etag(item.getVersion()))
                .body(inventoryMapper.toResponse(item));
    }

    private void validateExclusivePreconditions(String ifMatch, String ifNoneMatch) {
        if (ifMatch != null && ifNoneMatch != null) {
            throw new InventoryPreconditionException(
                    "If-Match and If-None-Match cannot be used together",
                    false);
        }
        if (ifNoneMatch != null && !"*".equals(ifNoneMatch)) {
            throw new InventoryPreconditionException(
                    "If-None-Match must be '*' when creating inventory",
                    false);
        }
    }

    private long parseEtag(String value) {
        Matcher matcher = ETAG.matcher(value);
        if (!matcher.matches()) {
            throw new InventoryPreconditionException(
                    "If-Match must contain a quoted numeric version",
                    false);
        }
        return Long.parseLong(matcher.group(1));
    }

    private String etag(long version) {
        return "\"" + version + "\"";
    }
}
