package com.paymentsapp.account.profile;

import static com.paymentsapp.account.error.AccountError.*;

import com.paymentsapp.account.user.User;
import com.paymentsapp.account.user.UserRepository;
import com.paymentsapp.platform.error.DomainException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// «Округление на пользу»: включить с шагом и фондом, выключить
@Service
public class RoundupService {

    private static final String STEPS_SEPARATOR = ", ";

    private final UserRepository users;
    private final RoundupProperties properties;
    private final Clock clock;

    public RoundupService(UserRepository users, RoundupProperties properties, Clock clock) {
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public User enable(UUID userId, Integer step, String fundCode, String fundName) {
        User user = users.findById(userId).orElseThrow(() -> new DomainException(USER_NOT_FOUND));
        if (step == null || !properties.steps().contains(step)) {
            throw new DomainException(ROUNDUP_STEP_INVALID,
                    properties.steps().stream().map(String::valueOf).collect(Collectors.joining(STEPS_SEPARATOR)));
        }
        if (isBlank(fundCode) || isBlank(fundName)) {
            throw new DomainException(ROUNDUP_FUND_REQUIRED);
        }
        user.enableRoundup(step, fundCode.trim(), fundName.trim(), OffsetDateTime.now(clock));
        return user;
    }

    @Transactional
    public void disable(UUID userId) {
        users.findById(userId).orElseThrow(() -> new DomainException(USER_NOT_FOUND)).disableRoundup();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
