package com.example.banking.unit;

import com.example.banking.dao.AccountDAO;
import com.example.banking.dao.jdbc.JdbcAccountDAO;
import com.example.banking.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;

import static com.example.banking.unit.AccountFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JdbcAccountDAOTest {
    private Connection connection;
    private PreparedStatement query;
    private ResultSet rows, keys;
    private final JdbcAccountDAO dao = new JdbcAccountDAO(10);

    @BeforeEach void setup() throws Exception {
        connection = mock(Connection.class);
        query = mock(PreparedStatement.class);
        rows = mock(ResultSet.class);
        keys = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(query);
        when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(query);
        when(query.executeQuery()).thenReturn(rows);
        when(query.executeUpdate()).thenReturn(1);
        when(query.getGeneratedKeys()).thenReturn(keys);
        when(keys.next()).thenReturn(true);
        when(keys.getLong(1)).thenReturn(7L);
        when(rows.next()).thenReturn(true, false);
        when(rows.getLong("account_id")).thenReturn(7L);
        when(rows.getLong("customer_id")).thenReturn(1L);
        when(rows.getString("account_number")).thenReturn(NUMBER.value());
        when(rows.getString("account_type")).thenReturn("SAVINGS");
        when(rows.getString("status")).thenReturn("ACTIVE");
        when(rows.getBigDecimal("balance")).thenReturn(new BigDecimal("0.00"));
        when(rows.getBigDecimal("minimum_balance")).thenReturn(new BigDecimal("0.00"));
        when(rows.getBigDecimal("interest_rate")).thenReturn(new BigDecimal("0.00"));
        when(rows.getObject("opening_date", LocalDate.class)).thenReturn(DATE);
    }

    @Test void insertBindsValuesAndNeverControlsTheTransaction() throws Exception {
        assertEquals(7, dao.insert(connection, 1, NUMBER, AccountType.CURRENT, DATE));
        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture(), eq(Statement.RETURN_GENERATED_KEYS));
        assertTrue(sql.getValue().contains("VALUES (?, ?, ?, 0.00, 0.00, 0.00, ?, 'ACTIVE')"));
        assertFalse(sql.getValue().contains(NUMBER.value()));
        verify(query).setLong(1, 1);
        verify(query).setString(2, NUMBER.value());
        verify(query).setString(3, "CURRENT");
        verify(query).setObject(4, DATE, Types.DATE);
        verify(query).setQueryTimeout(10);
        verify(keys).close();
        verify(query).close();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();
    }

    @Test void listAndDetailsHaveOwnerPredicatesAndDetailsAlsoBindTheId() throws Exception {
        assertEquals(1, dao.findOwned(connection, 1).size());
        when(rows.next()).thenReturn(true, false);
        assertTrue(dao.findOwned(connection, 1, 7).isPresent());
        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(connection, times(2)).prepareStatement(sql.capture());
        assertTrue(sql.getAllValues().get(0).contains("WHERE customer_id = ?"));
        assertTrue(sql.getAllValues().get(1).contains("AND account_id = ?"));
        verify(query, times(2)).setLong(1, 1);
        verify(query).setLong(2, 7);
        verify(query, times(2)).setQueryTimeout(10);
        verify(rows, times(2)).close();
    }

    @ParameterizedTest @EnumSource(AccountType.class)
    void mapsEveryPersistedTypeWithoutMakingPrincipalSpendable(AccountType type) throws Exception {
        when(rows.getString("account_type")).thenReturn(type.name());
        Account found = dao.findOwned(connection, 1, 7).orElseThrow();
        assertEquals(type, found.type());
        assertEquals(NUMBER, found.number());
        assertEquals(AccountStatus.ACTIVE, found.status());
        assertEquals(DATE, found.openingDate());
    }

    @Test void missingRowsAreEmptyRatherThanInventedAccounts() throws Exception {
        when(rows.next()).thenReturn(false);
        assertTrue(dao.findOwned(connection, 1).isEmpty());
        assertTrue(dao.findOwned(connection, 1, 7).isEmpty());
    }

    @Test void invalidInputIsRejectedBeforePreparingSql() {
        assertThrows(IllegalArgumentException.class, () -> dao.insert(connection, 1, NUMBER, AccountType.SAVINGS, LocalDate.of(999, 1, 1)));
        assertThrows(RuntimeException.class, () -> dao.insert(connection, 1, NUMBER, AccountType.LOAN, DATE));
        assertThrows(RuntimeException.class, () -> dao.findOwned(connection, 0));
        assertThrows(RuntimeException.class, () -> dao.findOwned(connection, 1, 0));
        verifyNoInteractions(connection);
    }

    @Test void onlyExecuteUpdateDuplicateRejectionBecomesACollision() throws Exception {
        when(query.executeUpdate()).thenThrow(new SQLException("SECRET", "23000", 1062));
        var failure = assertThrows(AccountDAO.NumberCollision.class,
                () -> dao.insert(connection, 1, NUMBER, AccountType.SAVINGS, DATE));
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains("SECRET"));
    }

    @Test void duplicateCodeDuringCleanupDoesNotBecomeARetryableCollision() throws Exception {
        doThrow(new SQLException("SECRET", "23000", 1062)).when(query).close();
        var failure = assertThrows(SQLException.class,
                () -> dao.insert(connection, 1, NUMBER, AccountType.SAVINGS, DATE));
        assertFalse(failure instanceof AccountDAO.NumberCollision);
    }

    @Test void wrongInsertCountAndMissingKeysFailExplicitly() throws Exception {
        when(query.executeUpdate()).thenReturn(0);
        assertThrows(SQLException.class, () -> dao.insert(connection, 1, NUMBER, AccountType.SAVINGS, DATE));
        when(query.executeUpdate()).thenReturn(1);
        when(keys.next()).thenReturn(false);
        assertThrows(SQLException.class, () -> dao.insert(connection, 1, NUMBER, AccountType.SAVINGS, DATE));
    }

    @Test void corruptRowsAreRejectedWithoutExposingTheirContents() throws Exception {
        when(rows.getString("account_number")).thenReturn("SECRET");
        var failure = assertThrows(SQLException.class, () -> dao.findOwned(connection, 1));
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains("SECRET"));
    }

    @Test void unknownTypeAndNegativeBalanceNeverDefaultToSavingsOrZero() throws Exception {
        when(rows.getString("account_type")).thenReturn("UNKNOWN");
        assertThrows(SQLException.class, () -> dao.findOwned(connection, 1, 7));
        when(rows.next()).thenReturn(true);
        when(rows.getString("account_type")).thenReturn("SAVINGS");
        when(rows.getBigDecimal("balance")).thenReturn(new BigDecimal("-0.01"));
        assertThrows(SQLException.class, () -> dao.findOwned(connection, 1, 7));
    }
}

