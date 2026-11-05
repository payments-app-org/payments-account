package com.paymentsapp.account.journal;

import static org.assertj.core.api.Assertions.assertThat;

import com.paymentsapp.account.limits.OperationType;
import com.paymentsapp.account.reservation.ReservationService;
import com.paymentsapp.account.reservation.ReserveCommand;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Весь account на настоящем Postgres (миграции Liquibase, блокировки строк): встречные переводы в кошельки двух
// клиентов (A→B и B→A) параллельно не взаимоблокируются, деньги не теряются и не появляются, баланс сходится
// с журналом. Взаимоблокировка здесь реальна: у переводов разные пользователи (их блокировки не мешают друг другу),
// а счета каждый поток берёт в своём порядке — если бы не сортировка по id.
// Заодно при старте CardKeyRotation защищает открытые номера карт из демо-миграций.
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {"CARD_HMAC_KEY=it-hmac-secret", "CARD_ENCRYPTION_KEY=" + TransferJournalIT.AES_KEY})
class TransferJournalIT {

    static final String AES_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    // Тестовые пользователи из миграций (оба идентифицированы) и их кошельки
    private static final UUID USER_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID WALLET_A = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    private static final UUID WALLET_B = UUID.fromString("00000000-0000-0000-0000-00000000b001");
    private static final int TRANSFERS = 40;
    private static final int THREADS = 8;
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withInitScript("create-schema.sql");

    @Autowired
    ReservationService reservations;

    @Autowired
    JdbcClient jdbc;

    @Test
    void oppositeTransfersInParallelKeepBalancesEqualToJournal() throws Exception {
        BigDecimal totalBefore = balance(WALLET_A).add(balance(WALLET_B));
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> done = new ArrayList<>();
        for (int i = 0; i < TRANSFERS; i++) {
            boolean forward = i % 2 == 0;
            ReserveCommand cmd = new ReserveCommand(UUID.randomUUID(), forward ? USER_A : USER_B, forward ? WALLET_A : WALLET_B,
                    forward ? WALLET_B : WALLET_A, OperationType.WALLET_TRANSFER, AMOUNT, BigDecimal.ZERO, "RUB", null);
            done.add(pool.submit(() -> reservations.transfer(cmd)));
        }
        for (Future<?> f : done) {
            // Взаимоблокировка Postgres оборвала бы одну из транзакций с ошибкой — get() её бы выбросил
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(balance(WALLET_A).add(balance(WALLET_B))).isEqualByComparingTo(totalBefore);
        for (UUID card : List.of(WALLET_A, WALLET_B)) {
            assertThat(balance(card)).as("баланс = журнал, счёт %s", card).isEqualByComparingTo(journalSum(card));
            assertThat(jdbc.sql("select reserved from accounts where id = :id").param("id", card).query(BigDecimal.class)
                    .single()).isEqualByComparingTo("0");
        }
    }

    @Test
    void cardNumbersAreProtectedOnStart() {
        assertThat(jdbc.sql("select count(*) from accounts where card_number is not null").query(Long.class).single())
                .isZero();
        assertThat(jdbc.sql("select count(*) from accounts where type = 'CARD' and card_key_id is distinct from 'v1'")
                .query(Long.class).single()).isZero();
        assertThat(Base64.getDecoder().decode(AES_KEY)).hasSize(32);
    }

    private BigDecimal balance(UUID account) {
        return jdbc.sql("select balance from accounts where id = :id").param("id", account).query(BigDecimal.class).single();
    }

    private BigDecimal journalSum(UUID account) {
        return jdbc.sql("""
                        select coalesce(sum(case when kind = 'DEBIT' then -amount else amount end), 0)
                        from account_operations where account_id = :id
                        """)
                .param("id", account).query(BigDecimal.class).single();
    }
}
