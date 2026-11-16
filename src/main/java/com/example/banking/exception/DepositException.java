package com.example.banking.exception;

import com.example.banking.model.TransactionReference;

/** Safe service-generated message and correlation reference; never retains driver errors. */
public final class DepositException extends FinancialOperationException {
    public DepositException(String safeMessage, TransactionReference reference) {
        super(safeMessage, reference);
    }
}

