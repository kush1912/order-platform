package com.orderplatform.order.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.order.application.service.OrderSagaService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryReservationResultListener {

    private final ObjectMapper objectMapper;
    private final OrderSagaService orderSagaService;

    public InventoryReservationResultListener(
            ObjectMapper objectMapper,
            OrderSagaService orderSagaService) {
        this.objectMapper = objectMapper;
        this.orderSagaService = orderSagaService;
    }

    @KafkaListener(
            topics = "inventory.reservation-results.v1",
            groupId = "order-inventory-reservation-v1")
    public void onReservationResult(String payload) throws JsonProcessingException {
        orderSagaService.applyReservationResult(
                objectMapper.readValue(payload, InventoryReservationResultEvent.class));
    }
}
