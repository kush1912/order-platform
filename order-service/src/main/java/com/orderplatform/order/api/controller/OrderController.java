package com.orderplatform.order.api.controller;

import com.orderplatform.order.api.dto.request.CreateOrderRequest;
import com.orderplatform.order.api.dto.response.OrderResponse;
import com.orderplatform.order.api.mapper.OrderMapper;
import com.orderplatform.order.application.service.CreateOrderResult;
import com.orderplatform.order.application.service.CancelOrderResult;
import com.orderplatform.order.application.service.OrderCancellationService;
import com.orderplatform.order.application.service.OrderApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final OrderApplicationService orderService;
    private final OrderMapper orderMapper;
    private final OrderCancellationService cancellationService;

    public OrderController(
            OrderApplicationService orderService,
            OrderMapper orderMapper,
            OrderCancellationService cancellationService) {
        this.orderService = orderService;
        this.orderMapper = orderMapper;
        this.cancellationService = cancellationService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestHeader(IDEMPOTENCY_KEY)
            @NotBlank
            @Size(max = 128)
            String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request,
            UriComponentsBuilder uriBuilder) {
        CreateOrderResult result = orderService.create(
                orderMapper.toCommand(request),
                idempotencyKey);
        OrderResponse response = orderMapper.toResponse(result.order());
        if (result.replayed()) {
            return ResponseEntity.ok()
                    .header(IDEMPOTENT_REPLAYED, "true")
                    .body(response);
        }

        URI location = uriBuilder.path("/api/v1/orders/{orderId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> get(@PathVariable UUID orderId) {
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(orderMapper.toResponse(orderService.get(orderId)));
    }

    @PostMapping("/{orderId}/cancellations")
    public ResponseEntity<OrderResponse> cancel(
            @PathVariable UUID orderId,
            @RequestHeader(IDEMPOTENCY_KEY)
            @NotBlank
            @Size(max = 128)
            String idempotencyKey) {
        CancelOrderResult result = cancellationService.cancel(orderId, idempotencyKey);
        ResponseEntity.BodyBuilder response = result.replayed()
                ? ResponseEntity.ok().header(IDEMPOTENT_REPLAYED, "true")
                : ResponseEntity.accepted();
        return response
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(orderMapper.toResponse(result.order()));
    }
}
