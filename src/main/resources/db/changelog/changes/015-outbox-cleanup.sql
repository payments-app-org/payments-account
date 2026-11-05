--liquibase formatted sql

--changeset account:015-outbox-cleanup
-- Очистка outbox (libs/platform OutboxCleanup) удаляет отправленные события старше retention
CREATE INDEX outbox_published_idx ON outbox (published_at) WHERE published_at IS NOT NULL;
