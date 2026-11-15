package com.example.banking.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Read-only principal snapshot. Funded opening and settlement remain separate work. */
public final class DepositAccount extends Account {
    public DepositAccount(long id, long owner, AccountNumber number, Money balance,
                          BigDecimal rate, Money minimum, LocalDate opened, AccountStatus status) {
        super(id, owner, number, AccountType.DEPOSIT, balance, rate, minimum, opened, status);
    }
}
