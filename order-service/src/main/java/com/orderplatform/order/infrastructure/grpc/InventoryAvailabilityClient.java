package com.orderplatform.order.infrastructure.grpc;

import com.orderplatform.inventory.grpc.v1.CheckAvailabilityRequest;
import com.orderplatform.inventory.grpc.v1.CheckAvailabilityResponse;
import com.orderplatform.inventory.grpc.v1.InventoryAvailabilityGrpc;
import com.orderplatform.inventory.grpc.v1.RequestedItem;
import com.orderplatform.inventory.grpc.v1.Shortfall;
import com.orderplatform.inventory.grpc.v1.ShortfallReason;
import com.orderplatform.order.application.service.CreateOrderCommand;
import com.orderplatform.order.domain.exception.InsufficientInventoryException;
import com.orderplatform.order.domain.exception.InventoryUnavailableException;
import com.orderplatform.order.domain.exception.UnknownSkuException;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Synchronous, fail-fast availability check performed during order creation.
 * A shortfall aborts the request before any order row is written; unknown SKUs
 * take precedence (422) over insufficient stock (409).
 */
@Component
public class InventoryAvailabilityClient {

    private final InventoryAvailabilityGrpc.InventoryAvailabilityBlockingStub availabilityStub;

    public InventoryAvailabilityClient(
            @GrpcClient("inventory")
            InventoryAvailabilityGrpc.InventoryAvailabilityBlockingStub availabilityStub) {
        this.availabilityStub = availabilityStub;
    }

    public void ensureAvailable(List<CreateOrderCommand.OrderItemCommand> items) {
        CheckAvailabilityRequest.Builder request = CheckAvailabilityRequest.newBuilder();
        items.forEach(item -> request.addItems(RequestedItem.newBuilder()
                .setSku(item.sku())
                .setQuantity(item.quantity())
                .build()));

        CheckAvailabilityResponse response;
        try {
            response = availabilityStub.checkAvailability(request.build());
        } catch (StatusRuntimeException exception) {
            throw new InventoryUnavailableException(exception);
        }

        if (response.getAvailable()) {
            return;
        }

        List<String> unknownSkus = response.getShortfallsList().stream()
                .filter(shortfall -> shortfall.getReason() == ShortfallReason.SKU_NOT_FOUND)
                .map(Shortfall::getSku)
                .toList();
        if (!unknownSkus.isEmpty()) {
            throw new UnknownSkuException(unknownSkus);
        }

        List<String> insufficientSkus = response.getShortfallsList().stream()
                .map(Shortfall::getSku)
                .toList();
        throw new InsufficientInventoryException(insufficientSkus);
    }
}
