package com.example.banking.unit;

import com.example.banking.dao.jdbc.JdbcCustomerDAO;
import com.example.banking.model.PasswordHash;
import com.example.banking.model.Registration;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JdbcCustomerDAOTest {
    private static final String IDENTITY_SQL =
            "SELECT 1 FROM customers WHERE username = ? OR email = ? LIMIT 1";
    private static final String UPDATE_LOGIN_SQL = """
            UPDATE customers SET failed_login_count = ?, locked_until = FROM_UNIXTIME(?)
            WHERE customer_id = ?
            """;
    private static final String INSERT_SQL = """
            INSERT INTO customers (customer_code, first_name, middle_initial,
                last_name, date_of_birth, address, email, phone, username, password_hash)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    @Test
    void identityLookupBindsValuesAndClosesResourcesWithoutOwningTheTransaction() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement query = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(IDENTITY_SQL)).thenReturn(query);
        when(query.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        String attempted = "' OR 1=1 --";
        assertTrue(new JdbcCustomerDAO(10).identityExists(connection, attempted, "test@example.invalid"));
        // The SQL must match the fixed template; user input is bound separately.
        verify(connection).prepareStatement(IDENTITY_SQL);
        verify(query).setString(1, attempted);
        verify(query).setString(2, "test@example.invalid");
        verify(query).setQueryTimeout(10);
        verify(rows).close();
        verify(query).close();
        verify(connection, never()).commit();
        verify(connection, never()).close();
    }

    @Test
    void writesLockoutAsEpochSecondsAndRejectsUnexpectedUpdateCounts() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement query = mock(PreparedStatement.class);
        when(connection.prepareStatement(UPDATE_LOGIN_SQL)).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1, 0);
        Instant until = Instant.parse("2026-11-11T12:15:00Z");
        var dao = new JdbcCustomerDAO(10);
        dao.updateLoginState(connection, 7, 5, until);
        verify(query).setLong(2, until.getEpochSecond());
        verify(query).setLong(3, 7);
        assertThrows(SQLException.class, () -> dao.updateLoginState(connection, 7, 0, null));
        verify(query).setNull(2, Types.BIGINT);
    }

    @Test
    void insertRequestsGeneratedKeysAndBindsCustomerValues() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement query = mock(PreparedStatement.class);
        ResultSet keys = mock(ResultSet.class);
        when(connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS))
                .thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);
        when(query.getGeneratedKeys()).thenReturn(keys);
        when(keys.next()).thenReturn(true);
        when(keys.getLong(1)).thenReturn(7L);
        var input = new Registration("Test", null, "Person", null, null,
                "test@example.invalid", "2025550123", "tester");
        // Synthetic encoded value: this DAO test does not hash or verify passwords.
        var hash = new PasswordHash("$2a$04$" + "A".repeat(53));

        var customer = new JdbcCustomerDAO(10).insert(connection, input, "C-fixture", hash);

        assertEquals(7L, customer.customerId());
        verify(connection).prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS);
        verify(query).setString(9, "tester");
        verify(query).setString(10, hash.value());
        verify(keys).close();
        verify(query).close();
        verify(connection, never()).commit();
        verify(connection, never()).close();
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new JdbcCustomerDAO(0));
    }
}
