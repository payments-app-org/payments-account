--liquibase formatted sql

--changeset account:011-sbp-sources
-- «Ваш счёт в другом банке» для автопополнения по СБП (me2me): как привязанная карта, только без карты —
-- банк-участник СБП и номер телефона владельца. Деньги с него забирает процессинг, баланс мы не ведём.
ALTER TABLE accounts DROP CONSTRAINT accounts_type_check;
ALTER TABLE accounts ADD CONSTRAINT accounts_type_check CHECK (type IN ('WALLET', 'CARD', 'EXTERNAL_CARD', 'EXTERNAL_SBP'));
ALTER TABLE accounts DROP CONSTRAINT accounts_card_last4_check;
ALTER TABLE accounts ADD CONSTRAINT accounts_card_last4_check CHECK (type IN ('WALLET', 'EXTERNAL_SBP') OR card_last4 IS NOT NULL);
ALTER TABLE accounts ADD COLUMN sbp_bank_id VARCHAR(12);
ALTER TABLE accounts ADD CONSTRAINT accounts_sbp_bank_check CHECK (type <> 'EXTERNAL_SBP' OR sbp_bank_id IS NOT NULL);
CREATE UNIQUE INDEX accounts_one_sbp_source_per_bank ON accounts (user_id, sbp_bank_id) WHERE type = 'EXTERNAL_SBP';
