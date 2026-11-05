package com.paymentsapp.account.card;

import com.paymentsapp.platform.crypto.Hashing;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

// Номер карты в базе: HMAC-SHA256 с секретным ключом (по нему ищем; без ключа номер не подобрать перебором по BIN
// и алгоритму Луна) и шифротекст AES-256-GCM (iv || ciphertext+tag в base64).
@Component
public class CardNumberProtector {

    private static final String AES = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final byte[] hmacKey;
    private final SecretKey encryptionKey;
    private final SecureRandom random = new SecureRandom();

    public CardNumberProtector(CardProperties properties) {
        if (properties.hmacKey() == null || properties.hmacKey().isBlank()) {
            throw new IllegalStateException("Не задан account.card.hmac-key (CARD_HMAC_KEY)");
        }
        byte[] key = properties.encryptionKey() == null ? new byte[0] : Base64.getDecoder().decode(properties.encryptionKey());
        if (key.length != KEY_BYTES) {
            throw new IllegalStateException("account.card.encryption-key (CARD_ENCRYPTION_KEY) — 32 байта в base64");
        }
        this.hmacKey = properties.hmacKey().getBytes(StandardCharsets.UTF_8);
        this.encryptionKey = new SecretKeySpec(key, AES);
    }

    public String fingerprint(String cardNumber) {
        return Hashing.hmacSha256Hex(hmacKey, cardNumber);
    }

    public String encrypt(String cardNumber) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(cardNumber.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String decrypt(String stored) {
        try {
            ByteBuffer data = ByteBuffer.wrap(Base64.getDecoder().decode(stored));
            byte[] iv = new byte[IV_BYTES];
            data.get(iv);
            byte[] encrypted = new byte[data.remaining()];
            data.get(encrypted);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
