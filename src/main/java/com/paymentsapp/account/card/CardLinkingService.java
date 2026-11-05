package com.paymentsapp.account.card;

import static com.paymentsapp.account.error.AccountError.*;

import com.paymentsapp.account.limits.AccountLimitDefaultsRepository;
import com.paymentsapp.account.limits.AccountLimits;
import com.paymentsapp.account.limits.AccountLimitsRepository;
import com.paymentsapp.account.reservation.ReservationRepository;
import com.paymentsapp.account.reservation.ReservationStatus;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountRepository;
import com.paymentsapp.account.user.AccountStatus;
import com.paymentsapp.account.user.AccountType;
import com.paymentsapp.account.user.User;
import com.paymentsapp.account.user.UserRepository;
import com.paymentsapp.platform.error.DomainException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Привязка карты другого банка и выбор инструмента оплаты по умолчанию
@Service
public class CardLinkingService {

    private static final Pattern PAN = Pattern.compile("\\d{16,19}");
    private static final Pattern EXPIRY = Pattern.compile("(0[1-9]|1[0-2])/(\\d{2})");
    private static final Pattern CVC = Pattern.compile("\\d{3}");
    private static final Pattern SPACES = Pattern.compile("\\s");
    private static final int CENTURY = 2000;
    private static final int LAST_DIGITS = 4;

    private final UserRepository users;
    private final AccountRepository accounts;
    private final AccountLimitsRepository limits;
    private final AccountLimitDefaultsRepository limitDefaults;
    private final ReservationRepository reservations;
    private final CardNumberProtector protector;
    private final CardProperties properties;
    private final Clock clock;

    public CardLinkingService(UserRepository users, AccountRepository accounts, AccountLimitsRepository limits,
                              AccountLimitDefaultsRepository limitDefaults, ReservationRepository reservations,
                              CardNumberProtector protector, CardProperties properties, Clock clock) {
        this.users = users;
        this.accounts = accounts;
        this.limits = limits;
        this.limitDefaults = limitDefaults;
        this.reservations = reservations;
        this.protector = protector;
        this.properties = properties;
        this.clock = clock;
    }

    // CVC проверяем на формат и нигде не сохраняем; номер карты — только HMAC-отпечатком и последними цифрами
    @Transactional
    public Account link(UUID userId, String cardNumber, String expiry, String cvc) {
        users.findByIdForUpdate(userId).orElseThrow(() -> new DomainException(USER_NOT_FOUND));
        String pan = cardNumber == null ? "" : SPACES.matcher(cardNumber).replaceAll("");
        if (!PAN.matcher(pan).matches() || !CardNumbers.luhn(pan)) {
            throw new DomainException(CARD_NUMBER_INVALID);
        }
        Matcher exp = expiry == null ? null : EXPIRY.matcher(expiry.trim());
        if (exp == null || !exp.matches()) {
            throw new DomainException(CARD_EXPIRY_INVALID);
        }
        YearMonth validThru = YearMonth.of(CENTURY + Integer.parseInt(exp.group(2)), Integer.parseInt(exp.group(1)));
        if (validThru.isBefore(YearMonth.now(clock))) {
            throw new DomainException(CARD_EXPIRED);
        }
        if (cvc == null || !CVC.matcher(cvc).matches()) {
            throw new DomainException(CARD_CVC_INVALID);
        }
        List<String> fingerprints = protector.fingerprints(pan);
        String fingerprint = protector.fingerprint(pan);
        if (accounts.findFirstByCardNumberHashInAndType(fingerprints, AccountType.CARD).isPresent()) {
            throw new DomainException(CARD_IS_OURS);
        }
        Optional<Account> linked = accounts.findFirstByUserIdAndCardFingerprintInAndStatusNot(userId, fingerprints,
                AccountStatus.CLOSED);
        if (linked.isPresent()) {
            // Отпечаток на старом ключе — заодно переводим на текущий
            linked.filter(a -> !protector.currentKeyId().equals(a.getFingerprintKeyId()))
                    .ifPresent(a -> a.rekeyFingerprint(fingerprint, protector.currentKeyId()));
            throw new DomainException(CARD_ALREADY_LINKED);
        }
        if (accounts.countByUserIdAndTypeAndStatusNot(userId, AccountType.EXTERNAL_CARD, AccountStatus.CLOSED)
                >= properties.maxLinked()) {
            throw new DomainException(LINKED_CARDS_LIMIT, properties.maxLinked());
        }
        Account card = accounts.save(Account.linkedCard(userId, pan.substring(pan.length() - LAST_DIGITS),
                CardNumbers.paymentSystem(pan), expiry.trim(), fingerprint, protector.currentKeyId(), OffsetDateTime.now(clock)));
        limitDefaults.findById(AccountType.EXTERNAL_CARD)
                .ifPresent(defaults -> limits.save(AccountLimits.fromDefaults(card.getId(), defaults)));
        return card;
    }

    // Отвязать карту другого банка. Карты нашего банка и кошелёк так не удаляются.
    @Transactional
    public void unlink(UUID userId, UUID accountId) {
        User user = users.findByIdForUpdate(userId).orElseThrow(() -> new DomainException(USER_NOT_FOUND));
        Account card = accounts.findByIdForUpdate(accountId)
                .filter(a -> a.getUserId().equals(userId) && a.getStatus() != AccountStatus.CLOSED)
                .orElseThrow(() -> new DomainException(CARD_NOT_FOUND));
        if (card.getType() != AccountType.EXTERNAL_CARD) {
            throw new DomainException(ONLY_EXTERNAL_UNLINK);
        }
        if (reservations.existsByAccountIdAndStatus(accountId, ReservationStatus.RESERVED)) {
            throw new DomainException(CARD_HAS_PENDING);
        }
        card.close();
        if (accountId.equals(user.getDefaultPaymentAccountId())) {
            user.setDefaultPaymentAccount(null);
        }
    }

    @Transactional
    public void setDefaultPaymentAccount(UUID userId, UUID accountId) {
        User user = users.findByIdForUpdate(userId).orElseThrow(() -> new DomainException(USER_NOT_FOUND));
        Account account = accounts.findById(accountId)
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> new DomainException(ACCOUNT_NOT_FOUND));
        if (!account.isActive()) {
            throw new DomainException(ACCOUNT_BLOCKED);
        }
        user.setDefaultPaymentAccount(accountId);
    }
}
