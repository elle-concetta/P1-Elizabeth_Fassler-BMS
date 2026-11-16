package com.example.banking.model;

import java.time.Instant;
import java.util.Objects;

/** Sender-facing view: deliberately no recipient ID, owner, or balance accessors. */
public final class TransferReceipt {
    private final BankTransaction transaction;

    private TransferReceipt(BankTransaction transaction) {
        this.transaction = Objects.requireNonNull(transaction);
        if (transaction.type() != TransactionType.TRANSFER)
            throw new IllegalArgumentException("A transfer candidate is required.");
    }

    /** Construction alone is not commit evidence; the service returns this after confirmed delivery. */
    public static TransferReceipt from(BankTransaction transaction) { return new TransferReceipt(transaction); }

    public TransactionReference reference() { return transaction.reference(); }
    public long sourceId() { return transaction.sourceId(); }
    public AccountNumber sourceNumber() { return transaction.number(); }
    public AccountNumber destinationNumber() { return transaction.destinationNumber(); }
    public Money amount() { return transaction.amount(); }
    public Money sourceBalanceBefore() { return transaction.balanceBefore(); }
    public Money sourceBalanceAfter() { return transaction.balanceAfter(); }
    public Instant createdAt() { return transaction.createdAt(); }

    @Override public String toString() {
        return "TransferReceipt[source=" + sourceNumber().masked()
                + ", destination=" + destinationNumber().masked() + "]";
    }
}

