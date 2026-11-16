package com.example.banking.exception;

import com.example.banking.model.TransactionReference;

/** Safe financial result and correlation reference, never a driver exception graph. */
public class FinancialOperationException extends BankingException {
    public FinancialOperationException(String safeMessage, TransactionReference reference) {
        super(safeMessage + " Reference: " + reference.value());
    }
}


