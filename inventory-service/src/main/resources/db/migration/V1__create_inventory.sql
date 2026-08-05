CREATE TABLE inventory_items (
    sku VARCHAR(64) PRIMARY KEY,
    on_hand_quantity BIGINT NOT NULL,
    reserved_quantity BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_inventory_on_hand_non_negative CHECK (on_hand_quantity >= 0),
    CONSTRAINT ck_inventory_reserved_non_negative CHECK (reserved_quantity >= 0),
    CONSTRAINT ck_inventory_reservations_within_stock CHECK (
        reserved_quantity <= on_hand_quantity
    )
);

CREATE TABLE inventory_changes (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL,
    previous_on_hand_quantity BIGINT,
    new_on_hand_quantity BIGINT NOT NULL,
    reason VARCHAR(128) NOT NULL,
    source_reference VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_inventory_changes_item
        FOREIGN KEY (sku) REFERENCES inventory_items (sku),
    CONSTRAINT ck_inventory_change_new_quantity CHECK (new_on_hand_quantity >= 0)
);

CREATE INDEX idx_inventory_changes_sku_created
    ON inventory_changes (sku, created_at DESC);

CREATE INDEX idx_inventory_low_availability
    ON inventory_items ((on_hand_quantity - reserved_quantity));
