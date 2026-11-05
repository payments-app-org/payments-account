package com.paymentsapp.account.card;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Ключи для номеров карт — только из окружения (в compose — из .env), значений по умолчанию нет.
// hmacKey — отпечаток номера для поиска и защиты от повторной привязки; encryptionKey — AES-256 (base64, 32 байта).
@ConfigurationProperties("account.card")
public record CardProperties(String hmacKey, String encryptionKey, int maxLinked) {
}
