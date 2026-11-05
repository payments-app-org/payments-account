package com.paymentsapp.account.reservation.check;

import static com.paymentsapp.account.error.AccountError.*;

import com.paymentsapp.account.limits.Limits;
import com.paymentsapp.account.limits.LimitsService;
import com.paymentsapp.account.limits.OperationType;
import com.paymentsapp.account.user.Account;
import com.paymentsapp.account.user.AccountType;
import com.paymentsapp.account.user.User;
import com.paymentsapp.account.user.UserRepository;
import com.paymentsapp.platform.error.DomainException;
import com.paymentsapp.platform.text.Rubles;
import java.math.BigDecimal;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// Получатель — наш клиент. Между своими — на любой свой счёт; на кошелёк — только на чужой «Мой кошелёк»;
// остальным переводам — только на карту. Максимальный остаток получателя проверяем здесь: после процессинга
// отказать в зачислении уже нельзя.
@Component
@Order(3)
public class RecipientCheck implements ReservationCheck {

    private final UserRepository users;
    private final LimitsService limits;

    public RecipientCheck(UserRepository users, LimitsService limits) {
        this.users = users;
        this.limits = limits;
    }

    @Override
    public void check(ReservationContext ctx) {
        if (!ctx.hasRecipient()) {
            return;
        }
        Account recipient = ctx.recipient();
        if (recipient == null) {
            throw new DomainException(RECIPIENT_NOT_FOUND);
        }
        Account source = ctx.source();
        OperationType operation = ctx.command().operationType();
        boolean own = operation == OperationType.OWN_TRANSFER;
        boolean toWallet = operation == OperationType.WALLET_TRANSFER;
        boolean sameOwner = recipient.getUserId().equals(source.getUserId());
        if (own && !sameOwner) {
            throw new DomainException(RECIPIENT_NOT_OWN);
        }
        // На свою привязанную карту другого банка перевести можно (доставит процессинг), на чужую — нет
        if (recipient.isExternal() && !own) {
            throw new DomainException(RECIPIENT_EXTERNAL);
        }
        // Электронные деньги нельзя получить в кредит: с кредитной карты кошелёк не пополняем
        if (recipient.isWallet() && source.isCredit()) {
            throw new DomainException(WALLET_TOPUP_FROM_CREDIT);
        }
        User owner = users.findById(recipient.getUserId()).orElseThrow(() -> new DomainException(RECIPIENT_NOT_FOUND));
        if (toWallet && !sameOwner && !limits.identification(owner.getIdentificationLevel()).receivesTransfers()) {
            throw new DomainException(RECIPIENT_NOT_IDENTIFIED);
        }
        if (toWallet && !recipient.isWallet()) {
            throw new DomainException(RECIPIENT_NOT_WALLET);
        }
        if (!own && !toWallet && recipient.getType() != AccountType.CARD) {
            throw new DomainException(RECIPIENT_NOT_CARD);
        }
        if (!recipient.isActive()) {
            throw new DomainException(RECIPIENT_BLOCKED);
        }
        if (recipient.getId().equals(source.getId())) {
            throw new DomainException(RECIPIENT_SAME_ACCOUNT);
        }
        if (!recipient.getCurrency().equals(source.getCurrency())) {
            throw new DomainException(RECIPIENT_CURRENCY_MISMATCH);
        }
        Limits recipientLimits = limits.of(recipient, owner.getIdentificationLevel());
        BigDecimal maxBalance = recipientLimits.maxBalance();
        if (maxBalance != null && recipient.getBalance().add(ctx.command().amount()).compareTo(maxBalance) > 0) {
            throw new DomainException(RECIPIENT_MAX_BALANCE, Rubles.format(maxBalance));
        }
    }
}
