package com.example.banking.exception;

/** No raw exception or identifying audit evidence in the customer message. */
public final class AuditUnavailableException extends BankingException {
    public AuditUnavailableException() {
        super("Audit storage was not confirmed. Further authentication is blocked; "
                + "unsaved evidence is retained only in this running process.");
    }
}

