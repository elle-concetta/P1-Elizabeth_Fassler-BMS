package com.example.banking.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Password-change metadata only: never credentials, hashes or free-form input. */
public record SecurityAuditEvent(UUID eventId, long customerId, Outcome outcome,
                                 String failureReason, Instant createdAt) {
    public enum Outcome { SUCCESS, FAILURE, LOCKED }

    public SecurityAuditEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(createdAt);
        if (customerId <= 0 || createdAt.getNano() != 0
                || createdAt.isBefore(Instant.parse("1970-01-01T00:00:01Z"))
                || createdAt.isAfter(Instant.parse("2038-01-19T03:14:07Z"))) {
            throw new IllegalArgumentException("Invalid security audit identity or time.");
        }
        boolean valid = switch (outcome) {
            case SUCCESS -> failureReason == null;
            case FAILURE -> "INVALID_CURRENT_PASSWORD".equals(failureReason);
            case LOCKED -> "ACCOUNT_UNAVAILABLE".equals(failureReason)
                    || "TOO_MANY_ATTEMPTS".equals(failureReason);
        };
        if (!valid) throw new IllegalArgumentException("Invalid security audit reason.");
    }

    @Override public String toString() { return "SecurityAuditEvent[REDACTED]"; }
}
