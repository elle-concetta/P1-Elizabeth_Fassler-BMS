package com.example.banking.unit;

import com.example.banking.model.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Synthetic shared fixtures; no database or real account data. */
final class AccountFixtures {
    static final LocalDate DATE = LocalDate.of(2026, 11, 14);
    static final AccountNumber NUMBER = AccountNumber.fromPayload("12345678901234567890123");
    static final AccountNumber SECOND = AccountNumber.fromPayload("22345678901234567890123");

    static Customer customer(long id, CustomerStatus status) {
        return new Customer(id, "C-fixture", "Demo", null, "Customer", null, null,
                "demo@example.invalid", "2025550123", "demo", status);
    }

    static Account account(AccountType type, long id, long owner, AccountNumber number,
                           AccountStatus status) {
        return switch (type) {
            case SAVINGS -> new SavingsAccount(id, owner, number, Money.ZERO,
                    BigDecimal.ZERO, Money.ZERO, DATE, status);
            case CURRENT -> new CurrentAccount(id, owner, number, Money.ZERO,
                    BigDecimal.ZERO, Money.ZERO, DATE, status);
            case DEPOSIT -> new DepositAccount(id, owner, number, Money.ZERO,
                    BigDecimal.ZERO, Money.ZERO, DATE, status);
            case LOAN -> new LoanAccount(id, owner, number, Money.ZERO,
                    BigDecimal.ZERO, Money.ZERO, DATE, status);
        };
    }

    private AccountFixtures() {}
}
