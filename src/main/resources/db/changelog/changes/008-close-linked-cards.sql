--liquibase formatted sql

--changeset account:008-close-linked-cards
-- Отвязанную карту не удаляем (на неё ссылаются резервы и платежи), а закрываем: CLOSED не показывается и не используется.
-- Ту же карту можно привязать заново — отпечаток уникален только среди незакрытых.
ALTER TABLE accounts DROP CONSTRAINT accounts_status_check;
ALTER TABLE accounts ADD CONSTRAINT accounts_status_check CHECK (status IN ('ACTIVE', 'BLOCKED', 'CLOSED'));
DROP INDEX accounts_card_fingerprint_uq;
CREATE UNIQUE INDEX accounts_card_fingerprint_uq ON accounts (user_id, card_fingerprint)
    WHERE card_fingerprint IS NOT NULL AND status <> 'CLOSED';
