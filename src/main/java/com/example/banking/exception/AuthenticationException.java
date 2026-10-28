package com.example.banking.exception;

public final class AuthenticationException extends BankingException {
    public AuthenticationException() { super("Invalid username or password."); }
}
