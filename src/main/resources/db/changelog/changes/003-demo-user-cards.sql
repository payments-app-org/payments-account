--liquibase formatted sql

--changeset account:003-demo-user-cards
-- Ещё две карты демо-пользователю: всего у него кошелёк и три карты
INSERT INTO accounts (id, user_id, type, title, balance, card_number, card_last4, payment_system, created_at) VALUES
    ('00000000-0000-0000-0000-00000000a003', '00000000-0000-0000-0000-000000000001', 'CARD', 'Кредитная',  50000.00, '5400123409871006', '1006', 'MASTERCARD', now() + interval '1 second'),
    ('00000000-0000-0000-0000-00000000a004', '00000000-0000-0000-0000-000000000001', 'CARD', 'Накопительная', 25000.00, '2200700155551004', '1004', 'MIR', now() + interval '2 seconds');
