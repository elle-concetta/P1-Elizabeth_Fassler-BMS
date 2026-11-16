package com.example.banking.model;

/**
 * Closed set of financial-attempt outcomes for transaction_audit.
 * Use name() for future persistence and valueOf(String) for exact lookup;
 * login audit outcomes and ledger statuses are separate contracts.
 */
public enum AuditOutcome {
    SUCCESS,
    REJECTED,
    FAILED,
    ROLLED_BACK
}

