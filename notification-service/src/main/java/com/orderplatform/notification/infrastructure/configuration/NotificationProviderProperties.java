package com.orderplatform.notification.infrastructure.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties("notification.providers")
public record NotificationProviderProperties(
        @Valid @NotNull Email email,
        @Valid @NotNull HttpProvider sms,
        @Valid @NotNull HttpProvider whatsapp,
        @Valid @NotNull HttpClient httpClient) {

    public record Email(@NotBlank String from) {
    }

    public record HttpProvider(@NotNull URI baseUrl) {
    }

    public record HttpClient(
            @NotNull Duration connectTimeout,
            @NotNull Duration readTimeout) {
    }
}
