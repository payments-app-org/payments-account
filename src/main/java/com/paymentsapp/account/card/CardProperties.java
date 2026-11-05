package com.paymentsapp.account.card;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Ключи для номеров карт — только из окружения (в compose — из .env), значений по умолчанию нет.
// keys — все действующие ключи по идентификатору (v1, v2…): hmac — отпечаток номера для поиска и защиты от повторной
// привязки, encryption — AES-256 (base64, 32 байта). currentKey — каким ключом защищать новые записи.
// Ротация: добавить ключ v2, сделать его текущим — при старте CardKeyRotation перешифрует наши карты на v2.
@ConfigurationProperties("account.card")
public record CardProperties(String currentKey, Map<String, Key> keys, int maxLinked) {

    public record Key(String hmac, String encryption) {
    }
}
