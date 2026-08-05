package com.orderplatform.notification.application.service;

import com.orderplatform.notification.domain.entity.DeliveryResult;
import com.orderplatform.notification.domain.entity.DeliveryStatus;
import com.orderplatform.notification.domain.entity.NotificationChannel;
import com.orderplatform.notification.domain.entity.NotificationCommand;
import com.orderplatform.notification.domain.exception.NotificationProviderException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationDispatchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final Map<NotificationChannel, NotificationSender> senders;
    private final MeterRegistry meterRegistry;

    public NotificationDispatchService(List<NotificationSender> senders, MeterRegistry meterRegistry) {
        this.senders = indexSenders(senders);
        this.meterRegistry = meterRegistry;
    }

    public List<DeliveryResult> dispatch(NotificationCommand command) {
        return command.channels().stream()
                .sorted()
                .map(channel -> dispatch(channel, command))
                .toList();
    }

    private DeliveryResult dispatch(NotificationChannel channel, NotificationCommand command) {
        NotificationSender sender = senders.get(channel);
        if (sender == null) {
            throw new IllegalStateException("No notification sender configured for channel " + channel);
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String providerMessageId = sender.send(command);
            record(sample, channel, DeliveryStatus.ACCEPTED);
            return new DeliveryResult(channel, DeliveryStatus.ACCEPTED, providerMessageId, null);
        } catch (NotificationProviderException exception) {
            DeliveryStatus status = exception.retryable()
                    ? DeliveryStatus.FAILED_RETRYABLE
                    : DeliveryStatus.FAILED_PERMANENT;
            LOGGER.warn(
                    "Notification provider call failed: channel={}, clientReference={}, "
                            + "failureCode={}, retryable={}",
                    exception.channel(),
                    command.clientReference(),
                    exception.failureCode(),
                    exception.retryable(),
                    exception);
            record(sample, channel, status);
            return new DeliveryResult(channel, status, null, exception.failureCode());
        }
    }

    private void record(Timer.Sample sample, NotificationChannel channel, DeliveryStatus status) {
        sample.stop(Timer.builder("notification.delivery")
                .description("Notification provider call duration and outcome")
                .tag("channel", channel.name().toLowerCase())
                .tag("outcome", status.name().toLowerCase())
                .register(meterRegistry));
    }

    private static Map<NotificationChannel, NotificationSender> indexSenders(List<NotificationSender> senders) {
        Map<NotificationChannel, NotificationSender> indexed = new EnumMap<>(NotificationChannel.class);
        for (NotificationSender sender : senders) {
            NotificationSender previous = indexed.put(sender.channel(), sender);
            if (previous != null) {
                throw new IllegalStateException("Multiple senders configured for channel " + sender.channel());
            }
        }
        return Map.copyOf(indexed);
    }
}
