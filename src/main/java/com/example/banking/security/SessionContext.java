package com.example.banking.security;

import java.time.Instant;

/** In-memory identity and activity only; no credentials or password hashes. */
public record SessionContext(long customerId, Instant createdAt, Instant lastActivity) {
    public SessionContext {
        if (customerId <= 0 || createdAt == null || lastActivity == null
                || lastActivity.isBefore(createdAt)) {
            throw new IllegalArgumentException("Invalid session state.");
        }
    }
}
