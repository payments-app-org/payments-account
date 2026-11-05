package com.paymentsapp.account.limits;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Счётчики израсходованных лимитов: вместо SUM по всем резервам месяца — одна строка на период.
// Меняются в транзакции резерва (+) и снятия резерва (−); период — по дате создания резерва в часовом поясе бизнеса.
// Пользователь заблокирован (SELECT FOR UPDATE) на время резерва, поэтому гонок за одну строку нет.
@Component
public class UsageCounters {

    private final JdbcClient jdbc;
    private final Clock clock;

    public UsageCounters(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void add(Usage usage) {
        change(usage, BigDecimal.ONE);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void subtract(Usage usage) {
        change(usage, BigDecimal.ONE.negate());
    }

    // Сколько пользователь потратил операциями этого вида за период, начавшийся periodStart
    public BigDecimal operationUsed(UUID userId, OperationType operation, LimitSource source, LimitPeriod period,
                                    LocalDate periodStart) {
        List<String> sources = source == LimitSource.ANY
                ? List.of(SourceKind.WALLET.name(), SourceKind.CARD.name()) : List.of(source.name());
        return jdbc.sql("""
                        select coalesce(sum(amount), 0) from operation_usage
                        where user_id = :userId and operation_type = :operation and source in (:sources)
                          and period = :period and period_start = :start
                        """)
                .param("userId", userId)
                .param("operation", operation.name())
                .param("sources", sources)
                .param("period", period.name())
                .param("start", periodStart)
                .query(BigDecimal.class)
                .single();
    }

    // Оборот счёта за календарный месяц (вместе с комиссией)
    public BigDecimal accountMonthUsed(UUID accountId, LocalDate monthStart) {
        return jdbc.sql("select coalesce(sum(amount), 0) from account_usage where account_id = :accountId and period_start = :start")
                .param("accountId", accountId)
                .param("start", monthStart)
                .query(BigDecimal.class)
                .single();
    }

    public LocalDate dayOf(OffsetDateTime at) {
        return at.atZoneSameInstant(clock.getZone()).toLocalDate();
    }

    public LocalDate monthOf(OffsetDateTime at) {
        return dayOf(at).withDayOfMonth(1);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDate thisMonth() {
        return today().withDayOfMonth(1);
    }

    private void change(Usage u, BigDecimal sign) {
        LocalDate day = dayOf(u.at());
        LocalDate month = monthOf(u.at());
        BigDecimal amount = u.amount().multiply(sign);
        upsertOperation(u, LimitPeriod.DAY, day, amount);
        upsertOperation(u, LimitPeriod.MONTH, month, amount);
        jdbc.sql("""
                        insert into account_usage (account_id, period_start, amount) values (:accountId, :start, :amount)
                        on conflict (account_id, period_start) do update set amount = account_usage.amount + excluded.amount
                        """)
                .param("accountId", u.accountId())
                .param("start", month)
                .param("amount", u.total().multiply(sign))
                .update();
    }

    private void upsertOperation(Usage u, LimitPeriod period, LocalDate start, BigDecimal amount) {
        jdbc.sql("""
                        insert into operation_usage (user_id, operation_type, source, period, period_start, amount)
                        values (:userId, :operation, :source, :period, :start, :amount)
                        on conflict (user_id, operation_type, source, period, period_start)
                        do update set amount = operation_usage.amount + excluded.amount
                        """)
                .param("userId", u.userId())
                .param("operation", u.operation().name())
                .param("source", u.source().name())
                .param("period", period.name())
                .param("start", start)
                .param("amount", amount)
                .update();
    }

    // amount — для лимитов на вид операции (без комиссии), total — для оборота счёта (с комиссией)
    public record Usage(UUID userId, OperationType operation, SourceKind source, UUID accountId,
                        BigDecimal amount, BigDecimal total, OffsetDateTime at) {
    }
}
