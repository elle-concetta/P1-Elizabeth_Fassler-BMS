package com.example.banking.security;

import com.example.banking.exception.AuthenticationException;
import com.example.banking.exception.SessionExpiredException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** One local CLI session. Expiry is checked BEFORE activity is refreshed. */
public final class SessionManager {
    private final Clock clock;
    private final Duration idleTimeout;
    private SessionContext current;

    public SessionManager(Clock clock, Duration idleTimeout) {
        this.clock = Objects.requireNonNull(clock);
        if (idleTimeout == null || idleTimeout.isNegative() || idleTimeout.isZero()) {
            throw new IllegalArgumentException("A positive idle timeout is required.");
        }
        this.idleTimeout = idleTimeout;
    }

    public synchronized SessionContext start(long customerId) {
        if (current != null) throw new IllegalStateException("End the existing session before logging in.");
        Instant now = clock.instant();
        return current = new SessionContext(customerId, now, now);
    }

    public synchronized SessionContext requireActive() {
        if (current == null) throw new AuthenticationException();
        Instant now = clock.instant();
        if (now.isBefore(current.lastActivity())
                || !now.isBefore(current.lastActivity().plus(idleTimeout))) {
            current = null;
            throw new SessionExpiredException();
        }
        current = new SessionContext(current.customerId(), current.createdAt(), now);
        return current;
    }

    public synchronized Optional<SessionContext> end() {
        SessionContext ended = current;
        current = null;
        return Optional.ofNullable(ended);
    }
}

