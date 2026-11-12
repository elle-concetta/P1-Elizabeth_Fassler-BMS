package com.example.banking.unit;

import com.example.banking.dao.jdbc.JdbcCustomerDAO;
import com.example.banking.model.PasswordHash;
import com.example.banking.model.Registration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JdbcCustomerDAOTest {
    private Connection connection;
    private PreparedStatement query;
    private ResultSet rows;
    private final JdbcCustomerDAO dao = new JdbcCustomerDAO(10);

    @BeforeEach
    void setup() throws Exception {
        connection = mock(Connection.class);
        query = mock(PreparedStatement.class);
        rows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(query);
        when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(query);
        when(query.executeQuery()).thenReturn(rows);
        when(query.getGeneratedKeys()).thenReturn(rows);
    }

    @Test
    void duplicateLookupBindsSqlLikeInputRatherThanInterpolatingIt() throws Exception {
        when(rows.next()).thenReturn(true);
        assertTrue(dao.identityExists(connection, "' OR 1=1 --", "x@example.invalid"));
        verify(connection).prepareStatement(
                "SELECT 1 FROM customers WHERE username = ? OR email = ? LIMIT 1");
        verify(query).setString(1, "' OR 1=1 --");
        verify(query).setString(2, "x@example.invalid");
        verify(query).setQueryTimeout(10);
        verify(connection, never()).commit();
        verify(query).close();
        verify(rows).close();
    }

    @Test
    void insertionBindsHashAndNullableProfileWithoutCommitting() throws Exception {
        when(query.executeUpdate()).thenReturn(1);
        when(rows.next()).thenReturn(true);
        when(rows.getLong(1)).thenReturn(7L);
        String hash = "$2a$04$" + "A".repeat(53);
        var profile = dao.insert(connection, new Registration("Test", "", "Person", null,
                "", "test@example.invalid", "", "tester"), "C-fixture", new PasswordHash(hash));
        assertEquals(7, profile.customerId());
        assertFalse(profile.toString().contains(hash));
        verify(query).setString(3, null);
        verify(query).setObject(5, null, Types.DATE);
        verify(query).setString(6, null);
        verify(query).setString(8, null);
        verify(query).setString(10, hash);
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(query).close();
        verify(rows).close();
    }

    @Test
    void loginLookupLocksRowAndMapsEpochWithoutDriverTimeZoneAssumptions() throws Exception {
        when(rows.next()).thenReturn(true);
        when(rows.getLong("customer_id")).thenReturn(7L);
        when(rows.getString("customer_code")).thenReturn("C-fixture");
        when(rows.getString("username")).thenReturn("tester");
        when(rows.getString("status")).thenReturn("ACTIVE");
        when(rows.getString("password_hash")).thenReturn("$2a$04$" + "A".repeat(53));
        when(rows.getLong("locked_epoch")).thenReturn(1234L);
        when(rows.wasNull()).thenReturn(false);
        var row = dao.lockByUsername(connection, "tester").orElseThrow();
        assertEquals(Instant.ofEpochSecond(1234), row.lockedUntil());
        assertEquals("AuthenticationRow[REDACTED]", row.toString());
        verify(connection).prepareStatement(argThat(sql -> sql.contains("FOR UPDATE")
                && sql.contains("UNIX_TIMESTAMP(locked_until)") && sql.contains("username = ?")));
        verify(query).setString(1, "tester");
        verify(connection, never()).commit();
    }

    @Test
    void counterUpdateUsesEpochAndRequiresExactlyOneAffectedCustomer() throws Exception {
        when(query.executeUpdate()).thenReturn(1, 0);
        dao.updateLoginState(connection, 7, 5, Instant.ofEpochSecond(1234));
        verify(query).setLong(2, 1234);
        verify(query).setInt(1, 5);
        verify(query).setLong(3, 7);
        assertThrows(SQLException.class, () -> dao.updateLoginState(connection, 7, 0, null));
        verify(query).setNull(2, Types.BIGINT);
        verify(connection, never()).commit();
    }
}

