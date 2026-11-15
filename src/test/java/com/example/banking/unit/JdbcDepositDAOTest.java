package com.example.banking.unit;

import com.example.banking.dao.jdbc.*;
import com.example.banking.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.*;
import java.time.*;
import java.util.Calendar;
import static com.example.banking.unit.DepositFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JdbcDepositDAOTest {
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
        when(query.executeUpdate()).thenReturn(1);
        when(query.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(false);
    }

    @Test void lockingReadUsesBothOwnerAndAccountPredicatesWithForUpdate() throws Exception {
        assertTrue(accounts.lockOwned(connection, 1, 7).isEmpty());
        var sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("WHERE customer_id = ?"));
        assertTrue(sql.getValue().endsWith("AND account_id = ? FOR UPDATE"));
        verify(query).setLong(1, 1);
        verify(query).setLong(2, 7);
        verify(query).setQueryTimeout(10);
        verify(rows).close();
    }

    @Test void balanceWriteGuardsOwnerStatusTypeAndPreviousValue() throws Exception {
        accounts.updateBalance(connection, 1, 7, Money.ZERO, Money.parse("25"));
        var sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("WHERE customer_id = ? AND account_id = ? AND balance = ?"));
        assertTrue(sql.getValue().contains("status = 'ACTIVE'"));
        assertTrue(sql.getValue().contains("account_type IN ('SAVINGS', 'CURRENT')"));
        verify(query).setBigDecimal(1, Money.parse("25").toBigDecimal());
        verify(query).setLong(2, 1);
        verify(query).setLong(3, 7);
        verify(query).setBigDecimal(4, Money.ZERO.toBigDecimal());
        verify(query).setQueryTimeout(10);
        verify(query).close();
        verify(connection, never()).commit();
    }

    @Test void balanceWriteCountMustBeExactlyOne() throws Exception {
        when(query.executeUpdate()).thenReturn(0, 2);
        assertThrows(SQLException.class, () -> accounts.updateBalance(connection, 1, 7, Money.ZERO, Money.parse("25")));
        assertThrows(SQLException.class, () -> accounts.updateBalance(connection, 1, 7, Money.ZERO, Money.parse("25")));
    }

    @Test void invalidBalancesRejectBeforeSql() {
        assertThrows(IllegalArgumentException.class, () -> accounts.updateBalance(connection, 1, 7, Money.ZERO, Money.parse("-1")));
        assertThrows(IllegalArgumentException.class, () -> accounts.updateBalance(connection, 1, 7, Money.ZERO, Money.ZERO));
        verifyNoInteractions(connection);
    }

    @Test void ledgerBindsDepositShapeAndExactDestinationSnapshots() throws Exception {
        var tx = receipt("25.10");
        ledger.insertDeposit(connection, tx);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("VALUES (?, NULL, ?, 'DEPOSIT', ?, 'SUCCESS', NULL, NULL, ?, NULL, ?, ?)"));
        assertFalse(sql.getValue().contains(REF.value()));
        verify(query).setString(1, REF.value());
        verify(query).setLong(2, 7);
        verify(query).setBigDecimal(3, Money.parse("25.10").toBigDecimal());
        verify(query).setBigDecimal(4, Money.ZERO.toBigDecimal());
        verify(query).setBigDecimal(5, Money.parse("25.10").toBigDecimal());
        var timestamp = ArgumentCaptor.forClass(Timestamp.class);
        var calendar = ArgumentCaptor.forClass(Calendar.class);
        verify(query).setTimestamp(eq(6), timestamp.capture(), calendar.capture());
        assertEquals(TIME, timestamp.getValue().toInstant());
        assertEquals("UTC", calendar.getValue().getTimeZone().getID());
        verify(query).setQueryTimeout(10);
        verify(query).close();
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection, never()).close();
    }

    @Test void unsupportedLedgerTimeIsRejectedWithoutRoundingOrPreparingSql() {
        for (Instant time : new Instant[]{TIME.plusNanos(1), Instant.parse("1970-01-01T00:00:00Z"),
                Instant.parse("2038-01-19T03:14:08Z")}) {
            var tx = BankTransaction.deposit(REF, account(1, AccountType.SAVINGS, AccountStatus.ACTIVE, "0"),
                    Money.parse("25"), time);
            assertThrows(IllegalArgumentException.class, () -> ledger.insertDeposit(connection, tx));
        }
        verifyNoInteractions(connection);
    }

    @Test void ledgerInsertCountAndCleanupFailureArePropagatedToTheTransactionOwner() throws Exception {
        when(query.executeUpdate()).thenReturn(0);
        assertThrows(SQLException.class, () -> ledger.insertDeposit(connection, receipt("25")));
        when(query.executeUpdate()).thenReturn(1);
        doThrow(new SQLException("cleanup")).when(query).close();
        assertThrows(SQLException.class, () -> ledger.insertDeposit(connection, receipt("25")));
        verify(connection, never()).commit();
    }
}
