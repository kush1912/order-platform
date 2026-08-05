package com.orderplatform.notification.infrastructure.kafka;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class NotificationKafkaConfiguration {

    @Bean
    CommonErrorHandler notificationErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${notification.retry.interval}") Duration interval,
            @Value("${notification.retry.max-attempts}") long maxAttempts) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        "notifications.requested.v1.dlt",
                        record.partition()));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(interval.toMillis(), maxAttempts));
        errorHandler.addNotRetryableExceptions(
                PoisonNotificationMessageException.class,
                PermanentNotificationException.class);
        return errorHandler;
    }
}
