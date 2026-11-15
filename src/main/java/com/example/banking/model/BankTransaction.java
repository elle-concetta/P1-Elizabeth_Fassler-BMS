package com.example.banking.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable deposit ledger candidate/receipt. Construction is not proof of commit.
 * Other operation factories and transaction history are deliberately not offered yet.
 */
public final class BankTransaction {
    private final TransactionReference reference;
    private final long destinationId;
    private final AccountNumber number;
    private final Money amount;
    private final Money before;
    private final Money after;
    private final Instant createdAt;

    private BankTransaction(TransactionReference reference, Account account, Money amount, Instant time) {
        this.reference = Objects.requireNonNull(reference);
        Objects.requireNonNull(account);
        this.amount = Objects.requireNonNull(amount);
        this.createdAt = Objects.requireNonNull(time);
        if (!amount.isPositive() || account.status() != AccountStatus.ACTIVE
                || (account.type() != AccountType.SAVINGS && account.type() != AccountType.CURRENT))
            throw new IllegalArgumentException("A positive deposit into an active ordinary account is required.");
        this.destinationId = account.accountId();
        this.number = account.number();
        this.before = account.balance();
        this.after = before.add(amount); // exact arithmetic; never round or overflow storage
    }

    public static BankTransaction deposit(TransactionReference reference, Account account, Money amount, Instant time) {
        return new BankTransaction(reference, account, amount, time);
    }

    public TransactionReference reference() { return reference; }
    public long destinationId() { return destinationId; }
    public AccountNumber number() { return number; }
    public Money amount() { return amount; }
    public Money balanceBefore() { return before; }
    public Money balanceAfter() { return after; }
    public Instant createdAt() { return createdAt; }

    @Override public String toString() { return "BankTransaction[type=DEPOSIT, number=" + number.masked() + "]"; }
}
