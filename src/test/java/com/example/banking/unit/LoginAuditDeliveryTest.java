package com.example.banking.unit;

import com.example.banking.dao.LoginAuditDAO;
import com.example.banking.dao.jdbc.JdbcLoginAuditDAO;
import com.example.banking.exception.PersistenceException;
import com.example.banking.model.LoginAudit;
import com.example.banking.model.LoginOutcome;
import com.example.banking.security.LoginAuditCapture;
import com.example.banking.security.LoginAuditDelivery;
import com.example.banking.util.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.banking.security.LoginAuditDelivery.Status.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real delivery + JDBC writer, mocked JDBC only; no live database writes. */
class LoginAuditDeliveryTest {
    private static final Instant WHOLE_SECOND = Instant.parse("2026-11-11T12:13:14Z");

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-11-11T12:13:14.123Z",
            "2026-11-11T12:13:14.123456Z",
            "2026-11-11T12:13:14.123456789Z",
            "1970-01-01T00:00:00Z",
            "2038-01-19T03:14:08Z",
            "-1000000000-01-01T00:00:00Z",
            "+1000000000-12-31T23:59:59.999999999Z"
    })
    void retainsExactRejectedClockEvidenceWithoutOpeningJdbc(String text) {
        Clock clock = Clock.fixed(Instant.parse(text), ZoneOffset.UTC);
        Instant capturedTime = Instant.now(clock);
        LoginAudit evidence = evidence(capturedTime);
        ConnectionFactory connections = mock(ConnectionFactory.class);
        var delivery = new LoginAuditDelivery(new JdbcLoginAuditDAO(connections));

        var result = delivery.deliver(evidence);

        assertEquals(REJECTED_NO_WRITE, result.status());
        assertSame(evidence, result.evidence());
        assertSame(capturedTime, result.evidence().createdAt());
        assertEquals(" Alice \n", result.evidence().usernameAttempted());
        assertEquals(LoginOutcome.SUCCESS, result.evidence().outcome());
        assertTrue(result.evidence().auditId().isEmpty());
        verifyNoInteractions(connections);
    }

    @Test
    void reportsSavedOnlyAfterOneNormalWriterReturn() throws Exception {
        ConnectionFactory connections = mock(ConnectionFactory.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connections.getLoginAuditConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        LoginAudit evidence = evidence(WHOLE_SECOND);

        var result = new LoginAuditDelivery(new JdbcLoginAuditDAO(connections)).deliver(evidence);

        assertEquals(SAVED, result.status());
        assertSame(evidence, result.evidence());
        assertTrue(evidence.auditId().isEmpty());
        verify(connections, times(1)).getLoginAuditConnection();
        verify(statement, times(1)).executeUpdate();
        verify(statement).setTimestamp(eq(5), eq(Timestamp.from(WHOLE_SECOND)), argThat(
                calendar -> "UTC".equals(calendar.getTimeZone().getID())));
        verify(connection, times(1)).commit();
    }

    @ParameterizedTest
    @ValueSource(strings = {"acquire", "insert", "commit", "close"})
    void uncertainJdbcFailuresNeverClaimSavedOrRetry(String failurePoint) throws Exception {
        ConnectionFactory connections = mock(ConnectionFactory.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        SQLException raw = new SQLException("private driver detail");
        if (failurePoint.equals("acquire")) {
            when(connections.getLoginAuditConnection()).thenThrow(raw);
        } else {
            when(connections.getLoginAuditConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            when(statement.executeUpdate()).thenReturn(1);
            switch (failurePoint) {
                case "insert" -> when(statement.executeUpdate()).thenThrow(raw);
                case "commit" -> doThrow(raw).when(connection).commit();
                case "close" -> doThrow(raw).when(connection).close();
                default -> fail("Unknown failure point");
            }
        }
        LoginAudit evidence = evidence(WHOLE_SECOND);

        var result = new LoginAuditDelivery(new JdbcLoginAuditDAO(connections)).deliver(evidence);

        assertEquals(SAVE_UNCERTAIN, result.status());
        assertSame(evidence, result.evidence());
        assertFalse(result.toString().contains("private driver detail"));
        verify(connections, times(1)).getLoginAuditConnection();
        verify(statement, atMostOnce()).executeUpdate();
        verify(connection, atMostOnce()).commit();
    }

    @Test
    void oversizedMarkerUsesSameDeliveryPolicyWithoutRestoringInput() {
        Instant time = WHOLE_SECOND.plusNanos(123456789);
        LoginAudit evidence = LoginAuditCapture.failedOversizedUsername("x".repeat(81), time);
        ConnectionFactory connections = mock(ConnectionFactory.class);

        var result = new LoginAuditDelivery(new JdbcLoginAuditDAO(connections)).deliver(evidence);

        assertEquals(REJECTED_NO_WRITE, result.status());
        assertSame(evidence, result.evidence());
        assertSame(time, result.evidence().createdAt());
        assertEquals("[OVERSIZED_USERNAME]", result.evidence().usernameAttempted());
        assertEquals("USERNAME_TOO_LONG", result.evidence().failureReason().orElseThrow());
        assertTrue(result.evidence().customerId().isEmpty());
        assertEquals(LoginOutcome.FAILURE, result.evidence().outcome());
        verifyNoInteractions(connections);
    }

    @ParameterizedTest
    @ValueSource(strings = {"reject", "uncertain", "saved"})
    void completedPrimaryOperationIsNotRepeatedByDelivery(String outcome) {
        LoginAuditDAO writer = mock(LoginAuditDAO.class);
        LoginAudit evidence = evidence(WHOLE_SECOND);
        if (outcome.equals("reject")) {
            doThrow(new IllegalArgumentException("private rejection")).when(writer).append(evidence);
        } else if (outcome.equals("uncertain")) {
            doThrow(new PersistenceException(new SQLException("private failure")))
                    .when(writer).append(evidence);
        }
        // Simulates the future caller's sequence, not an implemented AuthService.
        AtomicInteger completedPrimaryOperations = new AtomicInteger();
        completedPrimaryOperations.incrementAndGet();
        var result = new LoginAuditDelivery(writer).deliver(evidence);

        assertEquals(1, completedPrimaryOperations.get());
        assertSame(evidence, result.evidence());
        assertEquals(switch (outcome) {
            case "reject" -> REJECTED_NO_WRITE;
            case "uncertain" -> SAVE_UNCERTAIN;
            default -> SAVED;
        }, result.status());
        verify(writer, times(1)).append(evidence);
        verifyNoMoreInteractions(writer);
        assertEquals("LoginAuditDelivery.Result[status=" + result.status() + "]", result.toString());
    }

    @Test
    void rejectsCallerErrorsBeforeWriterAndDoesNotSwallowProgrammingFailures() {
        LoginAuditDAO writer = mock(LoginAuditDAO.class);
        var delivery = new LoginAuditDelivery(writer);
        assertThrows(IllegalArgumentException.class, () -> new LoginAuditDelivery(null));
        assertThrows(IllegalArgumentException.class, () -> delivery.deliver(null));
        LoginAudit saved = new LoginAudit(1L, null, "", LoginOutcome.FAILURE, null, WHOLE_SECOND);
        assertThrows(IllegalArgumentException.class, () -> delivery.deliver(saved));
        verifyNoInteractions(writer);

        LoginAudit evidence = evidence(WHOLE_SECOND);
        doThrow(new IllegalStateException("programming error")).when(writer).append(evidence);
        assertThrows(IllegalStateException.class, () -> delivery.deliver(evidence));
        verify(writer).append(evidence);
        verifyNoMoreInteractions(writer);
        assertThrows(IllegalArgumentException.class,
                () -> new LoginAuditDelivery.Result(null, SAVED));
        assertThrows(IllegalArgumentException.class,
                () -> new LoginAuditDelivery.Result(evidence, null));
    }

    private static LoginAudit evidence(Instant time) {
        return new LoginAudit(null, 11L, " Alice \n", LoginOutcome.SUCCESS, null, time);
    }
}
