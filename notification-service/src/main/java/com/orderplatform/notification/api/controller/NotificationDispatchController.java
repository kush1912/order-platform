package com.orderplatform.notification.api.controller;

import com.orderplatform.notification.api.dto.request.NotificationDispatchRequest;
import com.orderplatform.notification.api.dto.response.NotificationDispatchResponse;
import com.orderplatform.notification.api.mapper.NotificationMapper;
import com.orderplatform.notification.application.service.NotificationDispatchService;
import com.orderplatform.notification.domain.entity.DeliveryResult;
import com.orderplatform.notification.domain.entity.DeliveryStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/notifications")
public class NotificationDispatchController {

    private final NotificationDispatchService dispatchService;
    private final NotificationMapper mapper;

    public NotificationDispatchController(
            NotificationDispatchService dispatchService,
            NotificationMapper mapper) {
        this.dispatchService = dispatchService;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<NotificationDispatchResponse> dispatch(
            @Valid @RequestBody NotificationDispatchRequest request) {
        List<DeliveryResult> results = dispatchService.dispatch(mapper.toCommand(request));
        boolean allAccepted = results.stream()
                .allMatch(result -> result.status() == DeliveryStatus.ACCEPTED);
        HttpStatus status = allAccepted ? HttpStatus.ACCEPTED : HttpStatus.MULTI_STATUS;
        return ResponseEntity.status(status)
                .body(mapper.toResponse(request.clientReference(), results));
    }
}
