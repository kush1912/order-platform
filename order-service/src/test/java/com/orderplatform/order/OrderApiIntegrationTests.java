package com.orderplatform.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.order.application.service.CreateOrderCommand;
import com.orderplatform.order.application.service.CreateOrderResult;
import com.orderplatform.order.application.service.OrderApplicationService;
import com.orderplatform.order.application.service.OrderSagaService;
import com.orderplatform.order.infrastructure.kafka.InventoryReleasedEvent;
import com.orderplatform.order.infrastructure.kafka.InventoryReservationResultEvent;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "order.outbox.fixed-delay=1h"
})
@AutoConfigureMockMvc
class OrderApiIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderApplicationService orderService;

    @Autowired
    private OrderSagaService orderSagaService;

    @Test
    void createsRetrievesAndReplaysAnOrder() throws Exception {
        String idempotencyKey = "order-" + UUID.randomUUID();
        String request = requestBody("49.9900");

        String responseBody = mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(99.98))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(responseBody);
        String orderId = response.get("id").asText();

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.items[0].sku").value("SKU-1001"));

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(orderId));
    }

    @Test
    void rejectsAnIdempotencyKeyReusedForDifferentPayload() throws Exception {
        String idempotencyKey = "order-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("49.9900")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("59.9900")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Idempotency conflict"));
    }

    @Test
    void concurrentDuplicatesCreateOneOrder() throws Exception {
        String idempotencyKey = "concurrent-" + UUID.randomUUID();
        CreateOrderCommand command = new CreateOrderCommand(
                UUID.randomUUID(),
                "USD",
                List.of(new CreateOrderCommand.OrderItemCommand(
                        "SKU-CONCURRENT",
                        2,
                        new BigDecimal("10.0000"))));
        int requestCount = 12;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(requestCount)) {
            var futures = java.util.stream.IntStream.range(0, requestCount)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return orderService.create(command, idempotencyKey);
                    }))
                    .toList();

            ready.await();
            start.countDown();
            List<CreateOrderResult> results = futures.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    })
                    .toList();

            assertThat(results)
                    .extracting(result -> result.order().getId())
                    .containsOnly(results.getFirst().order().getId());
            assertThat(results)
                    .filteredOn(result -> !result.replayed())
                    .hasSize(1);
        }
    }

    @Test
    void confirmsAndCancelsOnlyAfterInventoryRelease() throws Exception {
        CreateOrderResult created = orderService.create(
                new CreateOrderCommand(
                        UUID.randomUUID(),
                        "USD",
                        List.of(new CreateOrderCommand.OrderItemCommand(
                                "SKU-CANCEL",
                                2,
                                new BigDecimal("10.0000")))),
                "create-" + UUID.randomUUID());
        UUID orderId = created.order().getId();

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancellations", orderId)
                        .header("Idempotency-Key", "cancel-before-confirm-" + UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid order state"));

        orderSagaService.applyReservationResult(new InventoryReservationResultEvent(
                UUID.randomUUID(),
                "InventoryReserved",
                OffsetDateTime.now(ZoneOffset.UTC),
                orderId,
                "RESERVED",
                null));

        String cancellationKey = "cancel-" + UUID.randomUUID();
        mockMvc.perform(post("/api/v1/orders/{orderId}/cancellations", orderId)
                        .header("Idempotency-Key", cancellationKey))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("CANCELLATION_PENDING"));

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancellations", orderId)
                        .header("Idempotency-Key", cancellationKey))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"));

        orderSagaService.applyInventoryReleased(new InventoryReleasedEvent(
                UUID.randomUUID(),
                "InventoryReleased",
                OffsetDateTime.now(ZoneOffset.UTC),
                orderId));

        mockMvc.perform(get("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    private String requestBody(String unitPrice) {
        return """
                {
                  "customerId": "%s",
                  "currency": "USD",
                  "items": [
                    {
                      "sku": "SKU-1001",
                      "quantity": 2,
                      "unitPrice": %s
                    }
                  ]
                }
                """.formatted(UUID.randomUUID(), unitPrice);
    }
}
