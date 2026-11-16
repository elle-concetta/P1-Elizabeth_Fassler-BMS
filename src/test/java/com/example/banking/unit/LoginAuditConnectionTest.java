package com.example.banking.unit;

import com.example.banking.dao.jdbc.JdbcLoginAuditDAO;
import com.example.banking.exception.PersistenceException;
import com.example.banking.model.LoginAudit;
import com.example.banking.model.LoginOutcome;
import com.example.banking.util.ConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real factory and DAO with mock JDBC: never contacts a database. */
class LoginAuditConnectionTest {
    private ConnectionFactory.Connector connector;
    private Connection connection;
    private Statement setup;
    private ConnectionFactory factory;

    @BeforeEach
    void setup() throws SQLException {
        connector = mock(ConnectionFactory.Connector.class);
        connection = mock(Connection.class);
        setup = mock(Statement.class);
        when(connector.connect(anyString(), any(Properties.class))).thenReturn(connection);
        when(connection.createStatement()).thenReturn(setup);
        // Even caller-supplied driver options cannot bypass the explicit session SQL.
        factory = new ConnectionFactory(
                "jdbc:mysql://localhost:3307/banking_test?connectionTimeZone=SERVER",
                "synthetic", "synthetic", connector);
    }

    @Test
    void establishesUtcAndClosesSetupBeforeReturningConnection() throws Exception {
        assertSame(connection, factory.getLoginAuditConnection());
        var order = inOrder(connection, setup);
        order.verify(connection).createStatement();
        order.verify(setup).execute("SET SESSION time_zone = '+00:00'");
        order.verify(setup).close();
        order.verifyNoMoreInteractions();
    }

    @Test
    void ordinaryConnectionsStillRunNoSetup() throws Exception {
        assertSame(connection, factory.getConnection());
        verifyNoInteractions(connection, setup);
    }

    @Test
    void setsUtcForEveryFreshAcquisition() throws Exception {
        Connection second = mock(Connection.class);
        Statement secondSetup = mock(Statement.class);
        when(connector.connect(anyString(), any(Properties.class))).thenReturn(connection, second);
        when(second.createStatement()).thenReturn(secondSetup);
        assertSame(connection, factory.getLoginAuditConnection());
        assertSame(second, factory.getLoginAuditConnection());
        verify(setup).execute("SET SESSION time_zone = '+00:00'");
        verify(secondSetup).execute("SET SESSION time_zone = '+00:00'");
        verify(connection, never()).close();
        verify(second, never()).close();
    }

    @Test
    void connectionFailureDoesNotAttemptSetupOrRetry() throws Exception {
        SQLException failure = new SQLException("synthetic connect failure");
        when(connector.connect(anyString(), any(Properties.class))).thenThrow(failure);
        assertSame(failure, assertThrows(SQLException.class, factory::getLoginAuditConnection));
        verify(connector).connect(anyString(), any(Properties.class));
        verifyNoInteractions(connection, setup);
    }

    @Test
    void nullConnectionFailsBeforeSetup() throws Exception {
        when(connector.connect(anyString(), any(Properties.class))).thenReturn(null);
        assertEquals("08001", assertThrows(SQLException.class,
                factory::getLoginAuditConnection).getSQLState());
        verifyNoInteractions(connection, setup);
    }

    @Test
    void statementCreationFailureClosesConnection() throws Exception {
        SQLException failure = new SQLException("synthetic create failure");
        when(connection.createStatement()).thenThrow(failure);
        assertSame(failure, assertThrows(SQLException.class, factory::getLoginAuditConnection));
        verify(connection).close();
        verifyNoInteractions(setup);
    }

    @Test
    void rejectedSessionSetupClosesStatementAndConnectionWithoutTransaction() throws Exception {
        SQLException failure = new SQLException("synthetic SET failure");
        when(setup.execute(anyString())).thenThrow(failure);
        assertSame(failure, assertThrows(SQLException.class, factory::getLoginAuditConnection));
        var order = inOrder(setup, connection);
        order.verify(connection).createStatement();
        order.verify(setup).execute("SET SESSION time_zone = '+00:00'");
        order.verify(setup).close();
        order.verify(connection).close();
        order.verifyNoMoreInteractions();
    }

    @Test
    void setupStatementCloseFailureDoesNotReturnConnection() throws Exception {
        SQLException failure = new SQLException("synthetic close failure");
        doThrow(failure).when(setup).close();
        assertSame(failure, assertThrows(SQLException.class, factory::getLoginAuditConnection));
        verify(connection).close();
        verify(connection, never()).setAutoCommit(anyBoolean());
    }

    @Test
    void cleanupFailurePreservesOriginalSetupFailure() throws Exception {
        SQLException failure = new SQLException("synthetic setup failure");
        SQLException cleanup = new SQLException("synthetic cleanup failure");
        when(setup.execute(anyString())).thenThrow(failure);
        doThrow(cleanup).when(connection).close();
        assertSame(failure, assertThrows(SQLException.class, factory::getLoginAuditConnection));
        assertArrayEquals(new Throwable[]{cleanup}, failure.getSuppressed());
    }

    @Test
    void runtimeSetupFailureAlsoClosesConnection() throws Exception {
        when(setup.execute(anyString())).thenThrow(new IllegalStateException("synthetic"));
        assertThrows(IllegalStateException.class, factory::getLoginAuditConnection);
        verify(setup).close();
        verify(connection).close();
    }

    @Test
    void daoSetsUtcBeforeTransactionAndInsert() throws Exception {
        PreparedStatement insert = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(insert);
        when(insert.executeUpdate()).thenReturn(1);
        new JdbcLoginAuditDAO(factory).append(audit());
        var order = inOrder(connection, setup, insert);
        order.verify(connection).createStatement();
        order.verify(setup).execute("SET SESSION time_zone = '+00:00'");
        order.verify(setup).close();
        order.verify(connection).setAutoCommit(false);
        order.verify(connection).prepareStatement(anyString());
        verify(connection).commit();
        verify(connection).close();
    }

    @Test
    void daoSanitizesSetupFailureAndNeverInsertsOrRetries() throws Exception {
        when(setup.execute(anyString())).thenThrow(new SQLException("private-driver-detail"));
        PersistenceException failure = assertThrows(PersistenceException.class,
                () -> new JdbcLoginAuditDAO(factory).append(audit()));
        assertFalse(failure.toString().contains("private-driver-detail"));
        assertFalse(failure.getCause().toString().contains("private-driver-detail"));
        verify(connector).connect(anyString(), any(Properties.class));
        verify(connection).close();
        verify(connection, never()).prepareStatement(anyString());
        verify(connection, never()).setAutoCommit(anyBoolean());
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
    }

    private static LoginAudit audit() {
        return new LoginAudit(null, null, "attempt", LoginOutcome.FAILURE, null,
                Instant.parse("2026-01-02T03:04:05Z"));
    }
}

