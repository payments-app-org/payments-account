--liquibase formatted sql

--changeset account:004-sbp-default-bank
-- Банк по умолчанию для входящих переводов по СБП: подставляется, когда отправитель не выбрал банк из списка
ALTER TABLE users ADD COLUMN sbp_default_bank_id VARCHAR(12);
UPDATE users SET sbp_default_bank_id = '100000000004' WHERE id = '00000000-0000-0000-0000-000000000001'; -- Т-Банк
UPDATE users SET sbp_default_bank_id = '100000000111' WHERE id = '00000000-0000-0000-0000-000000000002'; -- Сбербанк
UPDATE users SET sbp_default_bank_id = '100000000008' WHERE id = '00000000-0000-0000-0000-000000000003'; -- Альфа-Банк
UPDATE users SET sbp_default_bank_id = '100000000005' WHERE id = '00000000-0000-0000-0000-000000000004'; -- ВТБ
ALTER TABLE users ALTER COLUMN sbp_default_bank_id SET NOT NULL;

--changeset account:004-more-cards
-- Марии — ещё две карты (всего три), Ольге — первая карта: принимать переводы можно только на карту
INSERT INTO accounts (id, user_id, type, title, balance, card_number, card_last4, payment_system, created_at) VALUES
    ('00000000-0000-0000-0000-00000000b003', '00000000-0000-0000-0000-000000000002', 'CARD', 'Зарплатная', 120000.00, '2200300456792009', '2009', 'MIR',  now() + interval '1 second'),
    ('00000000-0000-0000-0000-00000000b004', '00000000-0000-0000-0000-000000000002', 'CARD', 'Travel',      15000.00, '4790123456783006', '3006', 'VISA', now() + interval '2 seconds'),
    ('00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-000000000004', 'CARD', 'Дебетовая',   12000.00, '2202200012344006', '4006', 'MIR',  now());

--changeset account:004-default-card
-- Карта по умолчанию: на неё приходят переводы по номеру телефона. У пользователя она одна, и это всегда карта.
ALTER TABLE accounts ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE accounts ADD CONSTRAINT accounts_default_is_card CHECK (NOT is_default OR type = 'CARD');
CREATE UNIQUE INDEX accounts_one_default_per_user ON accounts (user_id) WHERE is_default;
CREATE UNIQUE INDEX accounts_card_number_uq ON accounts (card_number) WHERE card_number IS NOT NULL;
UPDATE accounts SET is_default = true WHERE id IN (
    '00000000-0000-0000-0000-00000000a002',  -- Артур: Зарплатная WEEKEND ·· 3752
    '00000000-0000-0000-0000-00000000b002',  -- Мария: Дебетовая ·· 1002
    '00000000-0000-0000-0000-00000000c002',  -- Иван: Премиальная ·· 1005
    '00000000-0000-0000-0000-00000000d002'   -- Ольга: Дебетовая ·· 4006
);
