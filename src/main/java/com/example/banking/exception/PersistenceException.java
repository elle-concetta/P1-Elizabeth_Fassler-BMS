package com.example.banking.exception;

public final class PersistenceException extends BankingException {
    public PersistenceException(Throwable cause) {
        super("The operation could not be completed.", cause);
    }
}

