package com.paymentsapp.account.profile;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Допустимые шаги «Округления на пользу» (₽); те же значения — в ограничении users_roundup_step_check
@ConfigurationProperties("account.roundup")
public record RoundupProperties(List<Integer> steps) {
}
