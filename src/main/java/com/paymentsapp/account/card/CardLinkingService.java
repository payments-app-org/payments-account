package com.paymentsapp.account.card;

import com.paymentsapp.account.reservation.AccountLimits;
import com.paymentsapp.account.reservation.AccountLimitsRepository;
import com.paymentsapp.account.reservation.ReservationRepository;
import com.paymentsapp.account.reservation.ReservationStatus;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountRepository;
import com.paymentsapp.account.user.AccountStatus;
import com.paymentsapp.account.user.AccountType;
import com.paymentsapp.account.user.User;
import com.paymentsapp.account.user.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Привязка карты другого банка и выбор инструмента оплаты по умолчанию
@Service
public class CardLinkingService {

    private static final Pattern PAN = Pattern.compile("\\d{16,19}");
    private static final Pattern EXPIRY = Pattern.compile("(0[1-9]|1[0-2])/(\\d{2})");
    private static final Pattern CVC = Pattern.compile("\\d{3}");
    private static final int MAX_LINKED_CARDS = 5;
    // Лимиты привязанной карты: со стороны нашего банка платить с неё можно умеренно
    private static final BigDecimal LINKED_MAX_PAYMENT = BigDecimal.valueOf(100_000);
    private static final BigDecimal LINKED_MAX_MONTHLY = BigDecimal.valueOf(300_000);

    private final UserRepository users;
    private final AccountRepository accounts;
    private final AccountLimitsRepository limits;
    private final ReservationRepository reservations;

    public CardLinkingService(UserRepository users, AccountRepository accounts, AccountLimitsRepository limits,
                              ReservationRepository reservations) {
        this.users = users;
        this.accounts = accounts;
        this.limits = limits;
        this.reservations = reservations;
    }

    // CVC только проверяем на формат и нигде не сохраняем; номер карты храним отпечатком и последними цифрами
    @Transactional
    public Account link(UUID userId, String cardNumber, String expiry, String cvc) {
        users.findByIdForUpdate(userId).orElseThrow(() -> bad(HttpStatus.NOT_FOUND, "Пользователь не найден"));
        String pan = cardNumber == null ? "" : cardNumber.replaceAll("\\s", "");
        if (!PAN.matcher(pan).matches() || !luhn(pan)) {
            throw bad(HttpStatus.BAD_REQUEST, "Проверьте номер карты");
        }
        var exp = expiry == null ? null : EXPIRY.matcher(expiry.trim());
        if (exp == null || !exp.matches()) {
            throw bad(HttpStatus.BAD_REQUEST, "Срок действия в формате ММ/ГГ");
        }
        YearMonth validThru = YearMonth.of(2000 + Integer.parseInt(exp.group(2)), Integer.parseInt(exp.group(1)));
        if (validThru.isBefore(YearMonth.now(ZoneId.of("Europe/Moscow")))) {
            throw bad(HttpStatus.UNPROCESSABLE_CONTENT, "Срок действия карты истёк");
        }
        if (cvc == null || !CVC.matcher(cvc).matches()) {
            throw bad(HttpStatus.BAD_REQUEST, "CVC/CVV — три цифры на обороте карты");
        }
        if (accounts.findByCardNumberAndType(pan, AccountType.CARD).isPresent()) {
            throw bad(HttpStatus.UNPROCESSABLE_CONTENT, "Это карта нашего банка — привязывать её не нужно");
        }
        String fingerprint = sha256(pan);
        if (accounts.existsByUserIdAndCardFingerprintAndStatusNot(userId, fingerprint, AccountStatus.CLOSED)) {
            throw bad(HttpStatus.CONFLICT, "Эта карта уже привязана");
        }
        if (accounts.countByUserIdAndTypeAndStatusNot(userId, AccountType.EXTERNAL_CARD, AccountStatus.CLOSED) >= MAX_LINKED_CARDS) {
            throw bad(HttpStatus.UNPROCESSABLE_CONTENT, "Можно привязать не больше " + MAX_LINKED_CARDS + " карт");
        }
        Account card = accounts.save(Account.linkedCard(userId, pan.substring(pan.length() - 4), paymentSystem(pan),
                expiry.trim(), fingerprint));
        limits.save(new AccountLimits(card.getId(), LINKED_MAX_PAYMENT, null, LINKED_MAX_MONTHLY));
        return card;
    }

    // Отвязать карту другого банка. Карты нашего банка и кошелёк так не удаляются.
    @Transactional
    public void unlink(UUID userId, UUID accountId) {
        User user = users.findByIdForUpdate(userId).orElseThrow(() -> bad(HttpStatus.NOT_FOUND, "Пользователь не найден"));
        Account card = accounts.findByIdForUpdate(accountId)
                .filter(a -> a.getUserId().equals(userId) && a.getStatus() != AccountStatus.CLOSED)
                .orElseThrow(() -> bad(HttpStatus.NOT_FOUND, "Карта не найдена"));
        if (card.getType() != AccountType.EXTERNAL_CARD) {
            throw bad(HttpStatus.UNPROCESSABLE_CONTENT, "Отвязать можно только карту другого банка");
        }
        if (reservations.existsByAccountIdAndStatus(accountId, ReservationStatus.RESERVED)) {
            throw bad(HttpStatus.CONFLICT, "По карте есть незавершённый перевод, попробуйте позже");
        }
        card.close();
        if (accountId.equals(user.getDefaultPaymentAccountId())) {
            user.setDefaultPaymentAccount(null);
        }
    }

    @Transactional
    public void setDefaultPaymentAccount(UUID userId, UUID accountId) {
        User user = users.findByIdForUpdate(userId).orElseThrow(() -> bad(HttpStatus.NOT_FOUND, "Пользователь не найден"));
        Account account = accounts.findById(accountId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> bad(HttpStatus.NOT_FOUND, "Счёт не найден"));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw bad(HttpStatus.UNPROCESSABLE_CONTENT, "Счёт заблокирован");
        }
        user.setDefaultPaymentAccount(accountId);
    }

    // По первой цифре: 2 — МИР, 4 — VISA, 5 — Mastercard
    private static String paymentSystem(String pan) {
        return switch (pan.charAt(0)) {
            case '2' -> "MIR";
            case '4' -> "VISA";
            case '5' -> "MASTERCARD";
            default -> null;
        };
    }

    private static boolean luhn(String digits) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            int n = digits.charAt(digits.length() - 1 - i) - '0';
            if (i % 2 == 1) {
                n = n * 2 > 9 ? n * 2 - 9 : n * 2;
            }
            sum += n;
        }
        return sum % 10 == 0;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResponseStatusException bad(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
