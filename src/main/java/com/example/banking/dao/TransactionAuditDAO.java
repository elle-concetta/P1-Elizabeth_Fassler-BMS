package com.example.banking.dao;

import com.example.banking.model.TransactionAudit;

/** Append-only financial-attempt evidence; no update or delete operations. */
public interface TransactionAuditDAO {
    /**
     * Appends one unsaved record in a fresh, independently owned transaction.
     * Invoke only after the primary money transaction has committed or rolled back.
     * Does not change balances, insert ledger entries, or assign an ID to the model.
     *
     * The MySQL writer rejects unsupported timestamp precision/range before JDBC.
     * Retain that record for explicit handling: a rejected time is not a saved audit.
     * Persistence errors can mean an unknown commit result; do not retry blindly
     * or repeat the associated money operation.
     */
    void append(TransactionAudit audit);
}
