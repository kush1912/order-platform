package com.orderplatform.inventory.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.inventory.application.service.InventoryReservationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPlacedListener {

    private final ObjectMapper objectMapper;
    private final InventoryReservationService reservationService;

    public OrderPlacedListener(
            ObjectMapper objectMapper,
            InventoryReservationService reservationService) {
        this.objectMapper = objectMapper;
        this.reservationService = reservationService;
    }

    @KafkaListener(
            topics = "orders.placed.v1",
            groupId = "inventory-order-placed-v1")
    public void onOrderPlaced(String payload) throws JsonProcessingException {
        reservationService.reserve(
                objectMapper.readValue(payload, OrderPlacedEvent.class));
    }
}
