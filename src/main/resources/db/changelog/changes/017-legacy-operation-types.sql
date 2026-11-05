--liquibase formatted sql

--changeset account:017-legacy-operation-types
-- До объединения оплат поставщикам в BILL резервы создавались с MOBILE_PAYMENT и UIN_PAYMENT (payment уже
-- перевёл свои платежи на BILL). Приводим к BILL и запрещаем значения вне перечня OperationType.
UPDATE reservations SET operation_type = 'BILL' WHERE operation_type IN ('MOBILE_PAYMENT', 'UIN_PAYMENT');

INSERT INTO operation_usage (user_id, operation_type, source, period, period_start, amount)
SELECT user_id, 'BILL', source, period, period_start, sum(amount)
FROM operation_usage
WHERE operation_type IN ('MOBILE_PAYMENT', 'UIN_PAYMENT')
GROUP BY user_id, source, period, period_start
ON CONFLICT (user_id, operation_type, source, period, period_start)
DO UPDATE SET amount = operation_usage.amount + excluded.amount;
DELETE FROM operation_usage WHERE operation_type IN ('MOBILE_PAYMENT', 'UIN_PAYMENT');

ALTER TABLE reservations ADD CONSTRAINT reservations_operation_type_check CHECK (operation_type IN
    ('SBP_PHONE', 'CARD2CARD', 'OWN_TRANSFER', 'ABROAD', 'QR', 'WALLET_TRANSFER', 'REQUISITES', 'REQUISITES_OUR', 'BILL'));
