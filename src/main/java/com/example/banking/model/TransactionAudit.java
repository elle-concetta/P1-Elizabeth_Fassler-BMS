package com.example.banking.model;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/**
 * Immutable financial-attempt record, not a committed ledger entry.
 * Construction does not persist anything or enforce database append-only access.
 * Future writers must append in a separate transaction after commit/rollback.
 */
public final class TransactionAudit {

    private static final Pattern FAILURE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");

    private final Long auditId;
    private final long customerId;
    private final TransactionReference referenceNumber;
    private final TransactionType eventType;
    private final AuditOutcome outcome;
    private final String failureCode;
    private final Instant createdAt;

    /**
     * @param auditId null before insertion; otherwise a positive persisted ID
     * @param customerId positive ID of the authenticated customer
     * @param referenceNumber required for success; optional for other attempts
     * @param eventType required financial operation
     * @param outcome required financial outcome
     * @param failureCode optional application-defined constant, never exception text;
     *                    must be absent on success
     * @param createdAt required timestamp supplied by the caller, not the system clock
     * @throws IllegalArgumentException for an invalid or missing required field
     */
    public TransactionAudit(Long auditId, long customerId,
                            TransactionReference referenceNumber, TransactionType eventType,
                            AuditOutcome outcome, String failureCode, Instant createdAt) {
        if (auditId != null && auditId <= 0) {
            throw new IllegalArgumentException("Audit ID must be positive when present.");
        }
        if (customerId <= 0) {
            throw new IllegalArgumentException("Customer ID must be positive.");
        }
        if (eventType == null || outcome == null || createdAt == null) {
            throw new IllegalArgumentException("Event type, outcome, and timestamp are required.");
        }
        if (outcome == AuditOutcome.SUCCESS && referenceNumber == null) {
            throw new IllegalArgumentException("Successful attempts require a reference.");
        }
        if (outcome == AuditOutcome.SUCCESS && failureCode != null) {
            throw new IllegalArgumentException("Successful attempts cannot have a failure code.");
        }
        if (failureCode != null && !FAILURE_CODE.matcher(failureCode).matches()) {
            // Never include rejected input or a parser/SQL exception in the message.
            throw new IllegalArgumentException(
                    "Failure code must be 1-40 uppercase ASCII letters, digits, or underscores, starting with a letter.");
        }
        this.auditId = auditId;
        this.customerId = customerId;
        this.referenceNumber = referenceNumber;
        this.eventType = eventType;
        this.outcome = outcome;
        this.failureCode = failureCode;
        this.createdAt = createdAt;
    }

    public OptionalLong auditId() {
        return auditId == null ? OptionalLong.empty() : OptionalLong.of(auditId);
    }

    public long customerId() {
        return customerId;
    }

    public Optional<TransactionReference> referenceNumber() {
        return Optional.ofNullable(referenceNumber);
    }

    public TransactionType eventType() {
        return eventType;
    }

    public AuditOutcome outcome() {
        return outcome;
    }

    /**
     * For future persistence, not arbitrary diagnostic logging. Syntax validation
     * cannot establish that a caller supplied a trusted application-defined code.
     */
    public Optional<String> failureCode() {
        return Optional.ofNullable(failureCode);
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** Deliberately omits identifiers, failure codes, and timestamps. */
    @Override
    public String toString() {
        return "TransactionAudit[eventType=" + eventType + ", outcome=" + outcome + "]";
    }
}
