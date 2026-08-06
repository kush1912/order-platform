package com.orderplatform.notification.infrastructure.monitoring;

import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

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

    /**
     * Filters infrastructure noise out of observations (metrics + traces) so real
     * request/business traces are not buried in Jaeger and the OTLP exporter is not
     * pressured into dropping spans:
     * <ul>
     *   <li>Kubernetes actuator health-probe and Prometheus-scrape HTTP requests.</li>
     *   <li>Spring {@code @Scheduled} task polling which otherwise emits a single-span
     *       trace on every empty poll.</li>
     * </ul>
     * Real Kafka publish spans are unaffected and remain visible as their own traces.
     */
    @Bean
    ObservationPredicate excludeInfrastructureNoiseObservations() {
        return (name, context) -> {
            if (name.startsWith("tasks.scheduled")) {
                return false;
            }
            if (context instanceof ServerRequestObservationContext serverContext) {
                String uri = serverContext.getCarrier().getRequestURI();
                return uri == null || !uri.startsWith("/actuator");
            }
            return true;
        };
    }
}
