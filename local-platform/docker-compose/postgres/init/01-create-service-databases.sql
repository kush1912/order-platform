CREATE USER order_service WITH PASSWORD 'order-local-password';
CREATE DATABASE order_db OWNER order_service;

CREATE USER inventory_service WITH PASSWORD 'inventory-local-password';
CREATE DATABASE inventory_db OWNER inventory_service;

CREATE USER notification_service WITH PASSWORD 'notification-local-password';
CREATE DATABASE notification_db OWNER notification_service;
