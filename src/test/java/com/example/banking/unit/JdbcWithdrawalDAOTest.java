package com.example.banking.unit;

import com.example.banking.dao.jdbc.JdbcTransactionDAO;
import com.example.banking.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.*;
import java.time.Instant;
import java.util.Calendar;

import static com.example.banking.unit.DepositFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JdbcWithdrawalDAOTest {
    private Connection connection;
    private PreparedStatement query;
    private final JdbcTransactionDAO ledger = new JdbcTransactionDAO(10);

    @BeforeEach void setup() throws Exception {
        connection = mock(Connection.class);
        query = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);
    }

    private BankTransaction tx(Instant time) {
        return BankTransaction.withdrawal(REF, account(1, AccountType.SAVINGS, AccountStatus.ACTIVE, "100"),
                Money.parse("25.10"), time);
    }

    @Test void withdrawalBindsSourceAndNullDestinationWithExactSourceSnapshots() throws Exception {
        ledger.insertWithdrawal(connection, tx(TIME));
        var sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("VALUES (?, ?, NULL, 'WITHDRAWAL', ?, 'SUCCESS', NULL, ?, NULL, ?, NULL, ?)"));
        assertFalse(sql.getValue().contains(REF.value()));
        verify(query).setString(1, REF.value());
        verify(query).setLong(2, 7);
        verify(query).setBigDecimal(3, Money.parse("25.10").toBigDecimal());
        verify(query).setBigDecimal(4, Money.parse("100").toBigDecimal());
        verify(query).setBigDecimal(5, Money.parse("74.90").toBigDecimal());
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

    @Test void insertCannotConfuseDepositAndWithdrawalShapes() {
        assertThrows(IllegalArgumentException.class, () -> ledger.insertWithdrawal(connection, receipt("25")));
        assertThrows(IllegalArgumentException.class, () -> ledger.insertDeposit(connection, tx(TIME)));
        verifyNoInteractions(connection);
    }

    @Test void nullOrUnsupportedTimeRejectsBeforePreparingSql() {
        assertThrows(IllegalArgumentException.class, () -> ledger.insertWithdrawal(connection, null));
        for (Instant time : new Instant[]{TIME.plusNanos(1), Instant.parse("1970-01-01T00:00:00Z"),
                Instant.parse("2038-01-19T03:14:08Z")}) {
            assertThrows(IllegalArgumentException.class, () -> ledger.insertWithdrawal(connection, tx(time)));
        }
        verifyNoInteractions(connection);
    }

    @Test void exactlyOneInsertIsRequired() throws Exception {
        when(query.executeUpdate()).thenReturn(0, 2);
        assertThrows(SQLException.class, () -> ledger.insertWithdrawal(connection, tx(TIME)));
        assertThrows(SQLException.class, () -> ledger.insertWithdrawal(connection, tx(TIME)));
        verify(connection, never()).commit();
    }

    @Test void statementCleanupFailureReachesTheMoneyTransactionOwner() throws Exception {
        doThrow(new SQLException("cleanup")).when(query).close();
        assertThrows(SQLException.class, () -> ledger.insertWithdrawal(connection, tx(TIME)));
        verify(connection, never()).commit();
    }
}

