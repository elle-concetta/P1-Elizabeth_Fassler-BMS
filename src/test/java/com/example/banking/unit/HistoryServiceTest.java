package com.example.banking.unit;

import com.example.banking.dao.*;
import com.example.banking.exception.*;
import com.example.banking.model.*;
import com.example.banking.service.HistoryService;
import com.example.banking.util.ConnectionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static com.example.banking.unit.DepositFixtures.*;
import static com.example.banking.unit.HistoryFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HistoryServiceTest {
    private ConnectionFactory connections;
    private Connection connection;
    private Statement setup;
    private CustomerDAO customers;
    private AccountDAO accounts;
    private TransactionHistoryDAO history;
    private Supplier<Customer> identity;
    private HistoryService service;

    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        connections = mock(ConnectionFactory.class); connection = mock(Connection.class); setup = mock(Statement.class);
        customers = mock(CustomerDAO.class); accounts = mock(AccountDAO.class); history = mock(TransactionHistoryDAO.class);
        identity = mock(Supplier.class);
        when(identity.get()).thenReturn(customer(1, CustomerStatus.ACTIVE));
        when(connections.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(setup);
        when(customers.findProfile(connection, 1, false)).thenReturn(Optional.of(customer(1, CustomerStatus.ACTIVE)));
        when(accounts.findOwned(connection, 1, 7)).thenReturn(Optional.of(selected(1, AccountStatus.ACTIVE)));
        when(history.findOwned(any(), anyLong(), anyLong(), any())).thenReturn(List.of());
        service = new HistoryService(connections, accounts, customers, history, identity, 10);
    }

    @Test void checksOwnershipBeforeHistoryAndClosesAnExplicitUtcReadOnlySnapshot() throws Exception {
        var query = query(0, 2);
        when(history.findOwned(connection, 1, 7, query)).thenReturn(List.of(row(3, TIME), row(2, TIME), row(1, TIME)));
        var result = service.page(7, query);
        assertEquals(List.of(3L, 2L), result.entries().stream().map(HistoryEntry::transactionId).toList());
        assertTrue(result.hasNext());
        var order = inOrder(identity, connection, setup, customers, accounts, history);
        order.verify(identity).get();
        order.verify(connection).setReadOnly(true);
        order.verify(connection).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        order.verify(connection).setAutoCommit(false);
        order.verify(setup).setQueryTimeout(10);
        order.verify(setup).execute("SET SESSION time_zone = '+00:00'");
        order.verify(setup).close();
        order.verify(customers).findProfile(connection, 1, false);
        order.verify(accounts).findOwned(connection, 1, 7);
        order.verify(history).findOwned(connection, 1, 7, query);
        order.verify(connection).rollback();
        order.verify(connection).close();
        verify(connection, never()).commit();
        verify(accounts, never()).updateBalance(any(), anyLong(), anyLong(), any(), any());
    }

    @Test void unauthorizedAndMissingAccountsAreIndistinguishableAndNeverQueryHistory() throws Exception {
        when(accounts.findOwned(connection, 1, 7)).thenReturn(Optional.empty());
        String missing = assertThrows(ValidationException.class, () -> service.page(7, query(0, 20))).getMessage();
        when(accounts.findOwned(connection, 1, 7)).thenReturn(Optional.of(selected(2, AccountStatus.ACTIVE)));
        String foreign = assertThrows(ValidationException.class, () -> service.page(7, query(0, 20))).getMessage();
        assertEquals("Account not found.", missing);
        assertEquals(missing, foreign);
        verifyNoInteractions(history);
    }

    @ParameterizedTest @EnumSource(AccountStatus.class)
    void ownedFrozenClosedAndMaturedAccountsRemainReadable(AccountStatus status) throws Exception {
        when(accounts.findOwned(connection, 1, 7)).thenReturn(Optional.of(selected(1, status)));
        assertTrue(service.page(7, query(0, 20)).entries().isEmpty());
    }

    @ParameterizedTest @EnumSource(value = CustomerStatus.class, names = {"LOCKED", "CLOSED"})
    void unavailableCustomerCannotReadAnyHistory(CustomerStatus status) throws Exception {
        when(customers.findProfile(connection, 1, false)).thenReturn(Optional.of(customer(1, status)));
        assertThrows(AuthenticationException.class, () -> service.page(7, query(0, 20)));
        verifyNoInteractions(accounts, history);
    }

    @Test void emptyAndFarBeyondLastPagesAreSafe() {
        var page = service.page(7, query(1000, 20));
        assertTrue(page.entries().isEmpty());
        assertFalse(page.hasNext());
        assertEquals(1000, page.query().page());
    }

    @Test void expiredSessionInvalidIdAndMissingQueryCannotAcquireAConnection() {
        assertThrows(ValidationException.class, () -> service.page(0, query(0, 20)));
        assertThrows(ValidationException.class, () -> service.page(7, null));
        when(identity.get()).thenThrow(new SessionExpiredException());
        assertThrows(SessionExpiredException.class, () -> service.page(7, query(0, 20)));
        verifyNoInteractions(connections, customers, accounts, history);
    }

    @Test void unorderedDuplicateOrOversizedResultsFailClosed() throws Exception {
        when(history.findOwned(any(), anyLong(), anyLong(), any())).thenReturn(
                List.of(row(1, TIME), row(2, TIME)),
                List.of(row(2, TIME), row(2, TIME)),
                List.of(row(4, TIME), row(3, TIME), row(2, TIME), row(1, TIME)));
        for (int i = 0; i < 3; i++) assertThrows(PersistenceException.class, () -> service.page(7, query(0, 2)));
    }

    @Test void cleanupFailuresDiscardPageAndNeverExposeDriverGraphs() throws Exception {
        doThrow(new SQLException("SECRET")).when(connection).close();
        var error = assertThrows(PersistenceException.class, () -> service.page(7, query(0, 20)));
        assertNull(error.getCause());
        assertEquals(0, error.getSuppressed().length);
        assertFalse(error.getMessage().contains("SECRET"));
    }

    @Test void rollbackAndQueryFailuresAreSafeAndConnectionStillCloses() throws Exception {
        doThrow(new SQLException("SECRET")).when(connection).rollback();
        assertThrows(PersistenceException.class, () -> service.page(7, query(0, 20)));
        verify(connection).close();
    }

    @Test void denialDoesNotRetainSuppressedCleanupExceptions() throws Exception {
        when(accounts.findOwned(connection, 1, 7)).thenReturn(Optional.empty());
        doThrow(new SQLException("SECRET")).when(connection).close();
        var error = assertThrows(ValidationException.class, () -> service.page(7, query(0, 20)));
        assertEquals(0, error.getSuppressed().length);
        assertNull(error.getCause());
    }

    @Test void fixedDatasetWithEqualTimestampsHasNoDuplicatesOrOmissionsAcrossPages() throws Exception {
        List<HistoryEntry> fixed = List.of(row(7, TIME.plusSeconds(1)), row(6, TIME.plusSeconds(1)),
                row(5, TIME), row(4, TIME), row(3, TIME), row(2, TIME.minusSeconds(1)), row(1, TIME.minusSeconds(1)));
        when(history.findOwned(any(), anyLong(), anyLong(), any())).thenAnswer(call -> {
            HistoryQuery q = call.getArgument(3);
            return fixed.stream().skip(q.offset()).limit(q.fetchLimit()).toList();
        });
        List<Long> seen = new ArrayList<>();
        var q = query(0, 2);
        while (true) {
            var result = service.page(7, q);
            result.entries().forEach(row -> seen.add(row.transactionId()));
            if (!result.hasNext()) break;
            q = q.next();
        }
        assertEquals(List.of(7L, 6L, 5L, 4L, 3L, 2L, 1L), seen);
    }

    @Test void exactHalfOpenBoundaryFiltersReachTheDaoUnchanged() throws Exception {
        var start = LocalDate.of(2026, 11, 15);
        var q = new HistoryQuery(0, 2, start, start.plusDays(1), TransactionType.TRANSFER);
        service.page(7, q);
        verify(history).findOwned(connection, 1, 7, q);
    }
}

