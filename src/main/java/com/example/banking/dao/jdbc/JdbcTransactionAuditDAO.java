package com.example.banking.dao.jdbc;

import com.example.banking.dao.TransactionAuditDAO;
import com.example.banking.exception.PersistenceException;
import com.example.banking.model.TransactionAudit;
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
 * Appends financial evidence without sharing the primary money connection.
 * The factory must open a fresh connection on every call. Each writer session
 * is explicitly set to UTC; no global server settings or schema are changed.
 */
public final class JdbcTransactionAuditDAO implements TransactionAuditDAO {

    private static final Instant MIN_CREATED_AT = Instant.parse("1970-01-01T00:00:01Z");
    private static final Instant MAX_CREATED_AT = Instant.parse("2038-01-19T03:14:07Z");
    private static final String UTC_SESSION = "SET SESSION time_zone = '+00:00'";
    private static final String INSERT = """
            INSERT INTO transaction_audit
                (customer_id, reference_number, event_type, outcome, failure_code, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private final ConnectionFactory connections;
    private final int timeout;

    public JdbcTransactionAuditDAO(ConnectionFactory connections) {
        this(connections, 10);
    }

    public JdbcTransactionAuditDAO(ConnectionFactory connections, int timeout) {
        if (connections == null) {
            throw new IllegalArgumentException("Connection factory is required.");
        }
        this.connections = connections;
        if (timeout <= 0) throw new IllegalArgumentException("Query timeout must be positive.");
        this.timeout = timeout;
    }

    @Override
    public void append(TransactionAudit audit) {
        if (audit == null || audit.auditId().isPresent()) {
            throw new IllegalArgumentException("An unsaved transaction audit record is required.");
        }
        Instant createdAt = audit.createdAt();
        // The TIMESTAMP column stores whole seconds, not arbitrary Instants.
        // Reject before JDBC rather than silently rounding or changing evidence.
        if (createdAt.getNano() != 0 || createdAt.isBefore(MIN_CREATED_AT)
                || createdAt.isAfter(MAX_CREATED_AT)) {
            throw new IllegalArgumentException(
                    "Transaction audit timestamp requires whole seconds from "
                            + "1970-01-01T00:00:01Z through 2038-01-19T03:14:07Z inclusive; "
                            + "no save was attempted.");
        }

        try (Connection connection = connections.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement session = connection.prepareStatement(UTC_SESSION)) {
                    session.setQueryTimeout(timeout);
                    session.execute();
                }
                // A statement-close failure must happen before the commit.
                try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
                    statement.setQueryTimeout(timeout);
                    statement.setLong(1, audit.customerId());
                    if (audit.referenceNumber().isPresent()) {
                        statement.setString(2, audit.referenceNumber().orElseThrow().value());
                    } else {
                        statement.setNull(2, Types.VARCHAR);
                    }
                    statement.setString(3, audit.eventType().name());
                    statement.setString(4, audit.outcome().name());
                    if (audit.failureCode().isPresent()) {
                        statement.setString(5, audit.failureCode().orElseThrow());
                    } else {
                        statement.setNull(5, Types.VARCHAR);
                    }
                    statement.setTimestamp(6, Timestamp.from(createdAt),
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
                    // The public error must not retain raw driver details.
                }
                throw failure;
            }
        } catch (SQLException failure) {
            // A commit or connection-close error may follow a durable save.
            // Discard the raw exception graph, including cleanup failures.
            throw new PersistenceException(new SQLException(
                    "Transaction audit save was not confirmed. Do not retry automatically."));
        }
    }
}
