package com.orderplatform.inventory.infrastructure.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Duration publishTimeout;

    public OutboxPublisher(
            OutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${inventory.outbox.publish-timeout}") Duration publishTimeout) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.publishTimeout = publishTimeout;
    }

    @Scheduled(fixedDelayString = "${inventory.outbox.fixed-delay}")
    @Transactional
    public void publishPendingEvents() {
        for (OutboxEventEntity event : repository.lockNextBatch()) {
            try {
                kafkaTemplate.send(
                                event.getTopic(),
                                event.getMessageKey(),
                                event.getPayload())
                        .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
                event.markPublished();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                event.markFailed(exception.getMessage());
                return;
            } catch (ExecutionException | TimeoutException exception) {
                event.markFailed(exception.getMessage());
                LOGGER.error(
                        "Outbox publication failed eventId={} topic={} attemptsWillBeRetried=true",
                        event.getId(),
                        event.getTopic(),
                        exception);
            }
        }
    }
}
