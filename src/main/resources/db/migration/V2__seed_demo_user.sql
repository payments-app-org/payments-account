-- Демо-пользователь: пока нет авторизации, фронтенд работает от его имени
INSERT INTO users (id, first_name, last_name, middle_name, phone, email, subscription)
VALUES ('00000000-0000-0000-0000-000000000001', 'Артур', 'Р', 'Константинович', '+79964480000', NULL, 'NONE');

INSERT INTO accounts (id, user_id, type, title, balance, card_last4, payment_system) VALUES
    ('00000000-0000-0000-0000-00000000a001', '00000000-0000-0000-0000-000000000001', 'WALLET', 'Мой кошелёк', 0.00, NULL, NULL),
    ('00000000-0000-0000-0000-00000000a002', '00000000-0000-0000-0000-000000000001', 'CARD', 'Зарплатная WEEKEND', 183.00, '3752', 'MIR');
