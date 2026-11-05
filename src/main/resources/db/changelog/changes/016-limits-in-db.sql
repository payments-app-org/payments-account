--liquibase formatted sql

--changeset account:016-identification-limits
-- Лимиты «Моего кошелька» по уровню идентификации (161-ФЗ, «Памятка об электронных денежных средствах»).
-- Раньше были в коде (IdentificationLevel). NULL — без ограничения.
CREATE TABLE identification_limits (
    level                        VARCHAR(20) PRIMARY KEY CHECK (level IN ('ANONYMOUS', 'SIMPLIFIED', 'FULL')),
    wallet_max_payment           NUMERIC(19, 2),
    wallet_max_balance           NUMERIC(19, 2) NOT NULL,
    wallet_max_monthly_turnover  NUMERIC(19, 2) NOT NULL,
    -- Без идентификации кошелёк не принимает переводы от других людей
    receives_transfers           BOOLEAN        NOT NULL
);
INSERT INTO identification_limits VALUES
    ('ANONYMOUS',  NULL,      15000,  40000,   false),
    ('SIMPLIFIED', 60000,     60000,  200000,  true),
    ('FULL',       600000,    600000, 1200000, true);

--changeset account:016-identification-restrictions
-- Что запрещено на уровне идентификации. provider_group — для BILL, source — только при списании
-- с кошелька (WALLET) или с карты (CARD); NULL — любой. error_code — константа AccountError с текстом отказа.
CREATE TABLE identification_restrictions (
    id              SERIAL PRIMARY KEY,
    level           VARCHAR(20) NOT NULL REFERENCES identification_limits (level),
    operation_type  VARCHAR(30) NOT NULL,
    provider_group  VARCHAR(30),
    source          VARCHAR(10) CHECK (source IN ('WALLET', 'CARD')),
    error_code      VARCHAR(50) NOT NULL
);
INSERT INTO identification_restrictions (level, operation_type, provider_group, source, error_code) VALUES
    ('ANONYMOUS',  'SBP_PHONE',       NULL,             NULL,     'PERSON_TRANSFER_NOT_IDENTIFIED'),
    ('ANONYMOUS',  'CARD2CARD',       NULL,             NULL,     'PERSON_TRANSFER_NOT_IDENTIFIED'),
    ('ANONYMOUS',  'WALLET_TRANSFER', NULL,             NULL,     'PERSON_TRANSFER_NOT_IDENTIFIED'),
    ('ANONYMOUS',  'ABROAD',          NULL,             NULL,     'ABROAD_NOT_IDENTIFIED'),
    ('ANONYMOUS',  'BILL',            'FOREIGN_MOBILE', NULL,     'ABROAD_NOT_IDENTIFIED'),
    ('ANONYMOUS',  'BILL',            'EMONEY',         NULL,     'EMONEY_NOT_IDENTIFIED'),
    ('SIMPLIFIED', 'WALLET_TRANSFER', NULL,             'WALLET', 'EMONEY_REQUIRES_FULL'),
    ('SIMPLIFIED', 'BILL',            'EMONEY',         'WALLET', 'EMONEY_REQUIRES_FULL');

--changeset account:016-operation-limits
-- Лимиты на вид операции — одинаковы для всех клиентов (раньше копия на каждого в limits и константы в коде).
-- source: ANY — любой источник, WALLET — с кошелька (без открытия счёта), CARD — со счёта / карты.
CREATE TABLE operation_limits (
    id              SERIAL PRIMARY KEY,
    operation_type  VARCHAR(30)    NOT NULL,
    source          VARCHAR(10)    NOT NULL CHECK (source IN ('ANY', 'WALLET', 'CARD')),
    period          VARCHAR(10)    NOT NULL CHECK (period IN ('SINGLE', 'DAY', 'MONTH')),
    min_amount      NUMERIC(19, 2) CHECK (min_amount IS NULL OR period = 'SINGLE'),
    max_amount      NUMERIC(19, 2) NOT NULL,
    UNIQUE (operation_type, source, period)
);
INSERT INTO operation_limits (operation_type, source, period, min_amount, max_amount) VALUES
    -- «Переводы за рубеж», раздел о СБП, карте и реквизитах: от 10 до 1 000 000 ₽ за операцию, 3 000 000 ₽ в месяц
    ('SBP_PHONE',      'ANY',    'SINGLE', 10,   1000000),
    ('SBP_PHONE',      'ANY',    'MONTH',  NULL, 3000000),
    ('CARD2CARD',      'ANY',    'SINGLE', 10,   1000000),
    ('CARD2CARD',      'ANY',    'MONTH',  NULL, 3000000),
    ('REQUISITES',     'ANY',    'SINGLE', 10,   1000000),
    ('REQUISITES',     'ANY',    'MONTH',  NULL, 3000000),
    ('REQUISITES_OUR', 'ANY',    'SINGLE', 10,   1000000),
    ('REQUISITES_OUR', 'ANY',    'MONTH',  NULL, 3000000),
    -- За рубеж: без открытия счёта (кошелёк) и со счёта (карта)
    ('ABROAD',         'WALLET', 'SINGLE', NULL, 300000),
    ('ABROAD',         'WALLET', 'MONTH',  NULL, 400000),
    ('ABROAD',         'CARD',   'SINGLE', NULL, 400000),
    ('ABROAD',         'CARD',   'MONTH',  NULL, 3000000);
DROP TABLE limits;

--changeset account:016-account-limit-defaults
-- Лимиты нового счёта по типу (раньше константы в CardLinkingService)
CREATE TABLE account_limit_defaults (
    account_type          VARCHAR(20) PRIMARY KEY,
    max_payment           NUMERIC(19, 2),
    max_balance           NUMERIC(19, 2),
    max_monthly_turnover  NUMERIC(19, 2)
);
INSERT INTO account_limit_defaults VALUES ('EXTERNAL_CARD', 100000, NULL, 300000);

--changeset account:016-usage-counters
-- Счётчики израсходованных лимитов: проверка лимита — чтение одной строки, а не SUM по всем резервам периода
CREATE TABLE operation_usage (
    user_id         UUID           NOT NULL REFERENCES users (id),
    operation_type  VARCHAR(30)    NOT NULL,
    source          VARCHAR(10)    NOT NULL CHECK (source IN ('WALLET', 'CARD')),
    period          VARCHAR(10)    NOT NULL CHECK (period IN ('DAY', 'MONTH')),
    period_start    DATE           NOT NULL,
    amount          NUMERIC(19, 2) NOT NULL,
    PRIMARY KEY (user_id, operation_type, source, period, period_start)
);
CREATE TABLE account_usage (
    account_id    UUID           NOT NULL REFERENCES accounts (id),
    period_start  DATE           NOT NULL,
    amount        NUMERIC(19, 2) NOT NULL,
    PRIMARY KEY (account_id, period_start)
);
-- Заполняем по уже существующим резервам (снятые не считаются); периоды — по Москве
INSERT INTO operation_usage (user_id, operation_type, source, period, period_start, amount)
SELECT r.user_id, r.operation_type, CASE WHEN a.type = 'WALLET' THEN 'WALLET' ELSE 'CARD' END, 'DAY',
       (r.created_at AT TIME ZONE 'Europe/Moscow')::date, sum(r.amount)
FROM reservations r JOIN accounts a ON a.id = r.account_id
WHERE r.status <> 'RELEASED'
GROUP BY 1, 2, 3, 5;
INSERT INTO operation_usage (user_id, operation_type, source, period, period_start, amount)
SELECT r.user_id, r.operation_type, CASE WHEN a.type = 'WALLET' THEN 'WALLET' ELSE 'CARD' END, 'MONTH',
       date_trunc('month', r.created_at AT TIME ZONE 'Europe/Moscow')::date, sum(r.amount)
FROM reservations r JOIN accounts a ON a.id = r.account_id
WHERE r.status <> 'RELEASED'
GROUP BY 1, 2, 3, 5;
INSERT INTO account_usage (account_id, period_start, amount)
SELECT r.account_id, date_trunc('month', r.created_at AT TIME ZONE 'Europe/Moscow')::date, sum(r.total)
FROM reservations r
WHERE r.status <> 'RELEASED'
GROUP BY 1, 2;
DROP INDEX IF EXISTS reservations_limits_idx;

--changeset account:016-card-number-protection
-- Номер нашей карты: HMAC (поиск) и AES-GCM шифротекст; открытый номер стирает CardNumberMigration при старте
ALTER TABLE accounts ADD COLUMN card_number_hash VARCHAR(64);
ALTER TABLE accounts ADD COLUMN card_number_encrypted TEXT;
CREATE UNIQUE INDEX accounts_card_number_hash_uq ON accounts (card_number_hash) WHERE card_number_hash IS NOT NULL;
