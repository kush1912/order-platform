package com.orderplatform.notification.infrastructure.kafka;

final class RetryableNotificationException extends NotificationMessageException {

    RetryableNotificationException(String message) {
        super(message, null);
    }
}
