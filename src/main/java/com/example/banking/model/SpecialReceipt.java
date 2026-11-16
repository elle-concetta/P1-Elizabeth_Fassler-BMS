package com.example.banking.model;

import java.util.Objects;

/** Confirmed dedicated operation; endpoints are always owned by this session. */
public record SpecialReceipt(TransactionReference reference, String message, Account account,
                             BankTransaction movement) {
    public SpecialReceipt {
        Objects.requireNonNull(reference);
        Objects.requireNonNull(message);
        Objects.requireNonNull(account);
    }
    @Override public String toString() { return "SpecialReceipt[type=" + account.type() + ", status=" + account.status() + "]"; }
}

