package com.example.banking.service;

import com.example.banking.dao.CustomerDAO;
import com.example.banking.dao.SecurityAuditDAO;
import com.example.banking.exception.*;
import com.example.banking.model.*;
import com.example.banking.model.policy.PasswordPolicy;
import com.example.banking.security.PasswordHasher;
import com.example.banking.util.ConnectionFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Reauthentication, replacement and evidence under one customer-row lock; never retries. */
public final class PasswordChangeService {
    public enum Result { SUCCESS, REJECTED, LOCKED }
    private final ConnectionFactory connections;
    private final CustomerDAO customers;
    private final SecurityAuditDAO audit;
    private final PasswordHasher passwords;
    private final Supplier<Customer> identity;
    private final Clock clock;
    private final int maxFailures;
    private final Duration lockDuration;
    private final int timeout;
    private final String dummyHash;

    public PasswordChangeService(ConnectionFactory connections, CustomerDAO customers,
                                 SecurityAuditDAO audit, PasswordHasher passwords, Supplier<Customer> identity,
                                 Clock clock, int maxFailures, Duration lockDuration, int timeout, String dummyHash) {
        this.connections = Objects.requireNonNull(connections);
        this.customers = Objects.requireNonNull(customers);
        this.audit = Objects.requireNonNull(audit);
        this.passwords = Objects.requireNonNull(passwords);
        this.identity = Objects.requireNonNull(identity);
        this.clock = Objects.requireNonNull(clock);
        this.lockDuration = Objects.requireNonNull(lockDuration);
        this.dummyHash = Objects.requireNonNull(dummyHash);
        if (maxFailures <= 0 || timeout <= 0 || lockDuration.isNegative() || lockDuration.isZero())
            throw new IllegalArgumentException("Positive password-change settings required.");
        this.maxFailures = maxFailures;
        this.timeout = timeout;
    }

    public Result change(char[] current, char[] replacement, char[] confirmation) {
        UUID reference = UUID.randomUUID();
        try {
            Customer owner = identity.get(); // Session/expiry/pending-evidence check AFTER form entry.
            if (!PasswordPolicy.hasSupportedLength(current))
                throw new ValidationException("Enter your current password.");
            new PasswordPolicy().validate(owner.username(), replacement);
            if (!Arrays.equals(replacement, confirmation))
                throw new ValidationException("New password and confirmation do not match.");
            Instant now = clock.instant();
            // Validate the event boundary before any database work.
            new SecurityAuditEvent(reference, owner.customerId(), SecurityAuditEvent.Outcome.SUCCESS, null, now);
            try (Connection connection = connections.getConnection()) {
                try {
                    connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                    connection.setAutoCommit(false);
                    try (var statement = connection.createStatement()) {
                        statement.setQueryTimeout(timeout);
                        statement.execute("SET SESSION time_zone = '+00:00'");
                        statement.execute("SET SESSION innodb_lock_wait_timeout = " + timeout);
                    }
                    var row = customers.lockByUsername(connection, owner.username())
                            .orElseThrow(AuthenticationException::new);
                    if (row.customer().customerId() != owner.customerId())
                        throw new AuthenticationException();
                    boolean blocked = row.customer().status() != CustomerStatus.ACTIVE
                            || row.lockedUntil() != null && now.isBefore(row.lockedUntil());
                    boolean verified = passwords.verify(current, blocked ? dummyHash : row.hash().value());
                    Result result;
                    SecurityAuditEvent.Outcome outcome;
                    String reason;
                    if (blocked) {
                        result = Result.LOCKED;
                        outcome = SecurityAuditEvent.Outcome.LOCKED;
                        reason = "ACCOUNT_UNAVAILABLE";
                    } else if (!verified) {
                        int previous = row.lockedUntil() == null ? row.failedCount() : 0;
                        int failures = Math.min(Math.max(0, previous), maxFailures - 1) + 1;
                        Instant until = failures == maxFailures ? now.plus(lockDuration) : null;
                        if (until != null && (until.getNano() != 0
                                || until.isAfter(Instant.parse("2038-01-19T03:14:07Z"))))
                            throw new IllegalStateException("Lock expiry is outside the supported SQL boundary.");
                        customers.updateLoginState(connection, owner.customerId(), failures, until);
                        result = until == null ? Result.REJECTED : Result.LOCKED;
                        outcome = until == null ? SecurityAuditEvent.Outcome.FAILURE : SecurityAuditEvent.Outcome.LOCKED;
                        reason = until == null ? "INVALID_CURRENT_PASSWORD" : "TOO_MANY_ATTEMPTS";
                    } else {
                        if (Arrays.equals(current, replacement))
                            throw new ValidationException("Choose a password different from your current password.");
                        PasswordHash hash = new PasswordHash(passwords.hash(replacement));
                        customers.updatePasswordHash(connection, owner.customerId(), hash);
                        var saved = customers.lockByUsername(connection, owner.username())
                                .orElseThrow(AuthenticationException::new);
                        // Encoded storage read-back, not a substitute for credential verification.
                        if (saved.customer().customerId() != owner.customerId()
                                || !hash.equals(saved.hash()))
                            throw new SQLException("Password storage read-back not confirmed.");
                        customers.updateLoginState(connection, owner.customerId(), 0, null);
                        result = Result.SUCCESS;
                        outcome = SecurityAuditEvent.Outcome.SUCCESS;
                        reason = null;
                    }
                    audit.append(connection, new SecurityAuditEvent(reference, owner.customerId(), outcome, reason, now));
                    connection.commit();
                    return result;
                } catch (SQLException | RuntimeException | Error failure) {
                    try { connection.rollback(); } catch (SQLException ignored) { /* Never expose driver data. */ }
                    throw failure;
                }
            } catch (ValidationException invalid) {
                // Strip possible driver-close suppressed exceptions from this controlled message.
                throw new ValidationException(invalid.getMessage());
            } catch (AuthenticationException denied) {
                throw new AuthenticationException();
            } catch (SQLException | RuntimeException failure) {
                throw new PasswordChangeUnconfirmedException(reference);
            }
        } finally {
            clear(current); clear(replacement); clear(confirmation);
        }
    }

    private static void clear(char[] value) {
        if (value != null) Arrays.fill(value, '\0');
    }
}

