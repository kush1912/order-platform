package com.orderplatform.notification.infrastructure.kafka;

final class PermanentNotificationException extends NotificationMessageException {

    PermanentNotificationException(String message) {
        super(message, null);
    }
}
