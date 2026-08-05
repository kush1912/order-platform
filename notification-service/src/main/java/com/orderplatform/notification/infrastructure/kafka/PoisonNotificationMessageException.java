package com.orderplatform.notification.infrastructure.kafka;

final class PoisonNotificationMessageException extends NotificationMessageException {

    PoisonNotificationMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
