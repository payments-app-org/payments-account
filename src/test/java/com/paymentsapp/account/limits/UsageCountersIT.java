package com.paymentsapp.account.limits;

import static org.assertj.core.api.Assertions.assertThat;

import com.paymentsapp.account.user.IdentificationLevel;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Миграции Liquibase на настоящем Postgres + SQL счётчиков лимитов и справочники с L2-кэшем
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({UsageCounters.class, LimitsService.class, UsageCountersIT.Time.class})
@TestPropertySource(properties = {
        "spring.datasource.hikari.schema=account",
        "spring.liquibase.default-schema=account",
        "spring.liquibase.liquibase-schema=account",
        "spring.jpa.properties.hibernate.default_schema=account"
})
class UsageCountersIT {

    // Тестовый пользователь из миграций
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withInitScript("create-schema.sql");

    @Autowired
    UsageCounters usage;

    @Autowired
    LimitsService limits;

    @Test
    void countersGrowOnReserveAndShrinkOnRelease() {
        UUID account = UUID.fromString("00000000-0000-0000-0000-00000000a001");
        OffsetDateTime at = OffsetDateTime.now(MOSCOW);
        BigDecimal before = usage.operationUsed(USER, OperationType.SBP_PHONE, LimitSource.ANY, LimitPeriod.MONTH, usage.thisMonth());
        BigDecimal turnoverBefore = usage.accountMonthUsed(account, usage.thisMonth());
        UsageCounters.Usage u = new UsageCounters.Usage(USER, OperationType.SBP_PHONE, SourceKind.CARD, account,
                new BigDecimal("1000"), new BigDecimal("1050"), at);

        usage.add(u);
        usage.add(u);
        assertThat(usage.operationUsed(USER, OperationType.SBP_PHONE, LimitSource.ANY, LimitPeriod.MONTH, usage.thisMonth()))
                .isEqualByComparingTo(before.add(new BigDecimal("2000")));
        assertThat(usage.operationUsed(USER, OperationType.SBP_PHONE, LimitSource.WALLET, LimitPeriod.MONTH, usage.thisMonth()))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(usage.accountMonthUsed(account, usage.thisMonth())).isEqualByComparingTo(turnoverBefore.add(new BigDecimal("2100")));

        usage.subtract(u);
        assertThat(usage.operationUsed(USER, OperationType.SBP_PHONE, LimitSource.ANY, LimitPeriod.DAY, usage.today()))
                .isGreaterThanOrEqualTo(new BigDecimal("1000"));
        assertThat(usage.accountMonthUsed(account, usage.thisMonth())).isEqualByComparingTo(turnoverBefore.add(new BigDecimal("1050")));
    }

    @Test
    void referenceDataComesFromMigrations() {
        assertThat(limits.identification(IdentificationLevel.ANONYMOUS).getWalletMaxBalance()).isEqualByComparingTo("15000");
        assertThat(limits.identification(IdentificationLevel.ANONYMOUS).receivesTransfers()).isFalse();
        assertThat(limits.restrictions(IdentificationLevel.SIMPLIFIED)).hasSize(2);
        assertThat(limits.operationLimits(OperationType.ABROAD)).hasSize(4);
    }

    @TestConfiguration
    static class Time {
        @Bean
        Clock clock() {
            return Clock.system(MOSCOW);
        }
    }
}
