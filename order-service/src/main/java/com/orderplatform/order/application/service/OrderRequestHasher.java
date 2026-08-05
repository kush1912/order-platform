package com.orderplatform.order.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class OrderRequestHasher {

    private final ObjectMapper objectMapper;

    public OrderRequestHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String hash(CreateOrderCommand command) {
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(command);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(serialized);
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize order request for idempotency", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
