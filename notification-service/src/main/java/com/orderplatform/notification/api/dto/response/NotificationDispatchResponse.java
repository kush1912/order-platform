package com.orderplatform.notification.api.dto.response;

import com.orderplatform.notification.domain.entity.DeliveryStatus;
import com.orderplatform.notification.domain.entity.NotificationChannel;

import java.util.List;

public record NotificationDispatchResponse(
        String clientReference,
        List<ChannelDeliveryResponse> deliveries) {

    public record ChannelDeliveryResponse(
            NotificationChannel channel,
            DeliveryStatus status,
            String providerMessageId,
            String failureCode) {
    }
}
