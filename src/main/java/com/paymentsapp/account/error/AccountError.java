package com.paymentsapp.account.error;

import com.paymentsapp.platform.error.ErrorCode;
import org.springframework.http.HttpStatus;

// Отказы account. code — контракт с payment (FailureCode): у разных текстов может быть один общий код.
public enum AccountError implements ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Пользователь не найден", Codes.ACCOUNT_NOT_FOUND),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Счёт не найден"),
    ACCOUNT_NOT_OWNED(HttpStatus.FORBIDDEN, "Счёт принадлежит другому пользователю"),
    ACCOUNT_BLOCKED(HttpStatus.UNPROCESSABLE_CONTENT, "Счёт заблокирован"),
    CARD_UNLINKED(HttpStatus.UNPROCESSABLE_CONTENT, "Карта отвязана", Codes.ACCOUNT_BLOCKED),
    CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "Валюта счёта не совпадает с валютой платежа"),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_CONTENT, "Недостаточно средств"),

    RECIPIENT_NOT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT, "Счёт получателя не найден", Codes.RECIPIENT_INVALID),
    RECIPIENT_NOT_OWN(HttpStatus.UNPROCESSABLE_CONTENT, "Счёт зачисления принадлежит другому пользователю", Codes.RECIPIENT_INVALID),
    RECIPIENT_EXTERNAL(HttpStatus.UNPROCESSABLE_CONTENT, "Зачислить на карту другого банка отсюда нельзя", Codes.RECIPIENT_INVALID),
    RECIPIENT_NOT_WALLET(HttpStatus.UNPROCESSABLE_CONTENT, "Перевод на кошелёк зачисляется только на «Мой кошелёк»", Codes.RECIPIENT_INVALID),
    RECIPIENT_NOT_CARD(HttpStatus.UNPROCESSABLE_CONTENT, "Переводить можно только на карту", Codes.RECIPIENT_INVALID),
    RECIPIENT_BLOCKED(HttpStatus.UNPROCESSABLE_CONTENT, "Счёт получателя заблокирован", Codes.RECIPIENT_INVALID),
    RECIPIENT_SAME_ACCOUNT(HttpStatus.UNPROCESSABLE_CONTENT, "Нельзя перевести на тот же счёт", Codes.RECIPIENT_INVALID),
    RECIPIENT_CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "Валюта карты получателя другая", Codes.CURRENCY_MISMATCH),
    WALLET_TOPUP_FROM_CREDIT(HttpStatus.UNPROCESSABLE_CONTENT, "Пополнить кошелёк с кредитной карты нельзя", Codes.RECIPIENT_INVALID),
    RECIPIENT_NOT_IDENTIFIED(HttpStatus.UNPROCESSABLE_CONTENT,
            "Кошелёк получателя не принимает переводы: получатель не прошёл идентификацию", Codes.RECIPIENT_INVALID),
    RECIPIENT_MAX_BALANCE(HttpStatus.UNPROCESSABLE_CONTENT, "Превышен максимальный остаток на счёте получателя: %s",
            Codes.RECIPIENT_LIMIT_EXCEEDED),

    BELOW_MIN_AMOUNT(HttpStatus.UNPROCESSABLE_CONTENT, "Минимальная сумма перевода — %s", Codes.LIMIT_EXCEEDED),
    SINGLE_LIMIT(HttpStatus.UNPROCESSABLE_CONTENT, "Превышен лимит на одну операцию: %s", Codes.LIMIT_EXCEEDED),
    DAY_LIMIT(HttpStatus.UNPROCESSABLE_CONTENT, "Превышен дневной лимит: %s, доступно ещё %s", Codes.LIMIT_EXCEEDED),
    MONTH_LIMIT(HttpStatus.UNPROCESSABLE_CONTENT, "Превышен месячный лимит: %s, доступно ещё %s", Codes.LIMIT_EXCEEDED),
    ACCOUNT_MAX_PAYMENT(HttpStatus.UNPROCESSABLE_CONTENT, "Превышена максимальная сумма платежа: %s", Codes.LIMIT_EXCEEDED),
    ACCOUNT_MONTH_TURNOVER(HttpStatus.UNPROCESSABLE_CONTENT,
            "Превышена максимальная сумма операций в месяц: %s, доступно ещё %s", Codes.LIMIT_EXCEEDED),

    // Запреты по уровню идентификации — справочник identification_restrictions ссылается на эти коды
    PERSON_TRANSFER_NOT_IDENTIFIED(HttpStatus.UNPROCESSABLE_CONTENT,
            "Переводы другим людям недоступны без идентификации. Пройдите упрощённую идентификацию", Codes.IDENTIFICATION_REQUIRED),
    ABROAD_NOT_IDENTIFIED(HttpStatus.UNPROCESSABLE_CONTENT, "Переводы и платежи за рубеж недоступны без идентификации",
            Codes.IDENTIFICATION_REQUIRED),
    EMONEY_NOT_IDENTIFIED(HttpStatus.UNPROCESSABLE_CONTENT, "Пополнение чужих электронных кошельков недоступно без идентификации",
            Codes.IDENTIFICATION_REQUIRED),
    EMONEY_REQUIRES_FULL(HttpStatus.UNPROCESSABLE_CONTENT,
            "Переводы с кошелька на другие электронные кошельки доступны только с полной идентификацией", Codes.IDENTIFICATION_REQUIRED),

    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Резервирование не найдено"),
    RESERVATION_CONFLICT(HttpStatus.CONFLICT, "Платёж уже зарезервирован с другими параметрами"),
    RESERVATION_CAPTURED(HttpStatus.CONFLICT, "Резерв уже списан, снять нельзя", Codes.RESERVATION_CONFLICT),
    RESERVATION_RELEASED(HttpStatus.CONFLICT, "Резерв уже снят, списать нельзя", Codes.RESERVATION_CONFLICT),

    CARD_NUMBER_INVALID(HttpStatus.BAD_REQUEST, "Проверьте номер карты"),
    CARD_EXPIRY_INVALID(HttpStatus.BAD_REQUEST, "Срок действия в формате ММ/ГГ"),
    CARD_EXPIRED(HttpStatus.UNPROCESSABLE_CONTENT, "Срок действия карты истёк"),
    CARD_CVC_INVALID(HttpStatus.BAD_REQUEST, "CVC/CVV — три цифры на обороте карты"),
    CARD_IS_OURS(HttpStatus.UNPROCESSABLE_CONTENT, "Это карта нашего банка — привязывать её не нужно"),
    CARD_ALREADY_LINKED(HttpStatus.CONFLICT, "Эта карта уже привязана"),
    LINKED_CARDS_LIMIT(HttpStatus.UNPROCESSABLE_CONTENT, "Можно привязать не больше %d карт"),
    CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "Карта не найдена"),
    ONLY_EXTERNAL_UNLINK(HttpStatus.UNPROCESSABLE_CONTENT, "Отвязать можно только карту другого банка"),
    CARD_HAS_PENDING(HttpStatus.CONFLICT, "По карте есть незавершённый перевод, попробуйте позже"),

    ROUNDUP_STEP_INVALID(HttpStatus.BAD_REQUEST, "Шаг округления — до %s ₽"),
    ROUNDUP_FUND_REQUIRED(HttpStatus.BAD_REQUEST, "Выберите благотворительный фонд"),
    SBP_BANK_REQUIRED(HttpStatus.BAD_REQUEST, "Выберите банк"),
    RECIPIENT_UNKNOWN(HttpStatus.NOT_FOUND, "Получатель не найден");

    private final HttpStatus status;
    private final String template;
    private final String code;

    AccountError(HttpStatus status, String template) {
        this(status, template, null);
    }

    AccountError(HttpStatus status, String template, String code) {
        this.status = status;
        this.template = template;
        this.code = code;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String template() {
        return template;
    }

    @Override
    public String code() {
        return code != null ? code : name();
    }

    // Общие коды, которые видит payment
    private static final class Codes {
        static final String ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";
        static final String ACCOUNT_BLOCKED = "ACCOUNT_BLOCKED";
        static final String CURRENCY_MISMATCH = "CURRENCY_MISMATCH";
        static final String RECIPIENT_INVALID = "RECIPIENT_INVALID";
        static final String RECIPIENT_LIMIT_EXCEEDED = "RECIPIENT_LIMIT_EXCEEDED";
        static final String LIMIT_EXCEEDED = "LIMIT_EXCEEDED";
        static final String IDENTIFICATION_REQUIRED = "IDENTIFICATION_REQUIRED";
        static final String RESERVATION_CONFLICT = "RESERVATION_CONFLICT";
    }
}
