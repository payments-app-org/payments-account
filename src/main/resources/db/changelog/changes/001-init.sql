--liquibase formatted sql

--changeset account:001-users
CREATE TABLE users (
    id                UUID PRIMARY KEY,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    middle_name       VARCHAR(100),
    phone             VARCHAR(20)  NOT NULL UNIQUE,
    email             VARCHAR(255),
    subscription      VARCHAR(20)  NOT NULL DEFAULT 'NONE'
        CHECK (subscription IN ('NONE', 'PREMIUM', 'PREMIUM_PLUS')),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

--changeset account:001-accounts
-- balance — реальные деньги на счёте, reserved — заблокированные под платежи в процессе.
-- Доступно к списанию: balance - reserved.
CREATE TABLE accounts (
    id                UUID PRIMARY KEY,
    user_id           UUID           NOT NULL REFERENCES users (id),
    type              VARCHAR(20)    NOT NULL CHECK (type IN ('WALLET', 'CARD')),
    status            VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'BLOCKED')),
    title             VARCHAR(100)   NOT NULL,
    balance           NUMERIC(19, 2) NOT NULL DEFAULT 0,
    reserved          NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    currency          VARCHAR(3)     NOT NULL DEFAULT 'RUB',
    -- Полный номер карты храним только в демо; в проде он живёт в токенизированном хранилище
    card_number       VARCHAR(19),
    card_last4        VARCHAR(4),
    payment_system    VARCHAR(20),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (balance >= reserved),
    CHECK (type <> 'CARD' OR card_last4 IS NOT NULL)
);

CREATE INDEX accounts_user_id_idx ON accounts (user_id);
-- «Мой кошелёк» у пользователя ровно один
CREATE UNIQUE INDEX accounts_one_wallet_per_user ON accounts (user_id) WHERE type = 'WALLET';

--changeset account:001-limits
-- Лимит на сумму операций одного вида: на одну операцию, за день или за месяц.
-- Нет строки — нет лимита.
CREATE TABLE limits (
    id                UUID PRIMARY KEY,
    user_id           UUID           NOT NULL REFERENCES users (id),
    operation_type    VARCHAR(30)    NOT NULL,
    period            VARCHAR(20)    NOT NULL CHECK (period IN ('SINGLE', 'DAY', 'MONTH')),
    max_amount        NUMERIC(19, 2) NOT NULL,
    UNIQUE (user_id, operation_type, period)
);

--changeset account:001-reservations
-- Резервирование средств под платёж. Один платёж — одно резервирование.
-- Израсходованный лимит считается по резервированиям в статусах RESERVED и CAPTURED.
CREATE TABLE reservations (
    id                UUID PRIMARY KEY,
    payment_id        UUID           NOT NULL UNIQUE,
    user_id           UUID           NOT NULL REFERENCES users (id),
    account_id        UUID           NOT NULL REFERENCES accounts (id),
    operation_type    VARCHAR(30)    NOT NULL,
    amount            NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    commission        NUMERIC(19, 2) NOT NULL CHECK (commission >= 0),
    total             NUMERIC(19, 2) NOT NULL,
    status            VARCHAR(20)    NOT NULL CHECK (status IN ('RESERVED', 'RELEASED', 'CAPTURED')),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX reservations_limits_idx ON reservations (user_id, operation_type, created_at) WHERE status <> 'RELEASED';

--changeset account:001-outbox
-- События пишутся в одной транзакции с изменением данных, в Kafka их отправляет планировщик
CREATE TABLE outbox (
    id                UUID PRIMARY KEY,
    topic             VARCHAR(100) NOT NULL,
    aggregate_id      VARCHAR(100) NOT NULL,
    event_type        VARCHAR(50)  NOT NULL,
    payload           JSONB        NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at      TIMESTAMPTZ
);

CREATE INDEX outbox_unpublished_idx ON outbox (created_at) WHERE published_at IS NULL;
