CREATE TABLE notification_dead_letters (
    id UUID PRIMARY KEY,
    dead_letter_topic VARCHAR(128) NOT NULL,
    dead_letter_partition INTEGER NOT NULL,
    dead_letter_offset BIGINT NOT NULL,
    original_topic VARCHAR(128),
    payload TEXT NOT NULL,
    failure_class VARCHAR(512),
    failure_message VARCHAR(2048),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uk_notification_dead_letter_location
    ON notification_dead_letters (
        dead_letter_topic,
        dead_letter_partition,
        dead_letter_offset
    );

CREATE INDEX idx_notification_dead_letters_created
    ON notification_dead_letters (created_at DESC);
