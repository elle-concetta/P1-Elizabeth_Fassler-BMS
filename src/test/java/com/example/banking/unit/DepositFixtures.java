package com.example.banking.unit;

import com.example.banking.model.*;
import java.math.BigDecimal;
import java.time.*;

/** Synthetic money-operation fixtures; no live credentials or database access. */
final class DepositFixtures {
    static final Instant TIME = Instant.parse("2026-11-15T12:00:00Z");
    static final TransactionReference REF = new TransactionReference("TX-20261115-" + "A".repeat(28));
    static final AccountNumber NUMBER = AccountNumber.fromPayload("12345678901234567890123");
    static final Money LIMIT = Money.parse("1000000.00");
    static final Clock CLOCK = Clock.fixed(TIME, ZoneOffset.UTC);

    static Customer customer(long id, CustomerStatus status) {
        return new Customer(id, "C-deposit-demo", "Demo", null, "Customer", null, null,
                "demo@example.invalid", "2025550123", "demo", status);
    }

    static Account account(long owner, AccountType type, AccountStatus status, String balance) {
        Money value = Money.parse(balance);
        LocalDate date = LocalDate.of(2026, 11, 15);
        return switch (type) {
            case SAVINGS -> new SavingsAccount(7, owner, NUMBER, value, BigDecimal.ZERO, Money.ZERO, date, status);
            case CURRENT -> new CurrentAccount(7, owner, NUMBER, value, BigDecimal.ZERO, Money.ZERO, date, status);
            case DEPOSIT -> new DepositAccount(7, owner, NUMBER, value, BigDecimal.ZERO, Money.ZERO, date, status);
            case LOAN -> new LoanAccount(7, owner, NUMBER, value, BigDecimal.ZERO, Money.ZERO, date, status);
        };
    }

    static BankTransaction receipt(String amount) {
        return BankTransaction.deposit(REF, account(1, AccountType.SAVINGS, AccountStatus.ACTIVE, "0.00"),
                Money.parse(amount), TIME);
    }

    private DepositFixtures() {}
}
