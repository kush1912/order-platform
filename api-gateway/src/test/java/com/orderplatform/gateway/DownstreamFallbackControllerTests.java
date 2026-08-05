package com.orderplatform.gateway;

import com.orderplatform.gateway.api.controller.DownstreamFallbackController;
import com.orderplatform.gateway.infrastructure.filter.OriginalRequestUrlFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.util.concurrent.TimeoutException;
import java.net.URI;
import java.util.LinkedHashSet;

import static org.assertj.core.api.Assertions.assertThat;

class DownstreamFallbackControllerTests {

    @Test
    void returnsGatewayTimeoutForDeadlineFailure() {
        var registry = new SimpleMeterRegistry();
        var controller = new DownstreamFallbackController(registry);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/test").build());
        exchange.getAttributes().put(
                ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR,
                new TimeoutException("deadline exceeded"));
        exchange.getAttributes().put(
                ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR,
                new LinkedHashSet<>(java.util.List.of(
                        URI.create("http://localhost/internal/fallback/order-service"),
                        URI.create("https://api.orderplatform.local/api/v1/orders/test"))));
        exchange.getAttributes().put(
                OriginalRequestUrlFilter.ORIGINAL_REQUEST_URI_ATTRIBUTE,
                URI.create("https://api.orderplatform.local/api/v1/orders/test"));

        var response = controller.orderService(exchange).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("15");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Downstream timeout");
        assertThat(response.getBody().getInstance())
                .isEqualTo(URI.create(
                        "https://api.orderplatform.local/api/v1/orders/test"));
        assertThat(registry.counter(
                "gateway.downstream.failures",
                "service",
                "order-service",
                "outcome",
                "timeout").count()).isEqualTo(1);
    }

    @Test
    void returnsServiceUnavailableForOpenCircuit() {
        var controller = new DownstreamFallbackController(new SimpleMeterRegistry());
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders/test").build());
        exchange.getAttributes().put(
                ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR,
                new IllegalStateException("circuit is open"));

        var response = controller.orderService(exchange).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Downstream unavailable");
    }
}
