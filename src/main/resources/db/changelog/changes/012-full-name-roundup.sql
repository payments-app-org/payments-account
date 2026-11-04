--liquibase formatted sql

--changeset account:012-full-name-roundup
-- Полная фамилия тестового пользователя: в формах подставляется «Руднев Артур Константинович», а не «Р Артур …»
UPDATE users SET last_name = 'Руднев' WHERE id = '00000000-0000-0000-0000-000000000001';

-- «Округление на пользу»: шаг округления покупок и фонд, куда уходит разница. Пусто — округление выключено.
ALTER TABLE users ADD COLUMN roundup_step       INTEGER CHECK (roundup_step IN (10, 100, 1000));
ALTER TABLE users ADD COLUMN roundup_fund_code  VARCHAR(40);
ALTER TABLE users ADD COLUMN roundup_fund_name  VARCHAR(150);
ALTER TABLE users ADD COLUMN roundup_enabled_at TIMESTAMPTZ;
ALTER TABLE users ADD CONSTRAINT users_roundup_complete CHECK
    ((roundup_step IS NULL) = (roundup_fund_code IS NULL) AND (roundup_step IS NULL) = (roundup_fund_name IS NULL));
