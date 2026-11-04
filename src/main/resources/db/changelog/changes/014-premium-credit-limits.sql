--liquibase formatted sql

--changeset account:014-premium-rename
-- «МТС Premium» → «Премиум»
ALTER TABLE users DROP CONSTRAINT users_subscription_check;
UPDATE users SET subscription = 'PREMIUM' WHERE subscription = 'MTS_PREMIUM';
ALTER TABLE users ADD CONSTRAINT users_subscription_check CHECK (subscription IN ('NONE', 'PREMIUM', 'BANK_PREMIUM'));

--changeset account:014-credit-cards
-- Кредитная карта: с неё нельзя пополнять электронные кошельки
ALTER TABLE accounts ADD COLUMN credit BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE accounts ADD CONSTRAINT accounts_credit_is_card CHECK (NOT credit OR type = 'CARD');
UPDATE accounts SET credit = true WHERE type = 'CARD' AND title = 'Кредитная';

--changeset account:014-transfer-limits
-- По СБП, по номеру карты и по реквизитам: до 1 000 000 ₽ за операцию и 3 000 000 ₽ в месяц, дневного лимита нет
DELETE FROM limits WHERE operation_type IN ('SBP_PHONE', 'CARD2CARD', 'REQUISITES', 'REQUISITES_OUR');
INSERT INTO limits (id, user_id, operation_type, period, max_amount)
SELECT gen_random_uuid(), u.id, l.operation_type, l.period, l.max_amount
FROM users u
CROSS JOIN (VALUES
    ('SBP_PHONE',      'SINGLE', 1000000.00), ('SBP_PHONE',      'MONTH', 3000000.00),
    ('CARD2CARD',      'SINGLE', 1000000.00), ('CARD2CARD',      'MONTH', 3000000.00),
    ('REQUISITES',     'SINGLE', 1000000.00), ('REQUISITES',     'MONTH', 3000000.00),
    ('REQUISITES_OUR', 'SINGLE', 1000000.00), ('REQUISITES_OUR', 'MONTH', 3000000.00)
) AS l (operation_type, period, max_amount);
