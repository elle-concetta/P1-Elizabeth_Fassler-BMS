package com.example.banking.unit;

import com.example.banking.dao.jdbc.JdbcSecurityAuditDAO;
import com.example.banking.model.SecurityAuditEvent;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SecurityAuditTest {
    private final Instant now = Instant.parse("2026-11-14T12:00:00Z");

    @Test void auditBindsMetadataAndEpochOnlyWithoutOwningTheTransaction() throws Exception {
        var connection = mock(Connection.class);
        var query = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1, 0);
        var event = new SecurityAuditEvent(UUID.randomUUID(), 7, SecurityAuditEvent.Outcome.SUCCESS, null, now);
        var dao = new JdbcSecurityAuditDAO(10);
        dao.append(connection, event);
        verify(connection).prepareStatement(argThat(sql -> sql.contains("INSERT INTO security_audit")
                && sql.contains("'PASSWORD_CHANGE'") && sql.contains("FROM_UNIXTIME(?)")
                && !sql.contains("password_hash")));
        verify(query).setString(1, event.eventId().toString());
        verify(query).setLong(2, 7);
        verify(query).setString(3, "SUCCESS");
        verify(query).setString(4, null);
        verify(query).setLong(5, now.getEpochSecond());
        verify(query).setQueryTimeout(10);
        verify(query).close();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        assertThrows(SQLException.class, () -> dao.append(connection, event));
        assertEquals("SecurityAuditEvent[REDACTED]", event.toString());
    }

    @Test void invalidIdentityPrecisionRangeAndReasonAreRejected() {
        var id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> new SecurityAuditEvent(id, 0, SecurityAuditEvent.Outcome.SUCCESS, null, now));
        for (Instant time : new Instant[]{now.plusNanos(1), Instant.EPOCH, Instant.parse("2038-01-19T03:14:08Z")})
            assertThrows(IllegalArgumentException.class,
                    () -> new SecurityAuditEvent(id, 7, SecurityAuditEvent.Outcome.SUCCESS, null, time));
        assertThrows(IllegalArgumentException.class,
                () -> new SecurityAuditEvent(id, 7, SecurityAuditEvent.Outcome.FAILURE, null, now));
        assertThrows(IllegalArgumentException.class,
                () -> new SecurityAuditEvent(id, 7, SecurityAuditEvent.Outcome.SUCCESS, "PRIVATE", now));
        assertThrows(IllegalArgumentException.class,
                () -> new SecurityAuditEvent(id, 7, SecurityAuditEvent.Outcome.LOCKED, "PRIVATE", now));
        assertDoesNotThrow(() -> new SecurityAuditEvent(id, 7, SecurityAuditEvent.Outcome.LOCKED,
                "TOO_MANY_ATTEMPTS", now));
    }

    @Test void exactTimestampBoundsAndAllowedFailureReasonsRemainValid() {
        for (Instant time : new Instant[]{Instant.parse("1970-01-01T00:00:01Z"),
                Instant.parse("2038-01-19T03:14:07Z")})
            assertDoesNotThrow(() -> new SecurityAuditEvent(UUID.randomUUID(), 7,
                    SecurityAuditEvent.Outcome.FAILURE, "INVALID_CURRENT_PASSWORD", time));
        assertDoesNotThrow(() -> new SecurityAuditEvent(UUID.randomUUID(), 7,
                SecurityAuditEvent.Outcome.LOCKED, "ACCOUNT_UNAVAILABLE", now));
    }
}

