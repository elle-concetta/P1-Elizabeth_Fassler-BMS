package com.example.banking.exception;

public final class AccountNotEligibleException extends BankingException {
    public AccountNotEligibleException() { super("Account is not eligible for this operation."); }
}
