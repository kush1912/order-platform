package com.orderplatform.order.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.order.application.service.OrderSagaService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryReleasedListener {

    private final ObjectMapper objectMapper;
    private final OrderSagaService orderSagaService;

    public InventoryReleasedListener(
            ObjectMapper objectMapper,
            OrderSagaService orderSagaService) {
        this.objectMapper = objectMapper;
        this.orderSagaService = orderSagaService;
    }

    @KafkaListener(
            topics = "inventory.released.v1",
            groupId = "order-inventory-released-v1")
    public void onInventoryReleased(String payload) throws JsonProcessingException {
        orderSagaService.applyInventoryReleased(
                objectMapper.readValue(payload, InventoryReleasedEvent.class));
    }
}
