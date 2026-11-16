package com.example.banking.unit;

import com.example.banking.dao.jdbc.*;
import com.example.banking.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.*;
import java.util.Calendar;

import static com.example.banking.unit.DepositFixtures.*;
import static com.example.banking.unit.TransferFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JdbcTransferDAOTest {
    private Connection connection;
    private PreparedStatement query;
    private ResultSet rows;
    private final JdbcAccountDAO accounts = new JdbcAccountDAO(10);
    private final JdbcTransactionDAO ledger = new JdbcTransactionDAO(10);

    @BeforeEach void setup() throws Exception {
        connection = mock(Connection.class);
        query = mock(PreparedStatement.class);
        rows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(query);
        when(query.executeQuery()).thenReturn(rows);
        when(query.executeUpdate()).thenReturn(1);
    }

    @Test void routingBindsNumberButSelectsOnlyAnInternalId() throws Exception {
        when(rows.next()).thenReturn(true, false);
        when(rows.getLong(1)).thenReturn(11L);
        assertEquals(11, accounts.findDestinationId(connection, RECIPIENT).orElseThrow());
        verify(connection).prepareStatement("SELECT account_id FROM accounts WHERE account_number = ?");
        verify(query).setString(1, RECIPIENT.value());
        verify(query).setQueryTimeout(10);
        verify(rows).close();
        verify(query).close();
        verify(connection, never()).commit();
    }

    @Test void routingHandlesMissingRowsAndRejectsInvalidOrAmbiguousIds() throws Exception {
        assertTrue(accounts.findDestinationId(connection, RECIPIENT).isEmpty());
        when(rows.next()).thenReturn(true, false);
        when(rows.getLong(1)).thenReturn(0L);
        assertThrows(SQLException.class, () -> accounts.findDestinationId(connection, RECIPIENT));
        when(rows.next()).thenReturn(true, true);
        when(rows.getLong(1)).thenReturn(11L);
        assertThrows(SQLException.class, () -> accounts.findDestinationId(connection, RECIPIENT));
    }

    @Test void destinationLockIsByInternalIdAndHeldByTheCaller() throws Exception {
        assertTrue(accounts.lockDestination(connection, 11).isEmpty());
        var sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("WHERE account_id = ? FOR UPDATE"));
        assertFalse(sql.getValue().contains("customer_id = ?"));
        verify(query).setLong(1, 11);
        verify(query).setQueryTimeout(10);
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();
    }

    @Test void invalidRoutingInputsRejectBeforePreparingSql() {
        assertThrows(IllegalArgumentException.class, () -> accounts.findDestinationId(connection, null));
        assertThrows(com.example.banking.exception.ValidationException.class,
                () -> accounts.lockDestination(connection, 0));
        verifyNoInteractions(connection);
    }

    @Test void transferBindsBothLegsAndAllFourExactSnapshotsUsingUtc() throws Exception {
        ledger.insertTransfer(connection, candidate());
        var sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("VALUES (?, ?, ?, 'TRANSFER', ?, 'SUCCESS', NULL, ?, ?, ?, ?, ?)"));
        verify(query).setString(1, REF.value());
        verify(query).setLong(2, 7);
        verify(query).setLong(3, 11);
        verify(query).setBigDecimal(4, Money.parse("25").toBigDecimal());
        verify(query).setBigDecimal(5, Money.parse("100").toBigDecimal());
        verify(query).setBigDecimal(6, Money.parse("20").toBigDecimal());
        verify(query).setBigDecimal(7, Money.parse("75").toBigDecimal());
        verify(query).setBigDecimal(8, Money.parse("45").toBigDecimal());
        var time = ArgumentCaptor.forClass(Timestamp.class);
        var calendar = ArgumentCaptor.forClass(Calendar.class);
        verify(query).setTimestamp(eq(9), time.capture(), calendar.capture());
        assertEquals(TIME, time.getValue().toInstant());
        assertEquals("UTC", calendar.getValue().getTimeZone().getID());
        verify(query).setQueryTimeout(10);
        verify(query).close();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();
    }

    @Test void wrongShapeNullAndFractionalTimeCannotReachSql() {
        assertThrows(IllegalArgumentException.class, () -> ledger.insertTransfer(connection, null));
        assertThrows(IllegalArgumentException.class, () -> ledger.insertTransfer(connection, receipt("1")));
        var fractional = BankTransaction.transfer(REF, source("100"), recipient(11, 2, "20"),
                Money.parse("25"), TIME.plusNanos(1));
        assertThrows(IllegalArgumentException.class, () -> ledger.insertTransfer(connection, fractional));
        verifyNoInteractions(connection);
    }

    @Test void rowCountAndStatementCleanupMustBeConfirmedByTheCaller() throws Exception {
        when(query.executeUpdate()).thenReturn(0, 2);
        assertThrows(SQLException.class, () -> ledger.insertTransfer(connection, candidate()));
        assertThrows(SQLException.class, () -> ledger.insertTransfer(connection, candidate()));
        when(query.executeUpdate()).thenReturn(1);
        doThrow(new SQLException("cleanup")).when(query).close();
        assertThrows(SQLException.class, () -> ledger.insertTransfer(connection, candidate()));
    }
}

