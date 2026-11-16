package com.example.banking.exception;

import com.example.banking.model.TransactionReference;

/** Safe transfer result, never a raw driver exception or recipient's private data. */
public final class TransferException extends FinancialOperationException {
    public TransferException(String safeMessage, TransactionReference reference) {
        super(safeMessage, reference);
    }
}

