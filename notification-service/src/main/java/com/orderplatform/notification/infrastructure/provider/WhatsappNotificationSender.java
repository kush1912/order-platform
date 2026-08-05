package com.orderplatform.notification.infrastructure.provider;

import com.orderplatform.notification.application.service.NotificationSender;
import com.orderplatform.notification.domain.entity.NotificationChannel;
import com.orderplatform.notification.domain.entity.NotificationCommand;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class WhatsappNotificationSender implements NotificationSender {

    private final RestClient restClient;
    private final ProviderHttpClient providerHttpClient;

    public WhatsappNotificationSender(
            @Qualifier("whatsappRestClient") RestClient restClient,
            ProviderHttpClient providerHttpClient) {
        this.restClient = restClient;
        this.providerHttpClient = providerHttpClient;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WHATSAPP;
    }

    @Override
    public String send(NotificationCommand command) {
        return providerHttpClient.send(
                restClient,
                channel(),
                new WhatsappRequest(
                        command.phoneNumber(),
                        command.whatsappTemplateName(),
                        Map.of("message", command.message()),
                        command.clientReference()));
    }

    private record WhatsappRequest(
            String to,
            String templateName,
            Map<String, String> variables,
            String clientReference) {
    }
}
