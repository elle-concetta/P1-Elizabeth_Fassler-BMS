package com.example.banking.unit;

import com.example.banking.dao.LoginAuditDAO;
import com.example.banking.dao.jdbc.JdbcLoginAuditDAO;
import com.example.banking.exception.PersistenceException;
import com.example.banking.model.LoginAudit;
import com.example.banking.model.LoginOutcome;
import com.example.banking.util.ConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Mock JDBC only: no driver connection, SQL execution, or durable persistence. */
class JdbcLoginAuditDAOTest {
    private static final Instant TIME = Instant.parse("2026-01-02T03:04:05Z");
    private static final String SQL = """
            INSERT INTO login_audit
                (customer_id, username_attempted, outcome, failure_reason, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;
    private ConnectionFactory factory;
    private Connection connection;
    private PreparedStatement statement;
    private JdbcLoginAuditDAO dao;

    @BeforeEach
    void setup() throws SQLException {
        factory = mock(ConnectionFactory.class);
        connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(SQL)).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        dao = new JdbcLoginAuditDAO(factory);
    }

    @Test
    void bindsEveryFieldExactlyAndCommitsAfterClosingStatement() throws Exception {
        LoginAudit audit = audit(11L, LoginOutcome.FAILURE, "INVALID_CREDENTIALS");
        dao.append(audit);
        var order = inOrder(factory, connection, statement);
        order.verify(factory).getConnection();
        order.verify(connection).setAutoCommit(false);
        order.verify(connection).prepareStatement(SQL);
        order.verify(statement).setLong(1, 11L);
        order.verify(statement).setString(2, " Alice ");
        order.verify(statement).setString(3, "FAILURE");
        order.verify(statement).setString(4, "INVALID_CREDENTIALS");
        ArgumentCaptor<Calendar> calendar = ArgumentCaptor.forClass(Calendar.class);
        order.verify(statement).setTimestamp(eq(5), eq(Timestamp.from(TIME)), calendar.capture());
        assertEquals("UTC", calendar.getValue().getTimeZone().getID());
        order.verify(statement).executeUpdate();
        order.verify(statement).close();
        order.verify(connection).commit();
        order.verify(connection).close();
        order.verifyNoMoreInteractions();
        assertTrue(audit.auditId().isEmpty());
    }

    @Test
    void unknownCustomerAndAbsentReasonUseTypedNulls() throws Exception {
        dao.append(audit(null, LoginOutcome.FAILURE, null));
        verify(statement).setNull(1, Types.BIGINT);
        verify(statement).setNull(4, Types.VARCHAR);
        verify(statement, never()).setLong(anyInt(), anyLong());
        verify(connection).commit();
    }

    @Test
    void bindsEveryOutcomeByName() throws Exception {
        for (LoginOutcome outcome : LoginOutcome.values()) {
            dao.append(audit(null, outcome, null));
            verify(statement).setString(3, outcome.name());
        }
        verify(factory, times(5)).getConnection();
        verify(connection, times(5)).commit();
    }

    @Test
    void opensAnIndependentConnectionForEachAppend() throws Exception {
        Connection second = mock(Connection.class);
        PreparedStatement otherStatement = mock(PreparedStatement.class);
        when(factory.getConnection()).thenReturn(connection, second);
        when(second.prepareStatement(SQL)).thenReturn(otherStatement);
        when(otherStatement.executeUpdate()).thenReturn(1);
        dao.append(audit(null, LoginOutcome.FAILURE, null));
        dao.append(audit(11L, LoginOutcome.SUCCESS, null));
        for (Connection owned : new Connection[]{connection, second}) {
            verify(owned).setAutoCommit(false);
            verify(owned).commit();
            verify(owned).close();
            verify(owned, never()).setAutoCommit(true);
        }
    }

    @Test
    void rejectsNullRecordBeforeOpeningConnection() {
        assertThrows(IllegalArgumentException.class, () -> dao.append(null));
        verifyNoInteractions(factory);
    }

    @Test
    void rejectsAlreadyPersistedRecordBeforeOpeningConnection() {
        assertThrows(IllegalArgumentException.class, () -> dao.append(
                new LoginAudit(1L, null, "alice", LoginOutcome.FAILURE, null, TIME)));
        verifyNoInteractions(factory);
    }

    @Test
    void rejectsNullFactory() {
        assertThrows(IllegalArgumentException.class, () -> new JdbcLoginAuditDAO(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-01-02T03:04:05.000000001Z",
            "2026-01-02T03:04:05.123456Z",
            "2026-01-02T03:04:05.999999999Z",
            "1970-01-01T00:00:01.000000001Z",
            "2038-01-19T03:14:06.999999999Z",
            "1970-01-01T00:00:00.999999999Z",
            "2038-01-19T03:14:07.000000001Z",
            "1970-01-01T00:00:00Z",
            "2038-01-19T03:14:08Z",
            "-1000000000-01-01T00:00:00Z",
            "+1000000000-12-31T23:59:59.999999999Z"
    })
    void rejectsUnrepresentableTimeWithoutTouchingJdbcOrChangingModel(String text) {
        Instant time = Instant.parse(text);
        LoginAudit audit = new LoginAudit(null, null, "private-attempt",
                LoginOutcome.FAILURE, null, time);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> dao.append(audit));
        assertEquals("Login audit timestamp requires whole seconds from "
                + "1970-01-01T00:00:01Z through 2038-01-19T03:14:07Z inclusive; "
                + "no save was attempted.", error.getMessage());
        assertSame(time, audit.createdAt());
        assertTrue(audit.auditId().isEmpty());
        verifyNoInteractions(factory, connection, statement);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1970-01-01T00:00:01Z", "1970-01-01T00:00:02Z",
            "2038-01-19T03:14:06Z", "2038-01-19T03:14:07Z"
    })
    void bindsExactBoundsAndAdjacentSupportedSecondsWithoutChangingTime(String text)
            throws Exception {
        Instant time = Instant.parse(text);
        LoginAudit audit = new LoginAudit(null, null, "boundary-attempt",
                LoginOutcome.FAILURE, null, time);
        dao.append(audit);
        ArgumentCaptor<Calendar> calendar = ArgumentCaptor.forClass(Calendar.class);
        verify(statement).setTimestamp(eq(5), eq(Timestamp.from(time)), calendar.capture());
        assertEquals("UTC", calendar.getValue().getTimeZone().getID());
        verify(connection).commit();
        assertSame(time, audit.createdAt());
    }

    @Test
    void acquisitionFailureDoesNotTouchResources() throws Exception {
        when(factory.getConnection()).thenThrow(secretFailure());
        assertSafeFailure();
        verifyNoInteractions(connection, statement);
    }

    @Test
    void beginFailureClosesConnectionWithoutInsertingOrCommitting() throws Exception {
        doThrow(secretFailure()).when(connection).setAutoCommit(false);
        assertSafeFailure();
        verify(connection).close();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verifyNoInteractions(statement);
    }

    @Test
    void prepareFailureRollsBackAndClosesConnection() throws Exception {
        when(connection.prepareStatement(SQL)).thenThrow(secretFailure());
        assertSafeFailure();
        verify(connection).rollback();
        verify(connection).close();
        verify(connection, never()).commit();
        verifyNoInteractions(statement);
    }

    @Test
    void bindingFailureClosesStatementThenRollsBackAndClosesConnection() throws Exception {
        doThrow(secretFailure()).when(statement).setString(2, " Alice ");
        assertSafeFailure();
        verify(statement, never()).executeUpdate();
        verifyFailedTransactionCleanup();
    }

    @Test
    void executionFailureRollsBackAndClosesBothResources() throws Exception {
        when(statement.executeUpdate()).thenThrow(secretFailure());
        assertSafeFailure();
        verifyFailedTransactionCleanup();
    }

    @Test
    void unexpectedAffectedRowCountsNeverCommit() throws Exception {
        when(statement.executeUpdate()).thenReturn(0, 2, -1);
        for (int i = 0; i < 3; i++) {
            assertSafeFailure();
        }
        verify(connection, times(3)).rollback();
        verify(connection, times(3)).close();
        verify(statement, times(3)).close();
        verify(connection, never()).commit();
    }

    @Test
    void statementCloseFailureRollsBackBeforeCommit() throws Exception {
        doThrow(secretFailure()).when(statement).close();
        assertSafeFailure();
        verifyFailedTransactionCleanup();
    }

    @Test
    void commitFailureAttemptsRollbackWithoutRetryingInsert() throws Exception {
        doThrow(secretFailure()).when(connection).commit();
        assertSafeFailure();
        var order = inOrder(connection);
        order.verify(connection).commit();
        order.verify(connection).rollback();
        order.verify(connection).close();
        verify(statement).executeUpdate();
        verify(factory).getConnection();
    }

    @Test
    void rollbackAndCleanupFailuresDoNotLeakOrReplaceSafeError() throws Exception {
        when(statement.executeUpdate()).thenThrow(secretFailure());
        doThrow(secretFailure()).when(connection).rollback();
        doThrow(secretFailure()).when(statement).close();
        doThrow(secretFailure()).when(connection).close();
        assertSafeFailure();
        verifyFailedTransactionCleanup();
    }

    @Test
    void connectionCloseFailureAfterCommitDoesNotRollbackOrRetry() throws Exception {
        doThrow(secretFailure()).when(connection).close();
        assertSafeFailure();
        verify(connection).commit();
        verify(connection, never()).rollback();
        verify(statement).executeUpdate();
        verify(factory).getConnection();
    }

    @Test
    void contractHasOnlyAppendAndNoCallerConnectionOrMutationApi() {
        assertEquals(Set.of("append"), Arrays.stream(LoginAuditDAO.class.getDeclaredMethods())
                .map(method -> method.getName()).collect(Collectors.toSet()));
        assertArrayEquals(new Class<?>[]{LoginAudit.class},
                LoginAuditDAO.class.getDeclaredMethods()[0].getParameterTypes());
    }

    private void verifyFailedTransactionCleanup() throws SQLException {
        var order = inOrder(statement, connection);
        order.verify(statement).close();
        order.verify(connection).rollback();
        order.verify(connection).close();
        verify(connection, never()).commit();
    }

    private void assertSafeFailure() {
        PersistenceException error = assertThrows(PersistenceException.class,
                () -> dao.append(audit(null, LoginOutcome.FAILURE, null)));
        assertEquals("The operation could not be completed.", error.getMessage());
        assertEquals("Login audit save was not confirmed. Do not retry automatically.",
                error.getCause().getMessage());
        assertNull(error.getCause().getCause());
        assertEquals(0, error.getSuppressed().length);
        assertEquals(0, error.getCause().getSuppressed().length);
        assertNull(((SQLException) error.getCause()).getNextException());
    }

    private static SQLException secretFailure() {
        return new SQLException("password=TOP_SECRET; SELECT private_data", "08000");
    }

    private static LoginAudit audit(Long customer, LoginOutcome outcome, String reason) {
        return new LoginAudit(null, customer, " Alice ", outcome, reason, TIME);
    }
}