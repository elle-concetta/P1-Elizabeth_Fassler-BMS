package com.example.banking.unit;

import com.example.banking.model.*;
import java.math.BigDecimal;
import java.time.LocalDate;

import static com.example.banking.unit.DepositFixtures.*;

/** Synthetic endpoints; the default recipient belongs to another customer. */
final class TransferFixtures {
    static final AccountNumber RECIPIENT = AccountNumber.fromPayload("22345678901234567890123");

    static Account endpoint(long id, long owner, AccountType type, AccountStatus status, String balance) {
        var number = id == 7 ? NUMBER : RECIPIENT;
        var value = Money.parse(balance);
        var date = LocalDate.of(2026, 11, 15);
        return switch (type) {
            case SAVINGS -> new SavingsAccount(id, owner, number, value, BigDecimal.ZERO, Money.ZERO, date, status);
            case CURRENT -> new CurrentAccount(id, owner, number, value, BigDecimal.ZERO, Money.ZERO, date, status);
            case DEPOSIT -> new DepositAccount(id, owner, number, value, BigDecimal.ZERO, Money.ZERO, date, status);
            case LOAN -> new LoanAccount(id, owner, number, value, BigDecimal.ZERO, Money.ZERO, date, status);
        };
    }

    static Account source(String balance) { return endpoint(7, 1, AccountType.SAVINGS, AccountStatus.ACTIVE, balance); }
    static Account recipient(long id, long owner, String balance) {
        return endpoint(id, owner, AccountType.CURRENT, AccountStatus.ACTIVE, balance);
    }
    static BankTransaction candidate() {
        return BankTransaction.transfer(REF, source("100"), recipient(11, 2, "20"), Money.parse("25"), TIME);
    }
    private TransferFixtures() {}
}

