package com.example.banking.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Current-account snapshot; no overdraft or money mutation is exposed. */
public final class CurrentAccount extends Account {
    public CurrentAccount(long id, long owner, AccountNumber number, Money balance,
                          BigDecimal rate, Money minimum, LocalDate opened, AccountStatus status) {
        super(id, owner, number, AccountType.CURRENT, balance, rate, minimum, opened, status);
    }
}

