package com.paymentsapp.account.card;

import com.paymentsapp.platform.crypto.Hashing;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

// Номер карты в базе: HMAC-SHA256 с секретным ключом (по нему ищем; без ключа номер не подобрать перебором по BIN
// и алгоритму Луна) и шифротекст AES-256-GCM (iv || ciphertext+tag в base64). У каждой записи — id ключа:
// ключи можно менять, не теряя доступа к старым записям. Новые записи — текущим ключом, поиск — по всем.
@Component
public class CardNumberProtector {

    private static final String AES = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final String current;
    private final Map<String, byte[]> hmacKeys = new LinkedHashMap<>();
    private final Map<String, SecretKey> encryptionKeys = new LinkedHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public CardNumberProtector(CardProperties properties) {
        if (properties.keys() == null || properties.keys().isEmpty()) {
            throw new IllegalStateException("Не заданы ключи account.card.keys (CARD_HMAC_KEY, CARD_ENCRYPTION_KEY)");
        }
        properties.keys().forEach((id, key) -> {
            if (key.hmac() == null || key.hmac().isBlank()) {
                throw new IllegalStateException("Не задан HMAC-ключ карт " + id);
            }
            byte[] aes = key.encryption() == null ? new byte[0] : Base64.getDecoder().decode(key.encryption());
            if (aes.length != KEY_BYTES) {
                throw new IllegalStateException("Ключ шифрования карт " + id + " — 32 байта в base64");
            }
            hmacKeys.put(id, key.hmac().getBytes(StandardCharsets.UTF_8));
            encryptionKeys.put(id, new SecretKeySpec(aes, AES));
        });
        if (!hmacKeys.containsKey(properties.currentKey())) {
            throw new IllegalStateException("Текущий ключ карт " + properties.currentKey() + " не задан в account.card.keys");
        }
        this.current = properties.currentKey();
    }

    public String currentKeyId() {
        return current;
    }

    // Отпечаток текущим ключом — для новых записей
    public String fingerprint(String cardNumber) {
        return Hashing.hmacSha256Hex(hmacKeys.get(current), cardNumber);
    }

    // Отпечатки всеми ключами — для поиска: запись могла быть сделана старым ключом
    public List<String> fingerprints(String cardNumber) {
        return hmacKeys.values().stream().map(key -> Hashing.hmacSha256Hex(key, cardNumber)).toList();
    }

    public String encrypt(String cardNumber) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKeys.get(current), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(cardNumber.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // Нужен при ротации: номер нашей карты расшифровываем старым ключом и защищаем новым
    public String decrypt(String keyId, String stored) {
        SecretKey key = encryptionKeys.get(keyId);
        if (key == null) {
            throw new IllegalStateException("Ключ карт " + keyId + " удалён, а записи на нём остались");
        }
        try {
            ByteBuffer data = ByteBuffer.wrap(Base64.getDecoder().decode(stored));
            byte[] iv = new byte[IV_BYTES];
            data.get(iv);
            byte[] encrypted = new byte[data.remaining()];
            data.get(encrypted);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
