package com.example.banking.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable ordinary money-operation ledger candidate, not proof of commit.
 * A transfer includes both private snapshots; expose only TransferReceipt to the CLI.
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
    private final BankTransaction destinationCredit;

    private BankTransaction(TransactionType type, TransactionReference reference,
                            Account account, Money amount, Instant time) {
        this.type = Objects.requireNonNull(type);
        this.reference = Objects.requireNonNull(reference);
        Objects.requireNonNull(account);
        this.amount = Objects.requireNonNull(amount);
        this.createdAt = Objects.requireNonNull(time);
        this.destinationCredit = null;
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

    private BankTransaction(BankTransaction debit, BankTransaction credit) {
        this.type = TransactionType.TRANSFER;
        this.reference = debit.reference;
        this.accountId = debit.accountId;
        this.number = debit.number;
        this.amount = debit.amount;
        this.before = debit.before;
        this.after = debit.after;
        this.createdAt = debit.createdAt;
        this.destinationCredit = credit;
    }

    public static BankTransaction transfer(TransactionReference reference, Account source, Account destination,
                                           Money amount, Instant time) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(destination);
        if (source.accountId() == destination.accountId() || source.number().equals(destination.number()))
            throw new IllegalArgumentException("Transfer endpoints must be distinct.");
        return new BankTransaction(withdrawal(reference, source, amount, time),
                deposit(reference, destination, amount, time));
    }

    public TransactionReference reference() { return reference; }
    public TransactionType type() { return type; }
    public long accountId() { return accountId; }
    public long destinationId() {
        if (type == TransactionType.TRANSFER) return destinationCredit.accountId;
        if (type != TransactionType.DEPOSIT) throw new IllegalStateException("Withdrawal has no destination account.");
        return accountId;
    }
    public long sourceId() {
        if (type == TransactionType.DEPOSIT) throw new IllegalStateException("Deposit has no source account.");
        return accountId;
    }
    public AccountNumber destinationNumber() {
        if (type == TransactionType.TRANSFER) return destinationCredit.number;
        if (type == TransactionType.DEPOSIT) return number;
        throw new IllegalStateException("Withdrawal has no destination.");
    }
    public Money destinationBalanceBefore() {
        if (type == TransactionType.TRANSFER) return destinationCredit.before;
        if (type == TransactionType.DEPOSIT) return before;
        throw new IllegalStateException("Withdrawal has no destination.");
    }
    public Money destinationBalanceAfter() {
        if (type == TransactionType.TRANSFER) return destinationCredit.after;
        if (type == TransactionType.DEPOSIT) return after;
        throw new IllegalStateException("Withdrawal has no destination.");
    }
    public AccountNumber number() { return number; }
    public Money amount() { return amount; }
    public Money balanceBefore() { return before; }
    public Money balanceAfter() { return after; }
    public Instant createdAt() { return createdAt; }

    @Override public String toString() { return "BankTransaction[type=" + type + ", number=" + number.masked() + "]"; }
}
