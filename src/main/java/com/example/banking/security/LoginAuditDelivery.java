package com.example.banking.security;

import com.example.banking.dao.LoginAuditDAO;
import com.example.banking.exception.PersistenceException;
import com.example.banking.model.LoginAudit;

/**
 * One-shot audit delivery, separate from authentication. Call only after the
 * primary authentication/session transaction has finished.
 *
 * <p>The caller must keep the returned result until explicitly handled. This is
 * an in-memory handoff, not a durable queue: process exit loses unsaved evidence.
 * No fallback storage, retry, timestamp correction, or authentication action is
 * performed here. Never put the evidence into logs or user-facing responses.
 */
public final class LoginAuditDelivery {
    private final LoginAuditDAO audits;

    public LoginAuditDelivery(LoginAuditDAO audits) {
        if (audits == null) {
            throw new IllegalArgumentException("Login audit writer is required.");
        }
        this.audits = audits;
    }

    public enum Status {
        /** The writer returned normally, confirming the save. */
        SAVED,
        /** The writer rejected the evidence before attempting any write. */
        REJECTED_NO_WRITE,
        /** The write may have committed; reconciliation is required before redelivery. */
        SAVE_UNCERTAIN
    }

    /**
     * Retains the exact immutable evidence, including unsupported Instants.
     * A successful authentication outcome in evidence does not mean delivery
     * succeeded; only SAVED confirms persistence. Raw exceptions are not retained.
     */
    public record Result(LoginAudit evidence, Status status) {
        public Result {
            if (evidence == null || status == null) {
                throw new IllegalArgumentException("Evidence and delivery status are required.");
            }
        }

        @Override
        public String toString() {
            return "LoginAuditDelivery.Result[status=" + status + "]";
        }
    }

    /**
     * Attempts exactly one append. Per the LoginAuditDAO contract,
     * IllegalArgumentException means no write was attempted; PersistenceException
     * means save not confirmed, even if rollback was attempted.
     *
     * <p>Missing or already-saved records are caller errors, not rejected
     * timestamp evidence. Unexpected programming errors propagate rather than
     * being mislabeled as delivery results.
     */
    public Result deliver(LoginAudit evidence) {
        if (evidence == null || evidence.auditId().isPresent()) {
            throw new IllegalArgumentException("Unsaved login audit evidence is required.");
        }
        Status status;
        try {
            audits.append(evidence);
            status = Status.SAVED;
        } catch (IllegalArgumentException rejected) {
            status = Status.REJECTED_NO_WRITE;
        } catch (PersistenceException uncertain) {
            status = Status.SAVE_UNCERTAIN;
        }
        return new Result(evidence, status);
    }
}
