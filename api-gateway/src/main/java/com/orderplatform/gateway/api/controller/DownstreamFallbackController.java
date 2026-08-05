package com.orderplatform.gateway.api.controller;

import com.orderplatform.gateway.infrastructure.filter.OriginalRequestUrlFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.TimeoutException;

@RestController
public class DownstreamFallbackController {

    private final MeterRegistry meterRegistry;

    public DownstreamFallbackController(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @RequestMapping("/internal/fallback/order-service")
    public Mono<ResponseEntity<ProblemDetail>> orderService(ServerWebExchange exchange) {
        Throwable failure = exchange.getAttribute(
                ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        if (failure == null) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        boolean timedOut = isTimeout(failure);
        HttpStatus status = timedOut
                ? HttpStatus.GATEWAY_TIMEOUT
                : HttpStatus.SERVICE_UNAVAILABLE;
        String outcome = timedOut ? "timeout" : "circuit_open_or_unavailable";
        meterRegistry.counter(
                "gateway.downstream.failures",
                "service",
                "order-service",
                "outcome",
                outcome).increment();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                timedOut
                        ? "Order Service did not respond within the 2 second deadline"
                        : "Order Service is temporarily unavailable");
        problem.setTitle(timedOut ? "Downstream timeout" : "Downstream unavailable");
        problem.setType(URI.create("https://orderplatform.local/problems/" + outcome));
        URI originalRequestUri = originalRequestUri(exchange);
        if (originalRequestUri != null) {
            problem.setInstance(originalRequestUri);
        }
        problem.setProperty("downstreamService", "order-service");

        return Mono.just(ResponseEntity.status(status)
                .header(HttpHeaders.RETRY_AFTER, "15")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(problem));
    }

    private boolean isTimeout(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof TimeoutException
                    || current.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private URI originalRequestUri(ServerWebExchange exchange) {
        URI capturedUri = exchange.getAttribute(
                OriginalRequestUrlFilter.ORIGINAL_REQUEST_URI_ATTRIBUTE);
        if (capturedUri != null) {
            return capturedUri;
        }
        Set<URI> originalUris = exchange.getAttribute(
                ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        if (originalUris == null || originalUris.isEmpty()) {
            return null;
        }
        return originalUris.stream()
                .filter(uri -> !uri.getPath().startsWith("/internal/fallback/"))
                .findFirst()
                .orElse(null);
    }
}
