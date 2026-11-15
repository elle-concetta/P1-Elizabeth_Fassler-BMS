package com.example.banking.unit;

import com.example.banking.dao.*;
import com.example.banking.exception.*;
import com.example.banking.model.*;
import com.example.banking.service.AccountService;
import com.example.banking.util.ConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

import static com.example.banking.unit.AccountFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountServiceTest {
    private ConnectionFactory connections;
    private Connection connection;
    private AccountDAO accounts;
    private CustomerDAO customers;
    private Supplier<Customer> identity;
    private Supplier<AccountNumber> numbers;
    private AccountService service;

    @BeforeEach @SuppressWarnings("unchecked")
    void setup() throws Exception {
        connections = mock(ConnectionFactory.class);
        connection = mock(Connection.class);
        accounts = mock(AccountDAO.class);
        customers = mock(CustomerDAO.class);
        identity = mock(Supplier.class);
        numbers = mock(Supplier.class);
        when(identity.get()).thenReturn(customer(1, CustomerStatus.ACTIVE));
        when(numbers.get()).thenReturn(NUMBER);
        when(connections.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(mock(Statement.class));
        when(customers.findProfile(eq(connection), eq(1L), anyBoolean()))
                .thenReturn(Optional.of(customer(1, CustomerStatus.ACTIVE)));
        when(accounts.insert(eq(connection), eq(1L), any(), any(), eq(DATE))).thenReturn(7L);
        when(accounts.findOwned(connection, 1, 7)).thenReturn(Optional.of(
                account(AccountType.SAVINGS, 7, 1, NUMBER, AccountStatus.ACTIVE)));
        service = new AccountService(connections, accounts, customers, identity, numbers,
                Clock.fixed(DATE.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC), 10);
    }

    @ParameterizedTest @EnumSource(value = AccountType.class, names = {"SAVINGS", "CURRENT"})
    void openingUsesSessionOwnerZeroBalanceAndOneCommit(AccountType type) throws Exception {
        Account expected = account(type, 7, 1, NUMBER, AccountStatus.ACTIVE);
        when(accounts.findOwned(connection, 1, 7)).thenReturn(Optional.of(expected));
        assertSame(expected, service.open(type));
        var order = inOrder(connection, customers, accounts);
        order.verify(connection).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        order.verify(connection).setAutoCommit(false);
        order.verify(customers).findProfile(connection, 1, true);
        order.verify(accounts).insert(connection, 1, NUMBER, type, DATE);
        order.verify(accounts).findOwned(connection, 1, 7);
        order.verify(connection).commit();
        verify(connection, never()).rollback();
        verify(connection).close();
    }

    @Test void knownNumberCollisionGeneratesAnotherCandidateWithoutReplayingCommit() throws Exception {
        when(numbers.get()).thenReturn(NUMBER, SECOND);
        when(accounts.insert(connection, 1, NUMBER, AccountType.SAVINGS, DATE))
                .thenThrow(new AccountDAO.NumberCollision());
        when(accounts.findOwned(connection, 1, 7)).thenReturn(Optional.of(
                account(AccountType.SAVINGS, 7, 1, SECOND, AccountStatus.ACTIVE)));
        assertEquals(SECOND, service.open(AccountType.SAVINGS).number());
        verify(numbers, times(2)).get();
        verify(connection).commit();
    }

    @Test void customerMayOpenMultipleIndependentAccounts() throws Exception {
        when(numbers.get()).thenReturn(NUMBER, SECOND);
        when(accounts.insert(eq(connection), eq(1L), any(), any(), eq(DATE))).thenReturn(7L, 8L);
        when(accounts.findOwned(connection, 1, 8)).thenReturn(Optional.of(
                account(AccountType.CURRENT, 8, 1, SECOND, AccountStatus.ACTIVE)));
        assertEquals(7, service.open(AccountType.SAVINGS).accountId());
        assertEquals(8, service.open(AccountType.CURRENT).accountId());
        verify(connection, times(2)).commit();
    }

    @Test void collisionsStopAfterThreeRejectedInsertsAndRollback() throws Exception {
        when(accounts.insert(any(), anyLong(), any(), any(), any())).thenThrow(new AccountDAO.NumberCollision());
        assertThrows(ValidationException.class, () -> service.open(AccountType.SAVINGS));
        verify(accounts, times(3)).insert(connection, 1, NUMBER, AccountType.SAVINGS, DATE);
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @ParameterizedTest @EnumSource(value = AccountType.class, names = {"DEPOSIT", "LOAN"})
    void incompleteOpeningFlowsAreBlockedBeforeJdbc(AccountType type) {
        assertThrows(ValidationException.class, () -> service.open(type));
        verifyNoInteractions(connections, accounts, numbers);
    }

    @Test void missingSessionAndExpiredSessionBlockAllOperationsBeforeJdbc() {
        when(identity.get()).thenThrow(new SessionExpiredException());
        assertThrows(SessionExpiredException.class, () -> service.open(AccountType.SAVINGS));
        assertThrows(SessionExpiredException.class, service::list);
        assertThrows(SessionExpiredException.class, () -> service.details(7));
        verifyNoInteractions(connections, accounts);
    }

    @ParameterizedTest @EnumSource(value = CustomerStatus.class, names = {"LOCKED", "CLOSED"})
    void changedCustomerStatusBlocksOpeningAndReads(CustomerStatus status) throws Exception {
        when(customers.findProfile(eq(connection), eq(1L), anyBoolean()))
                .thenReturn(Optional.of(customer(1, status)));
        assertThrows(AuthenticationException.class, () -> service.open(AccountType.SAVINGS));
        assertThrows(AuthenticationException.class, service::list);
        assertThrows(AuthenticationException.class, () -> service.details(7));
        verifyNoInteractions(accounts);
    }

    @Test void missingOrForeignProfileCannotOpenAnAccount() throws Exception {
        when(customers.findProfile(connection, 1, true)).thenReturn(
                Optional.empty(), Optional.of(customer(2, CustomerStatus.ACTIVE)));
        assertThrows(AuthenticationException.class, () -> service.open(AccountType.SAVINGS));
        assertThrows(AuthenticationException.class, () -> service.open(AccountType.SAVINGS));
        verifyNoInteractions(accounts);
    }

    @Test void listOnlyUsesSessionOwnerAndReturnsAnImmutableSnapshot() throws Exception {
        when(accounts.findOwned(connection, 1)).thenReturn(List.of(
                account(AccountType.SAVINGS, 7, 1, NUMBER, AccountStatus.CLOSED),
                account(AccountType.CURRENT, 8, 1, SECOND, AccountStatus.FROZEN)));
        var result = service.list();
        assertEquals(2, result.size());
        assertThrows(UnsupportedOperationException.class, () -> result.clear());
        verify(connection).setReadOnly(true);
        verify(accounts).findOwned(connection, 1);
        verify(connection, never()).commit();
    }

    @Test void foreignRowInListFailsClosedWithoutReturningPartialData() throws Exception {
        when(accounts.findOwned(connection, 1)).thenReturn(List.of(
                account(AccountType.SAVINGS, 7, 2, NUMBER, AccountStatus.ACTIVE)));
        assertThrows(PersistenceException.class, service::list);
    }

    @Test void missingAndForeignDetailsHaveTheSameSafeMessage() throws Exception {
        when(accounts.findOwned(connection, 1, 7)).thenReturn(Optional.empty(),
                Optional.of(account(AccountType.SAVINGS, 7, 2, NUMBER, AccountStatus.ACTIVE)),
                Optional.of(account(AccountType.SAVINGS, 8, 1, NUMBER, AccountStatus.ACTIVE)));
        for (int i = 0; i < 3; i++)
            assertEquals("Account not found.", assertThrows(ValidationException.class, () -> service.details(7)).getMessage());
    }

    @Test void existingOwnedDetailsRemainReadable() {
        assertEquals(NUMBER, service.details(7).number());
    }

    @Test void invalidIdAndTypeAreRejectedBeforeOpeningConnection() {
        assertThrows(ValidationException.class, () -> service.details(-1));
        assertThrows(ValidationException.class, () -> service.open(null));
        verifyNoInteractions(connections);
    }

    @Test void commitFailureIsNeverRetriedAndBlocksFurtherOpeningButAllowsEnquiry() throws Exception {
        doThrow(new SQLException("SECRET")).when(connection).commit();
        var failure = assertThrows(AccountOpeningUnconfirmedException.class, () -> service.open(AccountType.SAVINGS));
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        assertFalse(failure.getMessage().contains("SECRET"));
        assertThrows(AccountOpeningUnconfirmedException.class, () -> service.open(AccountType.CURRENT));
        verify(accounts, times(1)).insert(any(), anyLong(), any(), any(), any());
        when(accounts.findOwned(connection, 1)).thenReturn(List.of());
        assertTrue(service.list().isEmpty());
    }

    @Test void insertErrorIsNotTreatedAsANumberCollision() throws Exception {
        when(accounts.insert(any(), anyLong(), any(), any(), any())).thenThrow(new SQLException("SECRET", "40001", 1213));
        assertThrows(AccountOpeningUnconfirmedException.class, () -> service.open(AccountType.SAVINGS));
        verify(accounts).insert(any(), anyLong(), any(), any(), any());
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test void numericDuplicateCodeWithoutExplicitRejectedInsertContractIsNotRetried() throws Exception {
        when(accounts.insert(any(), anyLong(), any(), any(), any())).thenThrow(new SQLException("SECRET", "23000", 1062));
        assertThrows(AccountOpeningUnconfirmedException.class, () -> service.open(AccountType.SAVINGS));
        verify(numbers).get();
    }

    @Test void failedReadBackRollsBackAndDoesNotClaimOpeningSucceeded() throws Exception {
        when(accounts.findOwned(connection, 1, 7)).thenReturn(Optional.empty());
        assertThrows(AccountOpeningUnconfirmedException.class, () -> service.open(AccountType.SAVINGS));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test void coercedReadBackIsRejectedBeforeCommit() throws Exception {
        when(accounts.findOwned(connection, 1, 7)).thenReturn(Optional.of(
                account(AccountType.CURRENT, 7, 1, NUMBER, AccountStatus.ACTIVE)));
        assertThrows(AccountOpeningUnconfirmedException.class, () -> service.open(AccountType.SAVINGS));
        verify(connection, never()).commit();
    }

    @Test void closeFailureAfterCommitIsUnconfirmedAndDoesNotRepeatInsert() throws Exception {
        doThrow(new SQLException("SECRET")).when(connection).close();
        assertThrows(AccountOpeningUnconfirmedException.class, () -> service.open(AccountType.SAVINGS));
        assertThrows(AccountOpeningUnconfirmedException.class, () -> service.open(AccountType.SAVINGS));
        verify(connection).commit();
        verify(accounts).insert(any(), anyLong(), any(), any(), any());
    }

    @Test void readAndConnectionErrorsAreSanitized() throws Exception {
        when(connections.getConnection()).thenThrow(new SQLException("SECRET"));
        var failure = assertThrows(PersistenceException.class, service::list);
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        assertFalse(failure.getMessage().contains("SECRET"));
        assertThrows(PersistenceException.class, () -> service.open(AccountType.SAVINGS));
    }
}

