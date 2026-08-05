package com.orderplatform.notification.infrastructure.kafka;

abstract class NotificationMessageException extends RuntimeException {

    NotificationMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
