--liquibase formatted sql

--changeset account:009-account-numbers
-- Номер счёта (20 цифр) для справок по счёту. 40914 — балансовый счёт электронных денег, 810 — рубли.
ALTER TABLE accounts ADD COLUMN account_number VARCHAR(20);
UPDATE accounts SET account_number = '40914810' || lpad((abs(hashtext(id::text))::bigint * 1000 + abs(hashtext(user_id::text)) % 1000)::text, 12, '0')
WHERE type <> 'EXTERNAL_CARD';
CREATE UNIQUE INDEX accounts_account_number_uq ON accounts (account_number) WHERE account_number IS NOT NULL;
