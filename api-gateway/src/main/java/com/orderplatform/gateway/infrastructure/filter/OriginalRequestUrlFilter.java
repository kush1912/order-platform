package com.orderplatform.gateway.infrastructure.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class OriginalRequestUrlFilter implements GlobalFilter, Ordered {

    public static final String ORIGINAL_REQUEST_URI_ATTRIBUTE =
            OriginalRequestUrlFilter.class.getName() + ".originalRequestUri";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getAttributes().putIfAbsent(
                ORIGINAL_REQUEST_URI_ATTRIBUTE,
                exchange.getRequest().getURI());
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
