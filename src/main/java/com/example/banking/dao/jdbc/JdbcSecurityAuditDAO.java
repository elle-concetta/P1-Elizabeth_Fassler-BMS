package com.example.banking.dao.jdbc;

import com.example.banking.dao.SecurityAuditDAO;
import com.example.banking.model.SecurityAuditEvent;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Append only. The password-change connection explicitly uses UTC. */
public final class JdbcSecurityAuditDAO implements SecurityAuditDAO {
    private final int timeout;

    public JdbcSecurityAuditDAO(int timeout) {
        if (timeout <= 0) throw new IllegalArgumentException("Positive timeout required.");
        this.timeout = timeout;
    }

    @Override
    public void append(Connection connection, SecurityAuditEvent event) throws SQLException {
        Objects.requireNonNull(event);
        try (var query = connection.prepareStatement("""
                INSERT INTO security_audit
                    (event_id, customer_id, event_type, outcome, failure_reason, created_at)
                VALUES (?, ?, 'PASSWORD_CHANGE', ?, ?, FROM_UNIXTIME(?))
                """)) {
            query.setQueryTimeout(timeout);
            query.setString(1, event.eventId().toString());
            query.setLong(2, event.customerId());
            query.setString(3, event.outcome().name());
            query.setString(4, event.failureReason());
            query.setLong(5, event.createdAt().getEpochSecond());
            if (query.executeUpdate() != 1) throw new SQLException("Security audit insert not confirmed.");
        }
    }
}

