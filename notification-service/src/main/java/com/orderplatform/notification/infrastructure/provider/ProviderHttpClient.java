package com.orderplatform.notification.infrastructure.provider;

import com.orderplatform.notification.domain.entity.NotificationChannel;
import com.orderplatform.notification.domain.exception.NotificationProviderException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ProviderHttpClient {

    public String send(RestClient restClient, NotificationChannel channel, Object payload) {
        try {
            ProviderResponse response = restClient.post()
                    .uri("/v1/messages")
                    .body(payload)
                    .retrieve()
                    .body(ProviderResponse.class);
            if (response == null || response.providerMessageId() == null) {
                throw providerException(
                        channel,
                        true,
                        "INVALID_PROVIDER_RESPONSE",
                        "Provider returned an incomplete response",
                        null);
            }
            return response.providerMessageId();
        } catch (HttpClientErrorException.TooManyRequests exception) {
            throw providerException(
                    channel,
                    true,
                    "RATE_LIMITED",
                    "Provider rate limit exceeded",
                    exception);
        } catch (HttpServerErrorException exception) {
            throw providerException(
                    channel,
                    true,
                    "PROVIDER_UNAVAILABLE",
                    "Provider returned " + exception.getStatusCode(),
                    exception);
        } catch (RestClientResponseException exception) {
            boolean retryable = exception.getStatusCode().is5xxServerError();
            throw providerException(
                    channel,
                    retryable,
                    "PROVIDER_HTTP_" + exception.getStatusCode().value(),
                    "Provider rejected the request with " + exception.getStatusCode(),
                    exception);
        } catch (ResourceAccessException exception) {
            throw providerException(
                    channel,
                    true,
                    "PROVIDER_IO_ERROR",
                    "Provider connection failed",
                    exception);
        }
    }

    private NotificationProviderException providerException(
            NotificationChannel channel,
            boolean retryable,
            String failureCode,
            String message,
            Throwable cause) {
        return new NotificationProviderException(channel, retryable, failureCode, message, cause);
    }

    private record ProviderResponse(
            String providerMessageId,
            String status,
            String clientReference) {
    }
}
