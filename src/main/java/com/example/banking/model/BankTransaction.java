package com.example.banking.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable ordinary deposit/withdrawal candidate or receipt, not proof of commit.
 * Transfers and transaction history are deliberately not offered yet.
 */
public final class BankTransaction {
    private final TransactionReference reference;
    private final TransactionType type;
    private final long accountId;
    private final AccountNumber number;
    private final Money amount;
    private final Money before;
    private final Money after;
    private final Instant createdAt;

    private BankTransaction(TransactionType type, TransactionReference reference,
                            Account account, Money amount, Instant time) {
        this.type = Objects.requireNonNull(type);
        this.reference = Objects.requireNonNull(reference);
        Objects.requireNonNull(account);
        this.amount = Objects.requireNonNull(amount);
        this.createdAt = Objects.requireNonNull(time);
        if (!amount.isPositive() || account.status() != AccountStatus.ACTIVE
                || (account.type() != AccountType.SAVINGS && account.type() != AccountType.CURRENT))
            throw new IllegalArgumentException("A positive amount and active ordinary account are required.");
        this.accountId = account.accountId();
        this.number = account.number();
        this.before = account.balance();
        this.after = type == TransactionType.DEPOSIT ? before.add(amount) : before.subtract(amount);
        if (type == TransactionType.WITHDRAWAL
                && (after.compareTo(Money.ZERO) < 0 || after.compareTo(account.minimumBalance()) < 0))
            throw new IllegalArgumentException("Withdrawal must preserve available funds and the stored minimum balance.");
    }

    public static BankTransaction deposit(TransactionReference reference, Account account, Money amount, Instant time) {
        return new BankTransaction(TransactionType.DEPOSIT, reference, account, amount, time);
    }

    public static BankTransaction withdrawal(TransactionReference reference, Account account, Money amount, Instant time) {
        return new BankTransaction(TransactionType.WITHDRAWAL, reference, account, amount, time);
    }

    public TransactionReference reference() { return reference; }
    public TransactionType type() { return type; }
    public long accountId() { return accountId; }
    public long destinationId() {
        if (type != TransactionType.DEPOSIT) throw new IllegalStateException("Withdrawal has no destination account.");
        return accountId;
    }
    public long sourceId() {
        if (type != TransactionType.WITHDRAWAL) throw new IllegalStateException("Deposit has no source account.");
        return accountId;
    }
    public AccountNumber number() { return number; }
    public Money amount() { return amount; }
    public Money balanceBefore() { return before; }
    public Money balanceAfter() { return after; }
    public Instant createdAt() { return createdAt; }

    @Override public String toString() { return "BankTransaction[type=" + type + ", number=" + number.masked() + "]"; }
}
