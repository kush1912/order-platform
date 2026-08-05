package com.orderplatform.inventory.infrastructure.kafka;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEventEntity {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(nullable = false, length = 128)
    private String topic;

    @Column(name = "message_key", nullable = false, length = 128)
    private String messageKey;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String payload;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    protected OutboxEventEntity() {
    }

    public static OutboxEventEntity create(
            UUID id,
            UUID aggregateId,
            String topic,
            String messageKey,
            String eventType,
            String payload) {
        OutboxEventEntity event = new OutboxEventEntity();
        event.id = id;
        event.aggregateId = aggregateId;
        event.topic = topic;
        event.messageKey = messageKey;
        event.eventType = eventType;
        event.payload = payload;
        event.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        return event;
    }

    public void markPublished() {
        attempts++;
        publishedAt = OffsetDateTime.now(ZoneOffset.UTC);
        lastError = null;
    }

    public void markFailed(String error) {
        attempts++;
        String safeError = error == null ? "Unknown publication failure" : error;
        lastError = safeError.substring(0, Math.min(safeError.length(), 1024));
    }

    public UUID getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getPayload() {
        return payload;
    }
}
