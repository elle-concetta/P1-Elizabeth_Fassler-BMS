package com.example.banking.exception;

public final class AuthorizationException extends BankingException {
    public AuthorizationException() { super("Account not found."); }
}
