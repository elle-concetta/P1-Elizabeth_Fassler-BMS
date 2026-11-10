package com.example.banking.unit;

import com.example.banking.util.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectionFactoryTest {

    private static final String URL = "jdbc:mysql://localhost:3306/banking_test";
    private static final String USERNAME = "test_only_user";
    // Synthetic test input, not a credential for a real database.
    private static final String PASSWORD = "  test-only-placeholder  ";

    @Mock
    private ConnectionFactory.Connector connector;

    @Mock
    private Connection connection;

    @Test
    void returnsConnectionWithExpectedCredentials() throws SQLException {
        when(connector.connect(eq(URL), any(Properties.class))).thenReturn(connection);
        ConnectionFactory factory = factory();

        Connection actual = factory.getConnection();

        ArgumentCaptor<Properties> credentials =
                ArgumentCaptor.forClass(Properties.class);
        verify(connector).connect(eq(URL), credentials.capture());
        assertAll(
                () -> assertSame(connection, actual),
                () -> assertEquals(USERNAME, credentials.getValue().getProperty("user")),
                () -> assertEquals(PASSWORD, credentials.getValue().getProperty("password"))
        );
        // Opening a connection must not commit, roll back, close, or execute SQL.
        verifyNoInteractions(connection);
    }

    @Test
    void preservesConnectionFailureForCaller() throws SQLException {
        SQLException failure = new SQLException("Synthetic connection failure.", "08001");
        when(connector.connect(eq(URL), any(Properties.class))).thenThrow(failure);

        SQLException actual = assertThrows(SQLException.class,
                () -> factory().getConnection());

        assertSame(failure, actual);
        verify(connector).connect(eq(URL), any(Properties.class));
        verifyNoInteractions(connection);
    }

    @Test
    void rejectsMissingDatabaseSettingsBeforeConnecting() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ConnectionFactory(null, USERNAME, PASSWORD, connector)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ConnectionFactory(URL, " ", PASSWORD, connector)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ConnectionFactory(URL, USERNAME, "", connector))
        );
        verifyNoInteractions(connector);
    }

    @Test
    void rejectsNonMySqlUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectionFactory(
                        "jdbc:invalid://localhost/example", USERNAME, PASSWORD, connector));
        verifyNoInteractions(connector);
    }

    @Test
    void rejectsNullConnector() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConnectionFactory(URL, USERNAME, PASSWORD, null));
    }

    @Test
    void rejectsNullConnectionInsteadOfHidingFailure() throws SQLException {
        when(connector.connect(eq(URL), any(Properties.class))).thenReturn(null);

        SQLException failure = assertThrows(SQLException.class,
                () -> factory().getConnection());

        assertEquals("08001", failure.getSQLState());
    }

    @Test
    void buildsFromSuppliedEnvironmentWithoutOpeningImmediately() throws SQLException {
        ConnectionFactory factory = ConnectionFactory.fromEnvironment(
                Map.of("DB_URL", URL, "DB_USERNAME", USERNAME, "DB_PASSWORD", PASSWORD),
                connector
        );
        verifyNoInteractions(connector);
        when(connector.connect(eq(URL), any(Properties.class))).thenReturn(connection);

        assertSame(connection, factory.getConnection());
    }

    @Test
    void rejectsMissingEnvironmentSettingBeforeConnecting() {
        Map<String, String> environment =
                Map.of("DB_URL", URL, "DB_USERNAME", USERNAME);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ConnectionFactory.fromEnvironment(environment, connector));

        assertEquals("Missing required environment variable: DB_PASSWORD",
                failure.getMessage());
        verifyNoInteractions(connector);
    }

    @Test
    void usesFreshCredentialPropertiesForEachConnection() throws SQLException {
        when(connector.connect(eq(URL), any(Properties.class))).thenReturn(connection);
        ConnectionFactory factory = factory();

        factory.getConnection();
        factory.getConnection();

        ArgumentCaptor<Properties> credentials =
                ArgumentCaptor.forClass(Properties.class);
        verify(connector, times(2)).connect(eq(URL), credentials.capture());
        assertNotSame(credentials.getAllValues().get(0),
                credentials.getAllValues().get(1));
    }

    private ConnectionFactory factory() {
        return new ConnectionFactory(URL, USERNAME, PASSWORD, connector);
    }
}
