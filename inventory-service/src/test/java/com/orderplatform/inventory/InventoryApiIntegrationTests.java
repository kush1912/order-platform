package com.orderplatform.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.inventory.infrastructure.repository.InventoryChangeRepository;
import com.orderplatform.inventory.application.service.InventoryApplicationService;
import com.orderplatform.inventory.application.service.InventoryReservationService;
import com.orderplatform.inventory.infrastructure.kafka.InventoryReleaseRequestedEvent;
import com.orderplatform.inventory.infrastructure.kafka.OrderPlacedEvent;
import com.orderplatform.inventory.infrastructure.repository.InventoryItemRepository;
import com.orderplatform.inventory.infrastructure.repository.InventoryReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "inventory.outbox.fixed-delay=1h"
})
@AutoConfigureMockMvc
class InventoryApiIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventoryChangeRepository changeRepository;

    @Autowired
    private InventoryApplicationService inventoryService;

    @Autowired
    private InventoryReservationService reservationService;

    @Autowired
    private InventoryItemRepository itemRepository;

    @Autowired
    private InventoryReservationRepository reservationRepository;

    @Test
    void createsReadsAndConditionallyUpdatesInventory() throws Exception {
        String sku = "sku-" + UUID.randomUUID();
        long initialChangeCount = changeRepository.count();

        mockMvc.perform(put("/api/v1/inventory/{sku}", sku)
                        .header("If-None-Match", "*")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(100, "initial-load")))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.sku").value(sku.toUpperCase()))
                .andExpect(jsonPath("$.availableQuantity").value(100));

        String getBody = mockMvc.perform(get("/api/v1/inventory/{sku}", sku))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode current = objectMapper.readTree(getBody);
        assertThat(current.get("reservedQuantity").asLong()).isZero();

        mockMvc.perform(put("/api/v1/inventory/{sku}", sku)
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(125, "erp-sync")))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.onHandQuantity").value(125));

        mockMvc.perform(put("/api/v1/inventory/{sku}", sku)
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(130, "stale-sync")))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.title").value("Inventory version conflict"));

        assertThat(changeRepository.count()).isEqualTo(initialChangeCount + 2);
    }

    @Test
    void requiresAConcurrencyPrecondition() throws Exception {
        String sku = "sku-" + UUID.randomUUID();

        mockMvc.perform(put("/api/v1/inventory/{sku}", sku)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(100, "missing-precondition")))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    void reservesAndReleasesInventoryIdempotently() {
        String sku = ("SAGA-" + UUID.randomUUID()).toUpperCase();
        UUID orderId = UUID.randomUUID();
        inventoryService.create(sku, 10, "TEST_SETUP", "saga-test");
        OrderPlacedEvent placed = new OrderPlacedEvent(
                UUID.randomUUID(),
                "OrderPlaced",
                OffsetDateTime.now(ZoneOffset.UTC),
                orderId,
                UUID.randomUUID(),
                "USD",
                List.of(new OrderPlacedEvent.Item(sku, 4)));

        reservationService.reserve(placed);
        reservationService.reserve(placed);

        assertThat(itemRepository.findById(sku).orElseThrow().getReservedQuantity())
                .isEqualTo(4);
        assertThat(reservationRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(com.orderplatform.inventory.domain.entity.InventoryReservationEntity.Status.RESERVED);

        InventoryReleaseRequestedEvent release = new InventoryReleaseRequestedEvent(
                UUID.randomUUID(),
                "InventoryReleaseRequested",
                OffsetDateTime.now(ZoneOffset.UTC),
                orderId);
        reservationService.release(release);
        reservationService.release(release);

        assertThat(itemRepository.findById(sku).orElseThrow().getReservedQuantity())
                .isZero();
        assertThat(reservationRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(com.orderplatform.inventory.domain.entity.InventoryReservationEntity.Status.RELEASED);
    }

    private String requestBody(long onHandQuantity, String reference) {
        return """
                {
                  "onHandQuantity": %d,
                  "reason": "WAREHOUSE_SYNCHRONIZATION",
                  "sourceReference": "%s"
                }
                """.formatted(onHandQuantity, reference);
    }
}
