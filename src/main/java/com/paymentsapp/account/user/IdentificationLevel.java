package com.paymentsapp.account.user;

// Уровень идентификации клиента (115-ФЗ, 161-ФЗ). Лимиты кошелька — identification_limits,
// запреты операций — identification_restrictions.
public enum IdentificationLevel {
    // Только номер телефона
    ANONYMOUS,
    // Паспорт и СНИЛС/ИНН, подтверждённые через Госуслуги или договор связи
    SIMPLIFIED,
    // Офис, салон связи или Единая биометрическая система
    FULL
}
