package com.example.banking.dao.jdbc;

import com.example.banking.dao.LoginAuditDAO;
import com.example.banking.exception.PersistenceException;
import com.example.banking.model.LoginAudit;
import com.example.banking.util.ConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Owns a separate short transaction per append. The supplied factory must open
 * a fresh connection, never return a caller's authentication/session connection.
 * Acquires explicit UTC writer sessions. Only whole-second instants in MySQL's portable
 * TIMESTAMP range are accepted; unsupported times fail before acquiring JDBC.
 * Callers must retain rejected evidence, not silently drop it or alter its time.
 */
public final class JdbcLoginAuditDAO implements LoginAuditDAO {
    // Portable MySQL TIMESTAMP(0) range, inclusive, interpreted in UTC.
    private static final Instant MIN_CREATED_AT = Instant.parse("1970-01-01T00:00:01Z");
    private static final Instant MAX_CREATED_AT = Instant.parse("2038-01-19T03:14:07Z");
    private static final String INSERT = """
            INSERT INTO login_audit
                (customer_id, username_attempted, outcome, failure_reason, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;

    private final ConnectionFactory connections;

    public JdbcLoginAuditDAO(ConnectionFactory connections) {
        if (connections == null) {
            throw new IllegalArgumentException("Connection factory is required.");
        }
        this.connections = connections;
    }

    @Override
    public void append(LoginAudit audit) {
        if (audit == null || audit.auditId().isPresent()) {
            throw new IllegalArgumentException("An unsaved login audit record is required.");
        }
        Instant createdAt = audit.createdAt();
        if (createdAt.getNano() != 0 || createdAt.isBefore(MIN_CREATED_AT)
                || createdAt.isAfter(MAX_CREATED_AT)) {
            // The model remains valid. Reject SQL-unrepresentable evidence without
            // rounding, truncating, replacing the time, or acquiring a connection.
            throw new IllegalArgumentException(
                    "Login audit timestamp requires whole seconds from "
                            + "1970-01-01T00:00:01Z through 2038-01-19T03:14:07Z inclusive; "
                            + "no save was attempted.");
        }
        try (Connection connection = connections.getLoginAuditConnection()) {
            connection.setAutoCommit(false);
            try {
                // Close the statement before committing; a close failure rolls back.
                try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
                    if (audit.customerId().isPresent()) {
                        statement.setLong(1, audit.customerId().getAsLong());
                    } else {
                        statement.setNull(1, Types.BIGINT);
                    }
                    statement.setString(2, audit.usernameAttempted());
                    statement.setString(3, audit.outcome().name());
                    if (audit.failureReason().isPresent()) {
                        statement.setString(4, audit.failureReason().orElseThrow());
                    } else {
                        statement.setNull(4, Types.VARCHAR);
                    }
                    statement.setTimestamp(5, Timestamp.from(audit.createdAt()),
                            Calendar.getInstance(TimeZone.getTimeZone("UTC")));
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("Unexpected audit insert count.");
                    }
                }
                connection.commit();
            } catch (SQLException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    // Do not attach driver messages, SQL, or credentials.
                }
                throw failure;
            }
        } catch (SQLException failure) {
            // Discard the entire raw exception graph, including cleanup failures.
            // A commit/connection-close error can occur after a successful save.
            throw new PersistenceException(new SQLException(
                    "Login audit save was not confirmed. Do not retry automatically."));
        }
    }
}
