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
    // providerGroup — группа поставщика для оплаты через biller (BILL); fromWallet — списание с «Моего кошелька».
    // Правила — из «Памятки об электронных денежных средствах» и «Переводов за рубеж».
    public String unavailableReason(String operationType, String providerGroup, boolean fromWallet) {
        boolean bill = "BILL".equals(operationType);
        boolean abroad = "ABROAD".equals(operationType) || bill && "FOREIGN_MOBILE".equals(providerGroup);
        boolean toEmoney = "WALLET_TRANSFER".equals(operationType) || bill && "EMONEY".equals(providerGroup);
        if (this == ANONYMOUS) {
            // Без идентификации платить можно только юрлицам и ИП в России
            if (PERSON_TRANSFERS.contains(operationType)) {
                return "Переводы другим людям недоступны без идентификации. Пройдите упрощённую идентификацию";
            }
            if (abroad) {
                return "Переводы и платежи за рубеж недоступны без идентификации";
            }
            if (toEmoney) {
                return "Пополнение чужих электронных кошельков недоступно без идентификации";
            }
        }
        // Перевод электронных денег без банковского счёта (на другой кошелёк) — только с полной идентификацией
        if (this == SIMPLIFIED && fromWallet && toEmoney) {
            return "Переводы с кошелька на другие электронные кошельки доступны только с полной идентификацией";
        }
        return null;
    }

    // Без идентификации кошелёк не принимает переводы от других людей — только со своих счетов и от юрлиц
    public boolean receivesFromOthers() {
        return this != ANONYMOUS;
    }
}
