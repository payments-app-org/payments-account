--liquibase formatted sql

--changeset account:010-demo-incomes
-- Поступления для демонстрации доходов Артура (операции — в history, 010-demo-incomes):
-- зарплата и оплата проекта — на Зарплатную, дивиденды — на Накопительную, переводы клиентов — в кошелёк
UPDATE accounts SET balance = balance + 312847.63 + 145300.00 WHERE id = '00000000-0000-0000-0000-00000000a002';
UPDATE accounts SET balance = balance + 500000.00             WHERE id = '00000000-0000-0000-0000-00000000a004';
UPDATE accounts SET balance = balance + 18500.00 + 27350.00   WHERE id = '00000000-0000-0000-0000-00000000a001';
