package com.example.banking.exception;

import java.util.Objects;
import java.util.UUID;

/** No driver graph or secrets. Keep the reference until an administrator reconciles it. */
public final class PasswordChangeUnconfirmedException extends BankingException {
    public PasswordChangeUnconfirmedException(UUID reference) {
        super("Password-change result is not confirmed. Reference: " + Objects.requireNonNull(reference)
                + ". Stop; do not retry or restart until this reference is checked in security_audit.");
    }
}

