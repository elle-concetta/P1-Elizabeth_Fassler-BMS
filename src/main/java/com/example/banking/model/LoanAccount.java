package com.example.banking.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Read-only outstanding-principal snapshot. Lending and repayment are not implemented. */
public final class LoanAccount extends Account {
    public LoanAccount(long id, long owner, AccountNumber number, Money balance,
                       BigDecimal rate, Money minimum, LocalDate opened, AccountStatus status) {
        super(id, owner, number, AccountType.LOAN, balance, rate, minimum, opened, status);
    }
}

