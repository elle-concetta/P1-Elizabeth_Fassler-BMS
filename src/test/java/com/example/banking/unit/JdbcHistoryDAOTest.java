package com.example.banking.unit;

import com.example.banking.dao.jdbc.JdbcTransactionHistoryDAO;
import com.example.banking.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.sql.*;
import java.time.LocalDate;
import java.util.Calendar;
import static com.example.banking.unit.DepositFixtures.*;
import static com.example.banking.unit.HistoryFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JdbcHistoryDAOTest {
    private Connection connection;
    private PreparedStatement statement;
    private ResultSet rows;
    private final JdbcTransactionHistoryDAO dao = new JdbcTransactionHistoryDAO(10);

    @BeforeEach void setup() throws Exception {
        connection = mock(Connection.class); statement = mock(PreparedStatement.class); rows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
    }

    @Test void ownerPredicateCoversBothDirectionsWithoutUnionOrDuplicateProjection() throws Exception {
        assertTrue(dao.findOwned(connection, 1, 7, query(2, 20)).isEmpty());
        var sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        String text = sql.getValue();
        assertTrue(text.contains("(t.source_account_id = a.account_id OR t.destination_account_id = a.account_id)"));
        assertTrue(text.contains("WHERE a.customer_id = ? AND a.account_id = ?"));
        assertTrue(text.contains("ORDER BY t.created_at DESC, t.transaction_id DESC LIMIT ? OFFSET ?"));
        assertFalse(text.contains("UNION"));
        assertFalse(text.contains("balance"));
        assertFalse(text.contains("account_number"));
        verify(statement).setLong(1, 1);
        verify(statement).setLong(2, 7);
        verify(statement).setInt(3, 21);
        verify(statement).setInt(4, 40);
        verify(statement).setQueryTimeout(10);
        verify(rows).close();
        verify(statement).close();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();
    }

    @Test void halfOpenUtcDatesAndTypeAreBoundParametersNotSqlText() throws Exception {
        var query = new HistoryQuery(0, 2, LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 16), TransactionType.TRANSFER);
        dao.findOwned(connection, 1, 7, query);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("t.created_at >= CAST(? AS DATETIME)"));
        assertTrue(sql.getValue().contains("t.created_at < CAST(? AS DATETIME)"));
        assertTrue(sql.getValue().contains("t.transaction_type = ?"));
        assertFalse(sql.getValue().contains("2026-11-15"));
        verify(statement).setString(3, "2026-11-15 00:00:00");
        verify(statement).setString(4, "2026-11-16 00:00:00");
        verify(statement).setString(5, "TRANSFER");
        verify(statement).setInt(6, 3);
        verify(statement).setInt(7, 0);
    }

    @Test void endOnlyAndStartOnlyHaveNoPhantomParameters() throws Exception {
        dao.findOwned(connection, 1, 7, new HistoryQuery(0, 1, null, LocalDate.of(2026, 11, 16), null));
        verify(statement).setString(3, "2026-11-16 00:00:00");
        verify(statement).setInt(4, 2);
        clearInvocations(statement);
        dao.findOwned(connection, 1, 7, new HistoryQuery(0, 1, LocalDate.of(2026, 11, 15), null, null));
        verify(statement).setString(3, "2026-11-15 00:00:00");
        verify(statement).setInt(4, 2);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void determinesDirectionFromTheSelectedRoleAndReadsUtc(boolean source) throws Exception {
        validRow(source);
        var result = dao.findOwned(connection, 1, 7, query(0, 20)).getFirst();
        assertEquals(source ? HistoryEntry.Direction.DEBIT : HistoryEntry.Direction.CREDIT, result.direction());
        assertEquals(TIME, result.createdAt());
        var calendar = ArgumentCaptor.forClass(Calendar.class);
        verify(rows).getTimestamp(eq("created_at"), calendar.capture());
        assertEquals("UTC", calendar.getValue().getTimeZone().getID());
    }

    @Test void malformedRowsAndAmbiguousRolesAreRejectedWithoutDriverGraphs() throws Exception {
        validRow(true);
        when(rows.getString("transaction_type")).thenReturn("SECRET");
        var error = assertThrows(SQLException.class, () -> dao.findOwned(connection, 1, 7, query(0, 20)));
        assertFalse(error.getMessage().contains("SECRET"));
        assertNull(error.getCause());
        validRow(true);
        when(rows.getBoolean("selected_is_destination")).thenReturn(true);
        assertThrows(SQLException.class, () -> dao.findOwned(connection, 1, 7, query(0, 20)));
    }

    @Test void anOverproducingDriverCannotGrowTheResultWithoutBound() throws Exception {
        validRow(true);
        when(rows.next()).thenReturn(true);
        assertThrows(SQLException.class, () -> dao.findOwned(connection, 1, 7, query(0, 1)));
        verify(rows, times(3)).next();
    }

    @Test void invalidCallerInputsNeverPrepareSql() {
        assertThrows(IllegalArgumentException.class, () -> dao.findOwned(connection, 0, 7, query(0, 20)));
        assertThrows(IllegalArgumentException.class, () -> dao.findOwned(connection, 1, 0, query(0, 20)));
        assertThrows(IllegalArgumentException.class, () -> dao.findOwned(connection, 1, 7, null));
        verifyNoInteractions(connection);
    }

    private void validRow(boolean source) throws Exception {
        when(rows.next()).thenReturn(true, false);
        when(rows.getBoolean("selected_is_source")).thenReturn(source);
        when(rows.getBoolean("selected_is_destination")).thenReturn(!source);
        when(rows.getLong("transaction_id")).thenReturn(1L);
        when(rows.getString("reference_number")).thenReturn(REF.value());
        when(rows.getString("transaction_type")).thenReturn("TRANSFER");
        when(rows.getString("status")).thenReturn("SUCCESS");
        when(rows.getBigDecimal("amount")).thenReturn(Money.parse("0.25").toBigDecimal());
        when(rows.getTimestamp(eq("created_at"), any(Calendar.class))).thenReturn(Timestamp.from(TIME));
    }
}

