package com.orderplatform.notification.infrastructure.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class NotificationDeadLetterRepository {

    private final JdbcClient jdbcClient;

    public NotificationDeadLetterRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void save(
            String deadLetterTopic,
            int deadLetterPartition,
            long deadLetterOffset,
            String originalTopic,
            String payload,
            String failureClass,
            String failureMessage) {
        jdbcClient.sql("""
                        INSERT INTO notification_dead_letters (
                            id,
                            dead_letter_topic,
                            dead_letter_partition,
                            dead_letter_offset,
                            original_topic,
                            payload,
                            failure_class,
                            failure_message,
                            created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (
                            dead_letter_topic,
                            dead_letter_partition,
                            dead_letter_offset
                        ) DO NOTHING
                        """)
                .params(
                        UUID.randomUUID(),
                        deadLetterTopic,
                        deadLetterPartition,
                        deadLetterOffset,
                        originalTopic,
                        payload,
                        failureClass,
                        failureMessage,
                        OffsetDateTime.now(ZoneOffset.UTC))
                .update();
    }

    public List<NotificationDeadLetter> findLatest() {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            dead_letter_topic,
                            dead_letter_partition,
                            dead_letter_offset,
                            original_topic,
                            payload,
                            failure_class,
                            failure_message,
                            created_at
                        FROM notification_dead_letters
                        ORDER BY created_at DESC
                        LIMIT 100
                        """)
                .query(NotificationDeadLetter.class)
                .list();
    }
}
