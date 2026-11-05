package com.paymentsapp.account.card;

// Правила номеров карт: контрольная сумма Луна и платёжная система по первой цифре (2 — МИР, 4 — VISA, 5 — Mastercard)
final class CardNumbers {

    private static final int LUHN_BASE = 10;
    private static final int LUHN_MAX_DIGIT = 9;

    private CardNumbers() {
    }

    static boolean luhn(String digits) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            int n = digits.charAt(digits.length() - 1 - i) - '0';
            if (i % 2 == 1) {
                n *= 2;
                if (n > LUHN_MAX_DIGIT) {
                    n -= LUHN_MAX_DIGIT;
                }
            }
            sum += n;
        }
        return sum % LUHN_BASE == 0;
    }

    static String paymentSystem(String pan) {
        return switch (pan.charAt(0)) {
            case '2' -> PaymentSystem.MIR.name();
            case '4' -> PaymentSystem.VISA.name();
            case '5' -> PaymentSystem.MASTERCARD.name();
            default -> null;
        };
    }

    enum PaymentSystem {
        MIR,
        VISA,
        MASTERCARD
    }
}
