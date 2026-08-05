package com.orderplatform.notification.infrastructure.kafka;

import com.orderplatform.notification.infrastructure.repository.NotificationDeadLetterRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

@Component
public class NotificationDeadLetterListener {

    private static final String ORIGINAL_TOPIC_HEADER = "kafka_dlt-original-topic";
    private static final String EXCEPTION_CLASS_HEADER = "kafka_dlt-exception-fqcn";
    private static final String EXCEPTION_MESSAGE_HEADER = "kafka_dlt-exception-message";

    private final NotificationDeadLetterRepository repository;

    public NotificationDeadLetterListener(NotificationDeadLetterRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(
            topics = "notifications.requested.v1.dlt",
            groupId = "notification-dead-letter-storage-v1")
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        repository.save(
                record.topic(),
                record.partition(),
                record.offset(),
                stringHeader(record, ORIGINAL_TOPIC_HEADER),
                record.value(),
                stringHeader(record, EXCEPTION_CLASS_HEADER),
                stringHeader(record, EXCEPTION_MESSAGE_HEADER));
    }

    private String stringHeader(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null) {
            return null;
        }
        return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(header.value())).toString();
    }
}
