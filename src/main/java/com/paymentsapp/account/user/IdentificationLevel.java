package com.paymentsapp.account.user;

import java.math.BigDecimal;
import java.util.Set;

// Уровень идентификации клиента. От него зависят лимиты «Моего кошелька» и доступные операции.
// Лимиты кошелька: платёж (вместе с комиссией), остаток, оборот за календарный месяц; null — без ограничения.
public enum IdentificationLevel {

    // Только номер телефона
    ANONYMOUS(null, 15_000, 40_000),
    // Паспорт и СНИЛС/ИНН, подтверждённые через Госуслуги или договор связи
    SIMPLIFIED(60_000, 60_000, 200_000),
    // Офис, салон связи или Единая биометрическая система
    FULL(600_000, 600_000, 1_200_000);

    // Переводы людям: по номеру телефона, по карте, на чужой кошелёк
    private static final Set<String> PERSON_TRANSFERS = Set.of("SBP_PHONE", "CARD2CARD", "WALLET_TRANSFER");

    private final BigDecimal walletMaxPayment;
    private final BigDecimal walletMaxBalance;
    private final BigDecimal walletMaxMonthlyTurnover;

    IdentificationLevel(Integer maxPayment, int maxBalance, int maxMonthlyTurnover) {
        this.walletMaxPayment = maxPayment == null ? null : BigDecimal.valueOf(maxPayment);
        this.walletMaxBalance = BigDecimal.valueOf(maxBalance);
        this.walletMaxMonthlyTurnover = BigDecimal.valueOf(maxMonthlyTurnover);
    }

    public BigDecimal walletMaxPayment() { return walletMaxPayment; }
    public BigDecimal walletMaxBalance() { return walletMaxBalance; }
    public BigDecimal walletMaxMonthlyTurnover() { return walletMaxMonthlyTurnover; }

    // Почему операция недоступна на этом уровне; null — доступна.
    // providerGroup — группа поставщика для оплаты через biller (BILL)
    public String unavailableReason(String operationType, String providerGroup) {
        boolean abroad = "ABROAD".equals(operationType)
                || "BILL".equals(operationType) && "FOREIGN_MOBILE".equals(providerGroup);
        if (abroad && this != FULL) {
            return "Переводы и платежи за рубеж доступны только с полной идентификацией";
        }
        if (this != ANONYMOUS) {
            return null;
        }
        if (PERSON_TRANSFERS.contains(operationType)) {
            return "Переводы другим людям недоступны без идентификации. Пройдите упрощённую идентификацию";
        }
        if ("BILL".equals(operationType) && "EMONEY".equals(providerGroup)) {
            return "Пополнение чужих электронных кошельков недоступно без идентификации";
        }
        return null;
    }
}
