package com.orderplatform.inventory.infrastructure.grpc;

import com.orderplatform.inventory.application.service.InventoryAvailabilityService;
import com.orderplatform.inventory.application.service.InventoryAvailabilityService.AvailabilityResult;
import com.orderplatform.inventory.application.service.InventoryAvailabilityService.Reason;
import com.orderplatform.inventory.grpc.v1.CheckAvailabilityRequest;
import com.orderplatform.inventory.grpc.v1.CheckAvailabilityResponse;
import com.orderplatform.inventory.grpc.v1.InventoryAvailabilityGrpc;
import com.orderplatform.inventory.grpc.v1.Shortfall;
import com.orderplatform.inventory.grpc.v1.ShortfallReason;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.LinkedHashMap;
import java.util.Map;

@GrpcService
public class InventoryAvailabilityGrpcService
        extends InventoryAvailabilityGrpc.InventoryAvailabilityImplBase {

    private final InventoryAvailabilityService availabilityService;

    public InventoryAvailabilityGrpcService(InventoryAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @Override
    public void checkAvailability(
            CheckAvailabilityRequest request,
            StreamObserver<CheckAvailabilityResponse> responseObserver) {
        Map<String, Long> requestedBySku = new LinkedHashMap<>();
        request.getItemsList().forEach(item ->
                requestedBySku.merge(item.getSku(), item.getQuantity(), Math::addExact));

        AvailabilityResult result = availabilityService.check(requestedBySku);

        CheckAvailabilityResponse.Builder response = CheckAvailabilityResponse.newBuilder()
                .setAvailable(result.available());
        result.shortfalls().forEach(shortfall -> response.addShortfalls(
                Shortfall.newBuilder()
                        .setSku(shortfall.sku())
                        .setReason(toProtoReason(shortfall.reason()))
                        .setRequested(shortfall.requested())
                        .setAvailable(shortfall.available())
                        .build()));

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    private ShortfallReason toProtoReason(Reason reason) {
        return switch (reason) {
            case INSUFFICIENT_INVENTORY -> ShortfallReason.INSUFFICIENT_INVENTORY;
            case SKU_NOT_FOUND -> ShortfallReason.SKU_NOT_FOUND;
        };
    }
}
