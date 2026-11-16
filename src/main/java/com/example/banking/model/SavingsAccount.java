package com.example.banking.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Savings snapshot; no overdraft or money mutation is exposed. */
public final class SavingsAccount extends Account {
    public SavingsAccount(long id, long owner, AccountNumber number, Money balance,
                          BigDecimal rate, Money minimum, LocalDate opened, AccountStatus status) {
        super(id, owner, number, AccountType.SAVINGS, balance, rate, minimum, opened, status);
    }
}

