--liquibase formatted sql

--changeset account:013-subscriptions
-- Подписки как в банке: «МТС Premium» (развлекательная) и пакет услуг «МТС Банк Premium»
ALTER TABLE users DROP CONSTRAINT users_subscription_check;
UPDATE users SET subscription = CASE subscription WHEN 'PREMIUM' THEN 'MTS_PREMIUM' WHEN 'PREMIUM_PLUS' THEN 'BANK_PREMIUM' ELSE subscription END;
ALTER TABLE users ADD CONSTRAINT users_subscription_check CHECK (subscription IN ('NONE', 'MTS_PREMIUM', 'BANK_PREMIUM'));

--changeset account:013-wallet-limits-by-identification
-- Лимиты «Моего кошелька» теперь задаёт уровень идентификации владельца (IdentificationLevel), а не account_limits
DELETE FROM account_limits WHERE account_id IN (SELECT id FROM accounts WHERE type = 'WALLET');

--changeset account:013-identification-test-users
-- Клиенты без полной идентификации: у них только «Мой кошелёк»
INSERT INTO users (id, first_name, last_name, middle_name, phone, email, subscription, sbp_default_bank_id, identification_level)
VALUES ('00000000-0000-0000-0000-000000000005', 'Анна', 'Ковалёва', NULL, '+79770001122', NULL, 'NONE', '100000000004', 'ANONYMOUS'),
       ('00000000-0000-0000-0000-000000000006', 'Пётр', 'Соколов', 'Андреевич', '+79850003344', NULL, 'NONE', '100000000004', 'SIMPLIFIED');

INSERT INTO accounts (id, user_id, type, title, balance, account_number)
VALUES ('00000000-0000-0000-0000-00000000e001', '00000000-0000-0000-0000-000000000005', 'WALLET', 'Мой кошелёк', 12000, '40914810700000000051'),
       ('00000000-0000-0000-0000-00000000f001', '00000000-0000-0000-0000-000000000006', 'WALLET', 'Мой кошелёк', 50000, '40914810700000000062');
