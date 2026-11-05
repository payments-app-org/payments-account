package com.paymentsapp.account.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CardNumberProtectorTest {

    private static final String CARD = "2200700123473752";
    private static final int MAX_LINKED = 5;
    private static final CardProperties.Key V1 = new CardProperties.Key("hmac-secret-1", aesKey(1));
    private static final CardProperties.Key V2 = new CardProperties.Key("hmac-secret-2", aesKey(2));

    private final CardNumberProtector protector = protector("v1", Map.of("v1", V1));

    @Test
    void encryptedNumberIsDecryptableAndRandomized() {
        String first = protector.encrypt(CARD);
        assertThat(first).doesNotContain(CARD).isNotEqualTo(protector.encrypt(CARD));
        assertThat(protector.decrypt("v1", first)).isEqualTo(CARD);
    }

    @Test
    void fingerprintIsStableAndKeyed() {
        CardNumberProtector other = protector("v2", Map.of("v2", V2));
        assertThat(protector.fingerprint(CARD)).isEqualTo(protector.fingerprint(CARD))
                .isNotEqualTo(other.fingerprint(CARD));
    }

    // Ротация: после смены текущего ключа старые записи по-прежнему читаются и находятся, новые — новым ключом
    @Test
    void afterRotationOldRecordsStayReadableAndFindable() {
        String storedWithV1 = protector.encrypt(CARD);
        String fingerprintV1 = protector.fingerprint(CARD);
        Map<String, CardProperties.Key> keys = new LinkedHashMap<>();
        keys.put("v1", V1);
        keys.put("v2", V2);

        CardNumberProtector rotated = protector("v2", keys);

        assertThat(rotated.currentKeyId()).isEqualTo("v2");
        assertThat(rotated.decrypt("v1", storedWithV1)).isEqualTo(CARD);
        assertThat(rotated.fingerprint(CARD)).isNotEqualTo(fingerprintV1);
        assertThat(rotated.fingerprints(CARD)).contains(fingerprintV1, rotated.fingerprint(CARD));
    }

    // Ключ удалили раньше, чем перешифровали записи на нём, — громкая ошибка, а не тихий мусор
    @Test
    void decryptWithRemovedKeyFails() {
        String storedWithV1 = protector.encrypt(CARD);
        CardNumberProtector withoutV1 = protector("v2", Map.of("v2", V2));
        assertThatThrownBy(() -> withoutV1.decrypt("v1", storedWithV1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void misconfiguredKeysFailOnStart() {
        assertThatThrownBy(() -> protector("v1", Map.of("v1", new CardProperties.Key("h", "c2hvcnQ="))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> protector("v3", Map.of("v1", V1))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void luhnAndPaymentSystem() {
        assertThat(CardNumbers.luhn("4111111111111111")).isTrue();
        assertThat(CardNumbers.luhn("4111111111111112")).isFalse();
        assertThat(CardNumbers.paymentSystem(CARD)).isEqualTo("MIR");
    }

    private static CardNumberProtector protector(String current, Map<String, CardProperties.Key> keys) {
        return new CardNumberProtector(new CardProperties(current, keys, MAX_LINKED));
    }

    private static String aesKey(int fill) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) fill);
        return Base64.getEncoder().encodeToString(key);
    }
}
