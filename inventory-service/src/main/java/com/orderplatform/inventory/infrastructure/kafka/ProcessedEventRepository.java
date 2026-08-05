package com.orderplatform.inventory.infrastructure.kafka;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository
        extends JpaRepository<ProcessedEventEntity, ProcessedEventEntity.Key> {
}
