package com.example.banking.dao;

import com.example.banking.model.LoginAudit;

/** Append-only authentication/session evidence; no update/delete operations. */
public interface LoginAuditDAO {
    /**
     * Saves one unsaved record using a fresh, independently owned transaction.
     * Call after any primary authentication transaction has completed.
     * A persistence failure may mean the commit result is unknown: never retry blindly.
     * The MySQL writer rejects unsupported timestamp precision/range with an
     * IllegalArgumentException before opening a connection; no save is attempted.
     * Retain that rejected evidence for explicit caller handling, not silent loss.
     * Implementations must only use IllegalArgumentException for pre-write
     * rejection; never use it for a possibly attempted write.
     * Future login callers can use
     * {@link com.example.banking.security.LoginAuditDelivery} for a one-shot
     * handoff that retains evidence and distinguishes these delivery outcomes.
     * Does not mutate the record or assign it an ID.
     */
    void append(LoginAudit audit);
}
