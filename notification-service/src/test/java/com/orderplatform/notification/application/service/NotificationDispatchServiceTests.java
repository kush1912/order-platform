package com.orderplatform.notification.application.service;

import com.orderplatform.notification.domain.entity.DeliveryStatus;
import com.orderplatform.notification.domain.entity.NotificationChannel;
import com.orderplatform.notification.domain.entity.NotificationCommand;
import com.orderplatform.notification.domain.exception.NotificationProviderException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDispatchServiceTests {

    @Test
    void dispatchesEveryRequestedChannel() {
        NotificationDispatchService service = serviceWith(
                sender(NotificationChannel.EMAIL, command -> "email-id"),
                sender(NotificationChannel.SMS, command -> "sms-id"),
                sender(NotificationChannel.WHATSAPP, command -> "whatsapp-id"));

        var results = service.dispatch(command());

        assertThat(results)
                .extracting(result -> result.status())
                .containsOnly(DeliveryStatus.ACCEPTED);
        assertThat(results)
                .extracting(result -> result.providerMessageId())
                .containsExactly("email-id", "sms-id", "whatsapp-id");
    }

    @Test
    void preservesRetryableFailureWithoutStoppingOtherChannels() {
        NotificationDispatchService service = serviceWith(
                sender(NotificationChannel.EMAIL, command -> "email-id"),
                sender(NotificationChannel.SMS, command -> {
                    throw new NotificationProviderException(
                            NotificationChannel.SMS,
                            true,
                            "RATE_LIMITED",
                            "Provider rate limit exceeded",
                            null);
                }),
                sender(NotificationChannel.WHATSAPP, command -> "whatsapp-id"));

        var results = service.dispatch(command());

        assertThat(results)
                .extracting(result -> result.status())
                .containsExactly(
                        DeliveryStatus.ACCEPTED,
                        DeliveryStatus.FAILED_RETRYABLE,
                        DeliveryStatus.ACCEPTED);
        assertThat(results.get(1).failureCode()).isEqualTo("RATE_LIMITED");
    }

    private NotificationDispatchService serviceWith(NotificationSender... senders) {
        return new NotificationDispatchService(List.of(senders), new SimpleMeterRegistry());
    }

    private NotificationSender sender(
            NotificationChannel channel,
            Function<NotificationCommand, String> behavior) {
        return new NotificationSender() {
            @Override
            public NotificationChannel channel() {
                return channel;
            }

            @Override
            public String send(NotificationCommand command) {
                return behavior.apply(command);
            }
        };
    }

    private NotificationCommand command() {
        return new NotificationCommand(
                "ORD-LOCAL-1002",
                EnumSet.allOf(NotificationChannel.class),
                "customer@example.com",
                "+15550001002",
                "Order confirmed",
                "Your order has been confirmed.",
                "order_confirmed");
    }
}
