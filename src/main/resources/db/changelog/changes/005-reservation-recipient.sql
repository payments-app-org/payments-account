--liquibase formatted sql

--changeset account:005-reservation-recipient
-- Если получатель — наш клиент, при списании деньги зачисляются ему на эту карту в той же транзакции
ALTER TABLE reservations ADD COLUMN recipient_account_id UUID REFERENCES accounts (id);
