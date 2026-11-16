package com.example.banking.model;

import com.example.banking.validation.TransactionValidator;
import java.time.Instant;
import java.util.Objects;

/** Selected-account projection: no counterparty identity, account number or balance. */
public record HistoryEntry(long transactionId, TransactionReference reference, TransactionType type,
                           Money amount, Direction direction, TransactionStatus status, Instant createdAt) {
    public enum Direction { CREDIT, DEBIT }

    public HistoryEntry {
        Objects.requireNonNull(reference);
        Objects.requireNonNull(type);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(direction);
        Objects.requireNonNull(status);
        if (transactionId <= 0 || !amount.isPositive() || !TransactionValidator.supportedTime(createdAt)
                || (type == TransactionType.DEPOSIT && direction != Direction.CREDIT)
                || (type == TransactionType.WITHDRAWAL && direction != Direction.DEBIT))
            throw new IllegalArgumentException("Stored history entry is invalid.");
    }
}

