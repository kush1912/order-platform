CREATE TABLE inventory_reservations (
    order_id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_inventory_reservation_status CHECK (
        status IN ('RESERVED', 'REJECTED', 'RELEASED')
    )
);

CREATE TABLE inventory_reservation_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    sku VARCHAR(64) NOT NULL,
    quantity BIGINT NOT NULL,
    CONSTRAINT fk_inventory_reservation_items_reservation
        FOREIGN KEY (order_id) REFERENCES inventory_reservations (order_id) ON DELETE CASCADE,
    CONSTRAINT fk_inventory_reservation_items_inventory
        FOREIGN KEY (sku) REFERENCES inventory_items (sku),
    CONSTRAINT uk_inventory_reservation_order_sku UNIQUE (order_id, sku),
    CONSTRAINT ck_inventory_reservation_item_quantity CHECK (quantity > 0)
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    topic VARCHAR(128) NOT NULL,
    message_key VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(1024)
);

CREATE INDEX idx_inventory_outbox_unpublished
    ON outbox_events (created_at)
    WHERE published_at IS NULL;

CREATE TABLE processed_events (
    event_id UUID NOT NULL,
    consumer_name VARCHAR(128) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, consumer_name)
);
