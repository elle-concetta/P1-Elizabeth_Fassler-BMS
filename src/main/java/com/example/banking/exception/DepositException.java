package com.example.banking.exception;

import com.example.banking.model.TransactionReference;

/** Safe service-generated message and correlation reference; never retains driver errors. */
public final class DepositException extends BankingException {
    public DepositException(String safeMessage, TransactionReference reference) {
        super(safeMessage + " Reference: " + reference.value());
    }
}

