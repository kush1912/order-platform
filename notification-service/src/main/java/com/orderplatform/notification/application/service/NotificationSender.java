package com.orderplatform.notification.application.service;

import com.orderplatform.notification.domain.entity.NotificationChannel;
import com.orderplatform.notification.domain.entity.NotificationCommand;

public interface NotificationSender {

    NotificationChannel channel();

    String send(NotificationCommand command);
}
