package com.paymentsapp.account.web;

import com.paymentsapp.account.profile.RecipientLookup;
import com.paymentsapp.account.profile.RecipientLookup.Recipient;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Внутренний API для payment и pricing: найти нашего клиента по реквизитам и счёт, на который зачислить перевод.
// 404 — реквизиты не наши (перевод уходит в другой банк).
@RestController
@RequestMapping("/api/internal/recipients")
public class RecipientController {

    private final RecipientLookup recipients;

    public RecipientController(RecipientLookup recipients) {
        this.recipients = recipients;
    }

    @GetMapping(params = "phone")
    public Recipient byPhone(@RequestParam @Pattern(regexp = "\\+7\\d{10}", message = "телефон в формате +7XXXXXXXXXX") String phone) {
        return recipients.byPhone(phone);
    }

    @GetMapping(params = "cardNumber")
    public Recipient byCard(@RequestParam @Pattern(regexp = "\\d{16,19}", message = "номер карты — 16–19 цифр") String cardNumber) {
        return recipients.byCard(cardNumber);
    }
}
