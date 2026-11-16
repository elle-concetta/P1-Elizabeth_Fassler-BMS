package com.example.banking.unit;

import com.example.banking.dao.TransactionAuditDAO;
import com.example.banking.dao.jdbc.JdbcTransactionAuditDAO;
import com.example.banking.exception.PersistenceException;
import com.example.banking.model.AuditOutcome;
import com.example.banking.model.TransactionAudit;
import com.example.banking.model.TransactionReference;
import com.example.banking.model.TransactionType;
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

/** Mockito-only tests: no real database, schema setup, or money movements. */
class JdbcTransactionAuditDAOTest {

    private static final Instant TIME = Instant.parse("2026-01-02T03:04:05Z");
    private static final TransactionReference REFERENCE = new TransactionReference(
            "TX-20260102-" + "A".repeat(28));
    private static final String UTC_SESSION = "SET SESSION time_zone = '+00:00'";
    private static final String INSERT = """
            INSERT INTO transaction_audit
                (customer_id, reference_number, event_type, outcome, failure_code, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private ConnectionFactory factory;
    private Connection connection;
    private PreparedStatement session;
    private PreparedStatement statement;
    private JdbcTransactionAuditDAO dao;

    @BeforeEach
    void setup() throws SQLException {
        factory = mock(ConnectionFactory.class);
        connection = mock(Connection.class);
        session = mock(PreparedStatement.class);
        statement = mock(PreparedStatement.class);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(UTC_SESSION)).thenReturn(session);
        when(connection.prepareStatement(INSERT)).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        dao = new JdbcTransactionAuditDAO(factory);
    }

    @Test
    void bindsFieldsAndCommitsOnlyAfterClosingStatements() throws Exception {
        TransactionAudit audit = audit(AuditOutcome.REJECTED, REFERENCE,
                "INSUFFICIENT_FUNDS", TIME);
        dao.append(audit);

        var order = inOrder(factory, connection, session, statement);
        order.verify(factory).getConnection();
        order.verify(connection).setAutoCommit(false);
        order.verify(connection).prepareStatement(UTC_SESSION);
        order.verify(session).execute();
        order.verify(session).close();
        order.verify(connection).prepareStatement(INSERT);
        order.verify(statement).setLong(1, 11L);
        order.verify(statement).setString(2, REFERENCE.value());
        order.verify(statement).setString(3, "TRANSFER");
        order.verify(statement).setString(4, "REJECTED");
        order.verify(statement).setString(5, "INSUFFICIENT_FUNDS");
        ArgumentCaptor<Calendar> calendar = ArgumentCaptor.forClass(Calendar.class);
        order.verify(statement).setTimestamp(eq(6), eq(Timestamp.from(TIME)), calendar.capture());
        assertEquals("UTC", calendar.getValue().getTimeZone().getID());
        order.verify(statement).executeUpdate();
        order.verify(statement).close();
        order.verify(connection).commit();
        order.verify(connection).close();
        order.verifyNoMoreInteractions();
        assertTrue(audit.auditId().isEmpty());
    }

    @Test
    void absentReferenceAndFailureCodeUseTypedNulls() throws Exception {
        dao.append(audit(AuditOutcome.ROLLED_BACK, null, null, TIME));
        verify(statement).setNull(2, Types.VARCHAR);
        verify(statement).setNull(5, Types.VARCHAR);
        verify(connection).commit();
    }

    @Test
    void bindsAllFinancialOutcomesByName() throws Exception {
        for (AuditOutcome outcome : AuditOutcome.values()) {
            dao.append(audit(outcome, REFERENCE, null, TIME));
            verify(statement).setString(4, outcome.name());
        }
        verify(factory, times(AuditOutcome.values().length)).getConnection();
        verify(connection, times(AuditOutcome.values().length)).commit();
    }

    @Test
    void bindsAllOperationTypesByName() throws Exception {
        for (TransactionType type : TransactionType.values()) {
            dao.append(new TransactionAudit(null, 11L, REFERENCE, type,
                    AuditOutcome.SUCCESS, null, TIME));
            verify(statement).setString(3, type.name());
        }
        verify(connection, times(TransactionType.values().length)).commit();
    }

    @Test
    void acquiresAndClosesAFreshConnectionForEveryAppend() throws Exception {
        Connection second = mock(Connection.class);
        PreparedStatement otherSession = mock(PreparedStatement.class);
        PreparedStatement otherInsert = mock(PreparedStatement.class);
        when(factory.getConnection()).thenReturn(connection, second);
        when(second.prepareStatement(UTC_SESSION)).thenReturn(otherSession);
        when(second.prepareStatement(INSERT)).thenReturn(otherInsert);
        when(otherInsert.executeUpdate()).thenReturn(1);

        dao.append(audit(AuditOutcome.FAILED, null, null, TIME));
        dao.append(audit(AuditOutcome.SUCCESS, REFERENCE, null, TIME));

        for (Connection owned : new Connection[]{connection, second}) {
            verify(owned).setAutoCommit(false);
            verify(owned).commit();
            verify(owned).close();
            verify(owned, never()).setAutoCommit(true);
        }
        verify(otherSession).execute();
        verify(otherSession).close();
        verify(otherInsert).close();
        verify(factory, times(2)).getConnection();
    }

    @Test
    void rejectsMissingFactory() {
        assertThrows(IllegalArgumentException.class, () -> new JdbcTransactionAuditDAO(null));
    }

    @Test
    void rejectsNullOrAlreadyPersistedEvidenceBeforeJdbc() {
        assertThrows(IllegalArgumentException.class, () -> dao.append(null));
        assertThrows(IllegalArgumentException.class, () -> dao.append(
                new TransactionAudit(1L, 11L, REFERENCE, TransactionType.TRANSFER,
                        AuditOutcome.SUCCESS, null, TIME)));
        verifyNoInteractions(factory, connection, session, statement);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1970-01-01T00:00:01Z",
            "2038-01-19T03:14:07Z"
    })
    void acceptsExactPortableTimestampBoundaries(String value) throws Exception {
        Instant time = Instant.parse(value);
        dao.append(audit(AuditOutcome.REJECTED, null, null, time));
        verify(statement).setTimestamp(eq(6), eq(Timestamp.from(time)), any(Calendar.class));
        verify(connection).commit();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1970-01-01T00:00:00Z",
            "2038-01-19T03:14:08Z",
            "2026-01-02T03:04:05.000000001Z",
            "1970-01-01T00:00:01.999999999Z",
            "2038-01-19T03:14:07.000000001Z",
            "-1000000000-01-01T00:00:00Z",
            "+1000000000-12-31T23:59:59.999999999Z"
    })
    void rejectsUnsupportedTimeBeforeJdbcWithoutChangingEvidence(String value) {
        Instant time = Instant.parse(value);
        TransactionAudit audit = audit(AuditOutcome.FAILED, null, null, time);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> dao.append(audit));
        assertTrue(error.getMessage().contains("no save was attempted"));
        assertSame(time, audit.createdAt());
        verifyNoInteractions(factory, connection, session, statement);
    }

    @Test
    void connectionAcquisitionFailureIsSafeAndNotRetried() throws Exception {
        when(factory.getConnection()).thenThrow(driverFailure());
        assertSafeFailure();
        verify(factory).getConnection();
        verifyNoInteractions(connection, session, statement);
    }

    @Test
    void autoCommitSetupFailureClosesWithoutInserting() throws Exception {
        doThrow(driverFailure()).when(connection).setAutoCommit(false);
        assertSafeFailure();
        verify(connection).close();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verifyNoInteractions(session, statement);
    }

    @Test
    void utcSessionPreparationFailureRollsBackWithoutInserting() throws Exception {
        when(connection.prepareStatement(UTC_SESSION)).thenThrow(driverFailure());
        assertSafeFailure();
        verifyRollbackAndClose();
        verifyNoInteractions(session, statement);
    }

    @Test
    void utcSessionExecutionFailureRollsBackWithoutInserting() throws Exception {
        when(session.execute()).thenThrow(driverFailure());
        assertSafeFailure();
        verify(session).close();
        verifyRollbackAndClose();
        verifyNoInteractions(statement);
    }

    @Test
    void utcSessionCloseFailurePreventsInsertAndRollsBack() throws Exception {
        doThrow(driverFailure()).when(session).close();
        assertSafeFailure();
        verifyRollbackAndClose();
        verifyNoInteractions(statement);
    }

    @Test
    void insertPreparationFailureRollsBack() throws Exception {
        when(connection.prepareStatement(INSERT)).thenThrow(driverFailure());
        assertSafeFailure();
        verifyRollbackAndClose();
        verifyNoInteractions(statement);
    }

    @Test
    void parameterBindingFailureClosesStatementAndRollsBack() throws Exception {
        doThrow(driverFailure()).when(statement).setLong(1, 11L);
        assertSafeFailure();
        verify(statement).close();
        verify(statement, never()).executeUpdate();
        verifyRollbackAndClose();
    }

    @Test
    void rejectedInsertClosesStatementAndRollsBack() throws Exception {
        when(statement.executeUpdate()).thenThrow(driverFailure());
        assertSafeFailure();
        verify(statement).close();
        verifyRollbackAndClose();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, -1})
    void unexpectedInsertCountsCannotCommit(int count) throws Exception {
        when(statement.executeUpdate()).thenReturn(count);
        assertSafeFailure();
        verify(statement).close();
        verifyRollbackAndClose();
    }

    @Test
    void insertStatementCloseFailurePreventsCommit() throws Exception {
        doThrow(driverFailure()).when(statement).close();
        assertSafeFailure();
        verifyRollbackAndClose();
    }

    @Test
    void commitFailureAttemptsRollbackWithoutRepeatingTheInsert() throws Exception {
        doThrow(driverFailure()).when(connection).commit();
        assertSafeFailure();
        var order = inOrder(connection);
        order.verify(connection).commit();
        order.verify(connection).rollback();
        order.verify(connection).close();
        verify(factory).getConnection();
        verify(statement).executeUpdate();
    }

    @Test
    void rollbackAndCloseFailuresDoNotLeakDriverDetails() throws Exception {
        when(statement.executeUpdate()).thenThrow(driverFailure());
        doThrow(driverFailure()).when(connection).rollback();
        doThrow(driverFailure()).when(statement).close();
        doThrow(driverFailure()).when(connection).close();
        assertSafeFailure();
        verifyRollbackAndClose();
    }

    @Test
    void connectionCloseFailureAfterCommitIsUncertainAndNotRetried() throws Exception {
        doThrow(driverFailure()).when(connection).close();
        assertSafeFailure();
        verify(connection).commit();
        verify(connection, never()).rollback();
        verify(factory).getConnection();
        verify(statement).executeUpdate();
    }

    @Test
    void interfaceExposesOnlyAppendWithoutACallerConnection() throws Exception {
        assertEquals(Set.of("append"), Arrays.stream(TransactionAuditDAO.class.getDeclaredMethods())
                .map(method -> method.getName()).collect(Collectors.toSet()));
        assertArrayEquals(new Class<?>[]{TransactionAudit.class},
                TransactionAuditDAO.class.getDeclaredMethod("append", TransactionAudit.class)
                        .getParameterTypes());
    }

    private void verifyRollbackAndClose() throws SQLException {
        var order = inOrder(connection);
        order.verify(connection).rollback();
        order.verify(connection).close();
        verify(connection, never()).commit();
    }

    private void assertSafeFailure() {
        PersistenceException error = assertThrows(PersistenceException.class,
                () -> dao.append(audit(AuditOutcome.REJECTED, null, null, TIME)));
        assertEquals("The operation could not be completed.", error.getMessage());
        assertInstanceOf(SQLException.class, error.getCause());
        assertEquals("Transaction audit save was not confirmed. Do not retry automatically.",
                error.getCause().getMessage());
        assertNull(error.getCause().getCause());
        assertEquals(0, error.getSuppressed().length);
        assertEquals(0, error.getCause().getSuppressed().length);
        assertNull(((SQLException) error.getCause()).getNextException());
        assertFalse(error.getCause().toString().contains("SYNTHETIC_PRIVATE_DATA"));
    }

    private static SQLException driverFailure() {
        SQLException error = new SQLException("SYNTHETIC_PRIVATE_DATA; SELECT private_data", "08000");
        error.setNextException(new SQLException("SYNTHETIC_PRIVATE_DATA"));
        return error;
    }

    private static TransactionAudit audit(AuditOutcome outcome, TransactionReference reference,
                                          String code, Instant time) {
        return new TransactionAudit(null, 11L, reference, TransactionType.TRANSFER,
                outcome, code, time);
    }
}
