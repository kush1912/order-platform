package com.orderplatform.notification.api.controller;

import com.orderplatform.notification.infrastructure.repository.NotificationDeadLetter;
import com.orderplatform.notification.infrastructure.repository.NotificationDeadLetterRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/notifications/dead-letters")
public class NotificationDeadLetterController {

    private final NotificationDeadLetterRepository repository;

    public NotificationDeadLetterController(NotificationDeadLetterRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<NotificationDeadLetter> getLatest() {
        return repository.findLatest();
    }
}
