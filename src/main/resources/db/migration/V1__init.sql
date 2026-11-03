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

CREATE TABLE accounts (
    id                UUID PRIMARY KEY,
    user_id           UUID          NOT NULL REFERENCES users (id),
    type              VARCHAR(20)   NOT NULL CHECK (type IN ('WALLET', 'CARD')),
    title             VARCHAR(100)  NOT NULL,
    balance           NUMERIC(19, 2) NOT NULL DEFAULT 0,
    currency          VARCHAR(3)    NOT NULL DEFAULT 'RUB',
    card_last4        VARCHAR(4),
    payment_system    VARCHAR(20),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (type <> 'CARD' OR card_last4 IS NOT NULL)
);

CREATE INDEX accounts_user_id_idx ON accounts (user_id);
-- «Мой кошелёк» у пользователя ровно один
CREATE UNIQUE INDEX accounts_one_wallet_per_user ON accounts (user_id) WHERE type = 'WALLET';

-- Лимиты на операции; правила наполним, когда появятся требования
CREATE TABLE limits (
    id                UUID PRIMARY KEY,
    user_id           UUID           NOT NULL REFERENCES users (id),
    operation_type    VARCHAR(30)    NOT NULL,
    period            VARCHAR(20)    NOT NULL CHECK (period IN ('SINGLE', 'DAY', 'MONTH')),
    max_amount        NUMERIC(19, 2) NOT NULL,
    UNIQUE (user_id, operation_type, period)
);
