package com.example.banking.model;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/**
 * Immutable authentication/session outcome, not an authentication operation.
 * Construction does not persist a row or enforce database append-only access.
 * Never supply passwords, hashes, tokens, or exception messages.
 */
public final class LoginAudit {

    private static final Pattern FAILURE_REASON = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");

    private final Long auditId;
    private final Long customerId;
    private final String usernameAttempted;
    private final LoginOutcome outcome;
    private final String failureReason;
    private final Instant createdAt;

    /**
     * @param auditId null before insertion; otherwise positive
     * @param customerId null for an unknown customer; otherwise positive
     * @param usernameAttempted exact attempted username, including blank input;
     *                          non-null and at most 80 Unicode code points;
     *                          oversized failures use the explicit
     *                          {@link com.example.banking.security.LoginAuditCapture}
     *                          marker policy instead, never silent truncation
     * @param outcome required authentication/session outcome
     * @param failureReason optional application-defined uppercase code (1-40
     *                      ASCII characters), only for FAILURE or LOCKED;
     *                      never credentials or raw exception text
     * @param createdAt required caller-supplied instant, not the system clock
     */
    public LoginAudit(Long auditId, Long customerId, String usernameAttempted,
                      LoginOutcome outcome, String failureReason, Instant createdAt) {
        if (auditId != null && auditId <= 0) {
            throw new IllegalArgumentException("Audit ID must be positive when present.");
        }
        if (customerId != null && customerId <= 0) {
            throw new IllegalArgumentException("Customer ID must be positive when present.");
        }
        if (usernameAttempted == null
                || usernameAttempted.codePointCount(0, usernameAttempted.length()) > 80) {
            throw new IllegalArgumentException("Attempted username must be non-null and at most 80 characters.");
        }
        if (outcome == null || createdAt == null) {
            throw new IllegalArgumentException("Outcome and timestamp are required.");
        }
        if (failureReason != null) {
            if (outcome != LoginOutcome.FAILURE && outcome != LoginOutcome.LOCKED) {
                throw new IllegalArgumentException("Only failed or locked attempts may have a failure reason.");
            }
            if (!FAILURE_REASON.matcher(failureReason).matches()) {
                throw new IllegalArgumentException(
                        "Failure reason must be a 1-40 character uppercase ASCII code starting with a letter.");
            }
        }
        this.auditId = auditId;
        this.customerId = customerId;
        this.usernameAttempted = usernameAttempted;
        this.outcome = outcome;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
    }

    public OptionalLong auditId() {
        return auditId == null ? OptionalLong.empty() : OptionalLong.of(auditId);
    }

    public OptionalLong customerId() {
        return customerId == null ? OptionalLong.empty() : OptionalLong.of(customerId);
    }

    /** For controlled persistence, not diagnostic logging. */
    public String usernameAttempted() {
        return usernameAttempted;
    }

    public LoginOutcome outcome() {
        return outcome;
    }

    /** Syntax cannot establish that a code came from trusted application constants. */
    public Optional<String> failureReason() {
        return Optional.ofNullable(failureReason);
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** Deliberately excludes identity, username, reason, and timestamp. */
    @Override
    public String toString() {
        return "LoginAudit[outcome=" + outcome + "]";
    }
}
