--liquibase formatted sql

--changeset account:007-linked-cards
-- Привязанная карта другого банка (EXTERNAL_CARD): источник для переводов, деньги с неё забирает процессинг.
-- Баланс её нам неизвестен — balance/reserved не используются. Полный номер и CVC не храним:
-- только последние 4 цифры, срок и отпечаток номера (SHA-256), чтобы не привязать одну карту дважды.
ALTER TABLE accounts DROP CONSTRAINT accounts_type_check;
ALTER TABLE accounts ADD CONSTRAINT accounts_type_check CHECK (type IN ('WALLET', 'CARD', 'EXTERNAL_CARD'));
ALTER TABLE accounts DROP CONSTRAINT accounts_check1;
ALTER TABLE accounts ADD CONSTRAINT accounts_card_last4_check CHECK (type = 'WALLET' OR card_last4 IS NOT NULL);
ALTER TABLE accounts ADD COLUMN card_expiry VARCHAR(5);
ALTER TABLE accounts ADD COLUMN card_fingerprint VARCHAR(64);
CREATE UNIQUE INDEX accounts_card_fingerprint_uq ON accounts (user_id, card_fingerprint) WHERE card_fingerprint IS NOT NULL;

--changeset account:007-payment-default
-- Инструмент оплаты по умолчанию («сердечко»): подставляется в «Списать с» во всех переводах.
-- Не путать с картой по умолчанию для входящих переводов (accounts.is_default).
ALTER TABLE users ADD COLUMN default_payment_account_id UUID REFERENCES accounts (id);
-- Уровень идентификации владельца кошелька
ALTER TABLE users ADD COLUMN identification_level VARCHAR(20) NOT NULL DEFAULT 'FULL'
    CHECK (identification_level IN ('ANONYMOUS', 'SIMPLIFIED', 'FULL'));
