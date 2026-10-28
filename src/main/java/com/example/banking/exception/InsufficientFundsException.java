package com.example.banking.exception;

public final class InsufficientFundsException extends BankingException {
    public InsufficientFundsException() { super("Insufficient funds."); }
}
