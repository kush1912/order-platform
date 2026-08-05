package com.orderplatform.inventory.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.inventory.application.service.InventoryReservationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryReleaseRequestedListener {

    private final ObjectMapper objectMapper;
    private final InventoryReservationService reservationService;

    public InventoryReleaseRequestedListener(
            ObjectMapper objectMapper,
            InventoryReservationService reservationService) {
        this.objectMapper = objectMapper;
        this.reservationService = reservationService;
    }

    @KafkaListener(
            topics = "inventory.release-requested.v1",
            groupId = "inventory-release-request-v1")
    public void onReleaseRequested(String payload) throws JsonProcessingException {
        reservationService.release(
                objectMapper.readValue(payload, InventoryReleaseRequestedEvent.class));
    }
}
