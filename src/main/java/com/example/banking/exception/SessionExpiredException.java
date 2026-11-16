package com.example.banking.exception;

public final class SessionExpiredException extends BankingException {
    public SessionExpiredException() { super("Session expired. Please log in again."); }
}

