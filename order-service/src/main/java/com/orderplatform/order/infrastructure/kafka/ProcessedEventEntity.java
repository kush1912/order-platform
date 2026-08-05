package com.orderplatform.order.infrastructure.kafka;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventEntity.Key.class)
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Id
    @Column(name = "consumer_name", length = 128)
    private String consumerName;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    protected ProcessedEventEntity() {
    }

    public static ProcessedEventEntity create(UUID eventId, String consumerName) {
        ProcessedEventEntity event = new ProcessedEventEntity();
        event.eventId = eventId;
        event.consumerName = consumerName;
        event.processedAt = OffsetDateTime.now(ZoneOffset.UTC);
        return event;
    }

    public static class Key implements Serializable {
        private UUID eventId;
        private String consumerName;

        public Key() {
        }

        public Key(UUID eventId, String consumerName) {
            this.eventId = eventId;
            this.consumerName = consumerName;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(eventId, key.eventId)
                    && Objects.equals(consumerName, key.consumerName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventId, consumerName);
        }
    }
}
