package com.example.banking.unit;

import com.example.banking.dao.*;
import com.example.banking.dao.jdbc.JdbcTransactionAuditDAO;
import com.example.banking.exception.*;
import com.example.banking.model.*;
import com.example.banking.service.TransactionService;
import com.example.banking.util.ConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.sql.*;
import java.time.*;
import java.util.Optional;
import java.util.function.Supplier;

import static com.example.banking.unit.DepositFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DepositServiceTest {
    private ConnectionFactory connections;
    private Connection connection;
    private AccountDAO accounts;
    private CustomerDAO customers;
    private TransactionDAO ledger;
    private TransactionAuditDAO audits;
    private Supplier<Customer> identity;
    private Supplier<TransactionReference> references;
    private TransactionService service;

    @BeforeEach @SuppressWarnings("unchecked")
    void setup() throws Exception {
        connections = mock(ConnectionFactory.class);
        connection = mock(Connection.class);
        accounts = mock(AccountDAO.class);
        customers = mock(CustomerDAO.class);
        ledger = mock(TransactionDAO.class);
        audits = mock(TransactionAuditDAO.class);
        identity = mock(Supplier.class);
        references = mock(Supplier.class);
        when(identity.get()).thenReturn(customer(1, CustomerStatus.ACTIVE));
        when(references.get()).thenReturn(REF);
        when(connections.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(mock(Statement.class));
        when(customers.findProfile(connection, 1, true)).thenReturn(Optional.of(customer(1, CustomerStatus.ACTIVE)));
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.of(account(1, AccountType.SAVINGS, AccountStatus.ACTIVE, "0")));
        when(accounts.findOwned(connection, 1, 7)).thenReturn(Optional.of(account(1, AccountType.SAVINGS, AccountStatus.ACTIVE, "25")));
        service = service(CLOCK, audits);
    }

    private TransactionService service(Clock clock, TransactionAuditDAO writer) {
        return new TransactionService(connections, accounts, customers, ledger, writer,
                identity, references, clock, LIMIT, 10);
    }

    @Test void successCommitsBalanceAndLedgerTogetherThenAuditsAfterClosingPrimary() throws Exception {
        BankTransaction result = service.deposit(7, "25.00");
        assertEquals(Money.parse("25"), result.balanceAfter());
        assertTrue(service.pending().isEmpty());
        var order = inOrder(connection, customers, accounts, ledger, audits);
        order.verify(connection).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        order.verify(connection).setAutoCommit(false);
        order.verify(customers).findProfile(connection, 1, true);
        order.verify(accounts).lockOwned(connection, 1, 7);
        order.verify(accounts).updateBalance(connection, 1, 7, Money.ZERO, Money.parse("25"));
        order.verify(ledger).insertDeposit(eq(connection), same(result));
        order.verify(accounts).findOwned(connection, 1, 7);
        order.verify(connection).commit();
        order.verify(connection).close();
        order.verify(audits).append(argThat(e -> e.outcome() == AuditOutcome.SUCCESS
                && e.referenceNumber().orElseThrow().equals(REF) && e.failureCode().isEmpty()));
        verify(connection, never()).rollback();
        verify(references).get();
    }

    @Test void currentAccountIsEligibleToo() throws Exception {
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.of(account(1, AccountType.CURRENT, AccountStatus.ACTIVE, "0")));
        when(accounts.findOwned(connection, 1, 7)).thenReturn(Optional.of(account(1, AccountType.CURRENT, AccountStatus.ACTIVE, "25")));
        assertEquals(Money.parse("25"), service.deposit(7, "25").balanceAfter());
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1.001", "1000000.01", "abc", "1e2"})
    void invalidAmountRejectsBeforeMoneyJdbcButPreservesIndependentAttemptEvidence(String amount) {
        assertThrows(DepositException.class, () -> service.deposit(7, amount));
        verifyNoInteractions(connections, accounts, ledger);
        verify(audits).append(argThat(e -> e.outcome() == AuditOutcome.REJECTED
                && e.failureCode().orElseThrow().equals("INVALID_AMOUNT")));
    }

    @Test void invalidAccountIdRejectsBeforeMoneySql() {
        assertThrows(DepositException.class, () -> service.deposit(0, "25"));
        verifyNoInteractions(connections, accounts, ledger);
        verify(audits).append(argThat(e -> e.failureCode().orElseThrow().equals("INVALID_ACCOUNT_ID")));
    }

    @Test void expiredSessionDoesNotAllocateReferenceOrAttemptAnyWrite() {
        when(identity.get()).thenThrow(new SessionExpiredException());
        assertThrows(SessionExpiredException.class, () -> service.deposit(7, "25"));
        verifyNoInteractions(connections, accounts, ledger, audits, references);
    }

    @Test void foreignAndMissingAccountsReturnTheSameSafeRejection() throws Exception {
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.empty(),
                Optional.of(account(2, AccountType.SAVINGS, AccountStatus.ACTIVE, "100")));
        String first = assertThrows(DepositException.class, () -> service.deposit(7, "25")).getMessage();
        String second = assertThrows(DepositException.class, () -> service.deposit(7, "25")).getMessage();
        assertEquals(first, second);
        assertTrue(first.contains("Account not found."));
        verify(accounts, never()).updateBalance(any(), anyLong(), anyLong(), any(), any());
        verifyNoInteractions(ledger);
    }

    @ParameterizedTest @EnumSource(value = AccountStatus.class, names = {"FROZEN", "CLOSED", "MATURED"})
    void nonActiveAccountsRejectUnderLock(AccountStatus status) throws Exception {
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.of(account(1, AccountType.SAVINGS, status, "0")));
        assertThrows(DepositException.class, () -> service.deposit(7, "25"));
        verify(audits).append(argThat(e -> e.failureCode().orElseThrow().equals("ACCOUNT_UNAVAILABLE")));
        verifyNoInteractions(ledger);
        verify(connection).rollback();
    }

    @ParameterizedTest @EnumSource(value = AccountType.class, names = {"DEPOSIT", "LOAN"})
    void dedicatedPrincipalAccountsCannotReceiveOrdinaryDeposits(AccountType type) throws Exception {
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.of(account(1, type, AccountStatus.ACTIVE, "0")));
        assertThrows(DepositException.class, () -> service.deposit(7, "25"));
        verify(audits).append(argThat(e -> e.failureCode().orElseThrow().equals("ACCOUNT_TYPE_NOT_ELIGIBLE")));
        verifyNoInteractions(ledger);
    }

    @ParameterizedTest @EnumSource(value = CustomerStatus.class, names = {"LOCKED", "CLOSED"})
    void changedCustomerStatusRejectsBeforeAccountAccess(CustomerStatus status) throws Exception {
        when(customers.findProfile(connection, 1, true)).thenReturn(Optional.of(customer(1, status)));
        assertThrows(DepositException.class, () -> service.deposit(7, "25"));
        verifyNoInteractions(accounts, ledger);
        verify(audits).append(argThat(e -> e.failureCode().orElseThrow().equals("CUSTOMER_UNAVAILABLE")));
    }

    @Test void balanceOverflowRejectsWithoutCallingUpdateOrLedger() throws Exception {
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.of(account(1, AccountType.SAVINGS,
                AccountStatus.ACTIVE, "99999999999999999.99")));
        assertThrows(DepositException.class, () -> service.deposit(7, "0.01"));
        verify(accounts, never()).updateBalance(any(), anyLong(), anyLong(), any(), any());
        verifyNoInteractions(ledger);
        verify(audits).append(argThat(e -> e.failureCode().orElseThrow().equals("BALANCE_OVERFLOW")));
    }

    @Test void ledgerFailureRollsBackBalanceAndAuditsAfterRollbackAndClose() throws Exception {
        doThrow(new SQLException("SECRET")).when(ledger).insertDeposit(any(), any());
        var failure = assertThrows(DepositException.class, () -> service.deposit(7, "25"));
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        assertFalse(failure.getMessage().contains("SECRET"));
        var order = inOrder(connection, audits);
        order.verify(connection).rollback();
        order.verify(connection).close();
        order.verify(audits).append(argThat(e -> e.outcome() == AuditOutcome.ROLLED_BACK));
        verify(connection, never()).commit();
    }

    @Test void balanceUpdateFailureCannotInsertALedgerRow() throws Exception {
        doThrow(new SQLException("SECRET")).when(accounts).updateBalance(any(), anyLong(), anyLong(), any(), any());
        assertThrows(DepositException.class, () -> service.deposit(7, "25"));
        verifyNoInteractions(ledger);
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test void readBackMismatchRollsBackBothChanges() throws Exception {
        when(accounts.findOwned(connection, 1, 7)).thenReturn(Optional.of(account(1, AccountType.SAVINGS, AccountStatus.ACTIVE, "24.99")));
        assertThrows(DepositException.class, () -> service.deposit(7, "25"));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test void referenceCollisionDoesNotGenerateANewReferenceOrRetryMoney() throws Exception {
        doThrow(new SQLException("duplicate", "23000", 1062)).when(ledger).insertDeposit(any(), any());
        assertThrows(DepositException.class, () -> service.deposit(7, "25"));
        verify(references).get();
        verify(ledger).insertDeposit(any(), any());
        verify(accounts).updateBalance(any(), anyLong(), anyLong(), any(), any());
        assertTrue(service.pending().isEmpty()); // rollback and audit were confirmed
    }

    @Test void commitExceptionCannotBeCalledARollbackEvenIfRollbackReturns() throws Exception {
        doThrow(new SQLException("SECRET")).when(connection).commit();
        assertThrows(DepositException.class, () -> service.deposit(7, "25"));
        var pending = service.pending().orElseThrow();
        assertEquals(TransactionService.MoneyState.UNCONFIRMED, pending.moneyState());
        assertEquals(TransactionService.AuditState.SAVED, pending.auditState());
        assertEquals(AuditOutcome.FAILED, pending.evidence().outcome());
        assertEquals("COMMIT_UNCONFIRMED", pending.evidence().failureCode().orElseThrow());
        assertThrows(DepositException.class, () -> service.deposit(7, "25"));
        assertSame(pending, service.pending().orElseThrow());
        verify(ledger).insertDeposit(any(), any());
    }

    @Test void rollbackFailureRetainsUnconfirmedMoneyEvidence() throws Exception {
        doThrow(new SQLException("SECRET")).when(ledger).insertDeposit(any(), any());
        doThrow(new SQLException("PRIVATE")).when(connection).rollback();
        assertThrows(DepositException.class, () -> service.deposit(7, "25"));
        assertEquals("ROLLBACK_UNCONFIRMED", service.pending().orElseThrow().evidence().failureCode().orElseThrow());
    }

    @Test void closeFailureAfterCommitNeverClaimsSuccessOrReplays() throws Exception {
        doThrow(new SQLException("SECRET")).when(connection).close();
        assertThrows(DepositException.class, () -> service.deposit(7, "25"));
        assertEquals(TransactionService.MoneyState.UNCONFIRMED, service.pending().orElseThrow().moneyState());
        assertEquals("CLEANUP_UNCONFIRMED", service.pending().orElseThrow().evidence().failureCode().orElseThrow());
        assertThrows(DepositException.class, () -> service.deposit(7, "25"));
        verify(ledger).insertDeposit(any(), any());
    }

    @Test void postCommitAuditFailureRetainsExactSuccessEvidenceWithoutReplayingDeposit() throws Exception {
        doThrow(new PersistenceException(new SQLException("SECRET"))).when(audits).append(any());
        var failure = assertThrows(DepositException.class, () -> service.deposit(7, "25"));
        assertTrue(failure.getMessage().contains("Deposit committed"));
        assertFalse(failure.getMessage().contains("SECRET"));
        var pending = service.pending().orElseThrow();
        assertEquals(AuditOutcome.SUCCESS, pending.evidence().outcome());
        assertEquals(REF, pending.evidence().referenceNumber().orElseThrow());
        assertEquals(TransactionService.MoneyState.COMMITTED, pending.moneyState());
        assertEquals(TransactionService.AuditState.SAVE_UNCERTAIN, pending.auditState());
        assertThrows(DepositException.class, () -> service.deposit(7, "25"));
        verify(audits).append(same(pending.evidence()));
        verify(ledger).insertDeposit(any(), any());
        verify(references).get();
    }

    @Test void rejectedAuditTimeIsRetainedExactlyAndNoMoneySqlRuns() {
        Instant fractional = TIME.plusNanos(123);
        service = service(Clock.fixed(fractional, ZoneOffset.UTC), audits);
        doThrow(new IllegalArgumentException("unsupported time")).when(audits).append(any());
        assertThrows(DepositException.class, () -> service.deposit(7, "25"));
        var pending = service.pending().orElseThrow();
        assertEquals(fractional, pending.evidence().createdAt());
        assertEquals(TransactionService.MoneyState.UNCHANGED, pending.moneyState());
        assertEquals(TransactionService.AuditState.REJECTED_NO_WRITE, pending.auditState());
        verifyNoInteractions(connections, accounts, ledger);
        assertFalse(pending.toString().contains(REF.value()));
        assertFalse(pending.toString().contains("customer"));
    }

    @Test void auditFailureOnInvalidAmountRetainsRejectionInsteadOfLosingEarlierEvidence() {
        doThrow(new PersistenceException(null)).when(audits).append(any());
        assertThrows(DepositException.class, () -> service.deposit(7, "0"));
        var exact = service.pending().orElseThrow().evidence();
        assertThrows(DepositException.class, () -> service.deposit(7, "25"));
        assertSame(exact, service.pending().orElseThrow().evidence());
        verify(audits).append(same(exact));
        verifyNoInteractions(connections, accounts, ledger);
    }

    @Test void connectionFailureAuditsFailedNotSuccessAndExposesNoDriverGraph() throws Exception {
        when(connections.getConnection()).thenThrow(new SQLException("SECRET"));
        var failure = assertThrows(DepositException.class, () -> service.deposit(7, "25"));
        assertFalse(failure.getMessage().contains("SECRET"));
        verify(audits).append(argThat(e -> e.outcome() == AuditOutcome.FAILED
                && e.failureCode().orElseThrow().equals("DATABASE_UNAVAILABLE")));
    }

    @Test void realAuditWriterAcquiresASecondConnectionOnlyAfterTheMoneyConnectionCloses() throws Exception {
        Connection independent = mock(Connection.class);
        PreparedStatement auditQuery = mock(PreparedStatement.class);
        when(connections.getConnection()).thenReturn(connection, independent);
        when(independent.prepareStatement(anyString())).thenReturn(auditQuery);
        when(auditQuery.executeUpdate()).thenReturn(1);
        service = service(CLOCK, new JdbcTransactionAuditDAO(connections, 10));
        assertNotNull(service.deposit(7, "25"));
        var order = inOrder(connections, connection, independent);
        order.verify(connections).getConnection();
        order.verify(connection).commit();
        order.verify(connection).close();
        order.verify(connections).getConnection();
        order.verify(independent).setAutoCommit(false);
        order.verify(independent).commit();
        order.verify(independent).close();
    }
}
