--liquibase formatted sql

--changeset account:018-account-operations
-- Журнал движений денег по счетам. Баланс счёта = сумма его записей: так баланс можно сверить, а входящее
-- зачисление (у него нет резерва) оставляет след. Уникальный индекс — одна операция платежа по счёту один раз:
-- повтор списания или зачисления с тем же paymentId не пройдёт.
CREATE TABLE account_operations (
    id             UUID PRIMARY KEY,
    account_id     UUID           NOT NULL REFERENCES accounts (id),
    payment_id     UUID,
    kind           VARCHAR(10)    NOT NULL CHECK (kind IN ('OPENING', 'DEBIT', 'CREDIT')),
    amount         NUMERIC(19, 2) NOT NULL CHECK (amount >= 0),
    balance_after  NUMERIC(19, 2) NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (kind = 'OPENING' OR payment_id IS NOT NULL)
);
CREATE UNIQUE INDEX account_operations_once_uq ON account_operations (payment_id, account_id, kind) WHERE payment_id IS NOT NULL;
CREATE INDEX account_operations_account_idx ON account_operations (account_id, created_at);

-- Начальный остаток: деньги, которые были на счетах до появления журнала (демо-данные)
INSERT INTO account_operations (id, account_id, kind, amount, balance_after)
SELECT gen_random_uuid(), id, 'OPENING', balance, balance FROM accounts WHERE type IN ('WALLET', 'CARD');

--changeset account:018-card-key-ids
-- Каким ключом посчитан отпечаток и зашифрован номер: ключи можно менять (ротация), старые записи
-- перешифровываются при старте (CardKeyRotation), отпечатки карт других банков — при следующей встрече с картой
ALTER TABLE accounts ADD COLUMN card_key_id VARCHAR(20);
ALTER TABLE accounts ADD COLUMN fingerprint_key_id VARCHAR(20);
UPDATE accounts SET card_key_id = 'v1' WHERE card_number_hash IS NOT NULL;
UPDATE accounts SET fingerprint_key_id = 'v1' WHERE card_fingerprint IS NOT NULL;

--changeset account:018-drop-outbox
-- События резервов (account.reservations) никто не читал: outbox в account больше не нужен
DROP TABLE outbox;
