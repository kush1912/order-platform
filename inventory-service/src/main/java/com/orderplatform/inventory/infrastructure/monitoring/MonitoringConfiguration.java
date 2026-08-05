package com.orderplatform.inventory.infrastructure.monitoring;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MonitoringConfiguration {

    private static final int MAX_HTTP_URI_TAGS = 100;

    @Bean
    MeterFilter httpServerUriTagCardinalityLimit() {
        return MeterFilter.maximumAllowableTags(
                "http.server.requests",
                "uri",
                MAX_HTTP_URI_TAGS,
                MeterFilter.deny());
    }
}
