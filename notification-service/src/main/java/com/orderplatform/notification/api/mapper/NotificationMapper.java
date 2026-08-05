package com.orderplatform.notification.api.mapper;

import com.orderplatform.notification.api.dto.request.NotificationDispatchRequest;
import com.orderplatform.notification.api.dto.response.NotificationDispatchResponse;
import com.orderplatform.notification.domain.entity.DeliveryResult;
import com.orderplatform.notification.domain.entity.NotificationCommand;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class NotificationMapper {

    public NotificationCommand toCommand(NotificationDispatchRequest request) {
        return new NotificationCommand(
                request.clientReference(),
                Set.copyOf(request.channels()),
                request.email(),
                request.phoneNumber(),
                request.subject(),
                request.message(),
                request.whatsappTemplateName());
    }

    public NotificationDispatchResponse toResponse(
            String clientReference,
            List<DeliveryResult> results) {
        List<NotificationDispatchResponse.ChannelDeliveryResponse> deliveries = results.stream()
                .map(result -> new NotificationDispatchResponse.ChannelDeliveryResponse(
                        result.channel(),
                        result.status(),
                        result.providerMessageId(),
                        result.failureCode()))
                .toList();
        return new NotificationDispatchResponse(clientReference, deliveries);
    }
}
