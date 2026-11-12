package com.example.banking.model;

/**
 * Closed set of authentication/session outcomes for login_audit.
 * Use name() for future persistence and valueOf(String) for exact lookup;
 * financial audit outcomes and ledger statuses are separate contracts.
 */
public enum LoginOutcome {
    SUCCESS,
    FAILURE,
    LOCKED,
    TIMEOUT,
    LOGOUT
}

