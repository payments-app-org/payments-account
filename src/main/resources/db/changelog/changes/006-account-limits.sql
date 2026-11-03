--liquibase formatted sql

--changeset account:006-account-limits
-- Лимиты счёта (в дополнение к лимитам пользователя на вид операции из таблицы limits).
-- NULL — без ограничения.
CREATE TABLE account_limits (
    account_id            UUID PRIMARY KEY REFERENCES accounts (id),
    -- Максимальная сумма одного платежа со счёта (вместе с комиссией)
    max_payment           NUMERIC(19, 2),
    -- Максимальный остаток: входящий перевод, после которого остаток превысит лимит, не примем
    max_balance           NUMERIC(19, 2),
    -- Максимальная сумма списаний со счёта за календарный месяц
    max_monthly_turnover  NUMERIC(19, 2)
);

-- Кошельки — как в условиях «Моего кошелька»; карты — с запасом
INSERT INTO account_limits (account_id, max_payment, max_balance, max_monthly_turnover)
SELECT id,
       CASE type WHEN 'WALLET' THEN 600000 ELSE 1000000 END,
       CASE type WHEN 'WALLET' THEN 600000 ELSE NULL END,
       CASE type WHEN 'WALLET' THEN 1200000 ELSE 3000000 END
FROM accounts;
