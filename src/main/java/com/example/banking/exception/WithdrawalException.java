package com.example.banking.exception;

import com.example.banking.model.TransactionReference;

/** A rejected, failed or unconfirmed withdrawal; never an instruction to retry. */
public final class WithdrawalException extends FinancialOperationException {
    public WithdrawalException(String safeMessage, TransactionReference reference) {
        super(safeMessage, reference);
    }
}


