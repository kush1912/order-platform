package com.orderplatform.notification.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderplatform.notification.application.service.NotificationDispatchService;
import com.orderplatform.notification.domain.entity.DeliveryResult;
import com.orderplatform.notification.domain.entity.DeliveryStatus;
import com.orderplatform.notification.domain.entity.NotificationCommand;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class NotificationRequestedListener {

    private final ObjectMapper objectMapper;
    private final NotificationDispatchService dispatchService;

    public NotificationRequestedListener(
            ObjectMapper objectMapper,
            NotificationDispatchService dispatchService) {
        this.objectMapper = objectMapper;
        this.dispatchService = dispatchService;
    }

    @KafkaListener(
            topics = "notifications.requested.v1",
            groupId = "notification-dispatch-v1")
    public void onNotificationRequested(String payload) {
        NotificationRequestedEvent event;
        try {
            event = objectMapper.readValue(payload, NotificationRequestedEvent.class);
        } catch (JsonProcessingException exception) {
            throw new PoisonNotificationMessageException(
                    "Notification message is not valid JSON",
                    exception);
        }

        NotificationCommand command = new NotificationCommand(
                event.clientReference(),
                Set.of(event.channel()),
                event.email(),
                event.phoneNumber(),
                event.subject(),
                event.message(),
                event.whatsappTemplateName());
        List<DeliveryResult> results = dispatchService.dispatch(command);
        DeliveryResult result = results.getFirst();
        if (result.status() == DeliveryStatus.FAILED_RETRYABLE) {
            throw new RetryableNotificationException(
                    "Retryable notification failure: " + result.failureCode());
        }
        if (result.status() == DeliveryStatus.FAILED_PERMANENT) {
            throw new PermanentNotificationException(
                    "Permanent notification failure: " + result.failureCode());
        }
    }
}
