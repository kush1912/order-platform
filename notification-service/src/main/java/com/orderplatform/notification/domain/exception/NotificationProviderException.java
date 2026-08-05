package com.orderplatform.notification.domain.exception;

import com.orderplatform.notification.domain.entity.NotificationChannel;

public class NotificationProviderException extends RuntimeException {

    private final NotificationChannel channel;
    private final boolean retryable;
    private final String failureCode;

    public NotificationProviderException(
            NotificationChannel channel,
            boolean retryable,
            String failureCode,
            String message,
            Throwable cause) {
        super(message, cause);
        this.channel = channel;
        this.retryable = retryable;
        this.failureCode = failureCode;
    }

    public NotificationChannel channel() {
        return channel;
    }

    public boolean retryable() {
        return retryable;
    }

    public String failureCode() {
        return failureCode;
    }
}
