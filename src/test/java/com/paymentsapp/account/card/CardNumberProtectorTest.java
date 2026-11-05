package com.paymentsapp.account.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class CardNumberProtectorTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String CARD = "2200700123473752";
    private static final int MAX_LINKED = 5;

    private final CardNumberProtector protector = new CardNumberProtector(new CardProperties("hmac-secret", KEY, MAX_LINKED));

    @Test
    void encryptedNumberIsDecryptableAndRandomized() {
        String first = protector.encrypt(CARD);
        assertThat(first).doesNotContain(CARD).isNotEqualTo(protector.encrypt(CARD));
        assertThat(protector.decrypt(first)).isEqualTo(CARD);
    }

    @Test
    void fingerprintIsStableAndKeyed() {
        CardNumberProtector other = new CardNumberProtector(new CardProperties("another-secret", KEY, MAX_LINKED));
        assertThat(protector.fingerprint(CARD)).isEqualTo(protector.fingerprint(CARD))
                .isNotEqualTo(other.fingerprint(CARD));
    }

    @Test
    void wrongKeyLengthFailsOnStart() {
        assertThatThrownBy(() -> new CardNumberProtector(new CardProperties("h", "c2hvcnQ=", MAX_LINKED)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void luhnAndPaymentSystem() {
        assertThat(CardNumbers.luhn("4111111111111111")).isTrue();
        assertThat(CardNumbers.luhn("4111111111111112")).isFalse();
        assertThat(CardNumbers.paymentSystem(CARD)).isEqualTo("MIR");
    }
}
