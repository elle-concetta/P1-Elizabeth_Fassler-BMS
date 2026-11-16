package com.example.banking.exception;

/** Base failure; messages must be safe for customers and contain no SQL/secrets. */
public class BankingException extends RuntimeException {
    public BankingException(String message) {
        super(message);
    }

    public BankingException(String message, Throwable cause) {
        super(message, cause);
    }
}

