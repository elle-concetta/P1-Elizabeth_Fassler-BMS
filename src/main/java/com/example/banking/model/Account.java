package com.example.banking.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Immutable persisted account snapshot. Mutating money workflows are separate. */
public abstract class Account {
    private final long accountId;
    private final long customerId;
    private final AccountNumber number;
    private final AccountType type;
    private final Money balance;
    private final BigDecimal interestRate;
    private final Money minimumBalance;
    private final LocalDate openingDate;
    private final AccountStatus status;

    protected Account(long accountId, long customerId, AccountNumber number, AccountType type,
                      Money balance, BigDecimal interestRate, Money minimumBalance,
                      LocalDate openingDate, AccountStatus status) {
        if (accountId <= 0 || customerId <= 0)
            throw new IllegalArgumentException("Saved account and owner identities are required.");
        this.accountId = accountId;
        this.customerId = customerId;
        this.number = Objects.requireNonNull(number);
        this.type = Objects.requireNonNull(type);
        this.balance = Objects.requireNonNull(balance);
        this.minimumBalance = Objects.requireNonNull(minimumBalance);
        this.interestRate = Objects.requireNonNull(interestRate);
        this.openingDate = Objects.requireNonNull(openingDate);
        this.status = Objects.requireNonNull(status);
        if (balance.compareTo(Money.ZERO) < 0 || minimumBalance.compareTo(Money.ZERO) < 0
                || interestRate.scale() > 2 || interestRate.signum() < 0
                || interestRate.compareTo(new BigDecimal("100")) > 0
                || openingDate.getYear() < 1000 || openingDate.getYear() > 9999)
            throw new IllegalArgumentException("Account values are outside supported storage bounds.");
    }

    public final long accountId() { return accountId; }
    public final long customerId() { return customerId; }
    public final AccountNumber number() { return number; }
    public final AccountType type() { return type; }
    public final Money balance() { return balance; }
    public final BigDecimal interestRate() { return interestRate; }
    public final Money minimumBalance() { return minimumBalance; }
    public final LocalDate openingDate() { return openingDate; }
    public final AccountStatus status() { return status; }

    public final String balanceLabel() {
        return switch (type) {
            case LOAN -> "Outstanding principal (not spendable)";
            case DEPOSIT -> "Locked principal";
            case SAVINGS, CURRENT -> "Balance";
        };
    }

    @Override
    public final String toString() {
        return "Account[number=" + number.masked() + ", type=" + type + ", status=" + status + "]";
    }
}
