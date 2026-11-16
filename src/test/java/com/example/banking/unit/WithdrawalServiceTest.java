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

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.Optional;
import java.util.function.Supplier;

import static com.example.banking.unit.DepositFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WithdrawalServiceTest {
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
        locked(AccountType.SAVINGS, "100", "75");
        service = service(CLOCK, audits);
    }

    private void locked(AccountType type, String before, String after) throws SQLException {
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.of(account(1, type, AccountStatus.ACTIVE, before)));
        when(accounts.findOwned(connection, 1, 7)).thenReturn(Optional.of(account(1, type, AccountStatus.ACTIVE, after)));
    }

    private TransactionService service(Clock clock, TransactionAuditDAO writer) {
        return new TransactionService(connections, accounts, customers, ledger, writer,
                identity, references, clock, LIMIT, 10);
    }

    @ParameterizedTest @EnumSource(value = AccountType.class, names = {"SAVINGS", "CURRENT"})
    void lockedDebitAndLedgerCommitTogetherBeforeIndependentAudit(AccountType type) throws Exception {
        locked(type, "100", "75");
        var tx = service.withdraw(7, "25.00");
        assertEquals(Money.parse("75"), tx.balanceAfter());
        assertEquals(TransactionType.WITHDRAWAL, tx.type());
        var order = inOrder(connection, customers, accounts, ledger, audits);
        order.verify(connection).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        order.verify(connection).setAutoCommit(false);
        order.verify(customers).findProfile(connection, 1, true);
        order.verify(accounts).lockOwned(connection, 1, 7);
        order.verify(accounts).updateBalance(connection, 1, 7, Money.parse("100"), Money.parse("75"));
        order.verify(ledger).insertWithdrawal(connection, tx);
        order.verify(accounts).findOwned(connection, 1, 7);
        order.verify(connection).commit();
        order.verify(connection).close();
        order.verify(audits).append(argThat(e -> e.eventType() == TransactionType.WITHDRAWAL
                && e.outcome() == AuditOutcome.SUCCESS && e.referenceNumber().orElseThrow().equals(REF)));
        verify(ledger, never()).insertDeposit(any(), any());
        verify(connection, never()).rollback();
        assertTrue(service.pending().isEmpty());
    }

    @Test void exactFullBalanceDebitMayReachZeroWithoutOverdraft() throws Exception {
        locked(AccountType.CURRENT, "100", "0");
        assertEquals(Money.ZERO, service.withdraw(7, "100").balanceAfter());
    }

    @ParameterizedTest @EnumSource(value = AccountType.class, names = {"SAVINGS", "CURRENT"})
    void insufficientLockedFundsRejectWithoutAnyBalanceOrLedgerWrite(AccountType type) throws Exception {
        locked(type, "24.99", "0");
        assertThrows(WithdrawalException.class, () -> service.withdraw(7, "25"));
        verify(accounts, never()).updateBalance(any(), anyLong(), anyLong(), any(), any());
        verifyNoInteractions(ledger);
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(audits).append(argThat(e -> e.eventType() == TransactionType.WITHDRAWAL
                && e.outcome() == AuditOutcome.REJECTED && e.failureCode().orElseThrow().equals("INSUFFICIENT_FUNDS")));
    }

    @Test void storedMinimumIsCheckedAgainstTheLockedBalance() throws Exception {
        var minimum = new SavingsAccount(7, 1, NUMBER, Money.parse("100"), BigDecimal.ZERO,
                Money.parse("80"), LocalDate.of(2026, 11, 15), AccountStatus.ACTIVE);
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.of(minimum));
        assertThrows(WithdrawalException.class, () -> service.withdraw(7, "25"));
        verifyNoInteractions(ledger);
        verify(audits).append(argThat(e -> e.failureCode().orElseThrow().equals("MINIMUM_BALANCE")));
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1.001", "1e2", "1000000.01", " 1"})
    void invalidAmountsAreAuditedWithoutOpeningAMoneyConnection(String amount) {
        assertThrows(WithdrawalException.class, () -> service.withdraw(7, amount));
        verifyNoInteractions(connections, accounts, ledger);
        verify(audits).append(argThat(e -> e.eventType() == TransactionType.WITHDRAWAL
                && e.failureCode().orElseThrow().equals("INVALID_AMOUNT")));
    }

    @Test void invalidIdCannotReachMoneySql() {
        assertThrows(WithdrawalException.class, () -> service.withdraw(0, "25"));
        verifyNoInteractions(connections, accounts, ledger);
        verify(audits).append(argThat(e -> e.failureCode().orElseThrow().equals("INVALID_ACCOUNT_ID")));
    }

    @Test void missingAndForeignAccountsHaveTheSameSafeRejection() throws Exception {
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.empty());
        var missing = assertThrows(WithdrawalException.class, () -> service.withdraw(7, "25"));
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.of(account(2, AccountType.SAVINGS, AccountStatus.ACTIVE, "100")));
        var foreign = assertThrows(WithdrawalException.class, () -> service.withdraw(7, "25"));
        assertEquals(missing.getMessage(), foreign.getMessage());
        verifyNoInteractions(ledger);
        verify(accounts, never()).updateBalance(any(), anyLong(), anyLong(), any(), any());
    }

    @ParameterizedTest @EnumSource(value = AccountType.class, names = {"DEPOSIT", "LOAN"})
    void dedicatedPrincipalAccountsCannotBeDebited(AccountType type) throws Exception {
        locked(type, "100", "75");
        assertThrows(WithdrawalException.class, () -> service.withdraw(7, "25"));
        verifyNoInteractions(ledger);
        verify(audits).append(argThat(e -> e.failureCode().orElseThrow().equals("ACCOUNT_TYPE_NOT_ELIGIBLE")));
    }

    @ParameterizedTest @EnumSource(value = AccountStatus.class, names = {"FROZEN", "MATURED", "CLOSED"})
    void inactiveAccountsAreRejected(AccountStatus status) throws Exception {
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.of(account(1, AccountType.SAVINGS, status, "100")));
        assertThrows(WithdrawalException.class, () -> service.withdraw(7, "25"));
        verifyNoInteractions(ledger);
        verify(audits).append(argThat(e -> e.failureCode().orElseThrow().equals("ACCOUNT_UNAVAILABLE")));
    }

    @ParameterizedTest @EnumSource(value = CustomerStatus.class, names = {"LOCKED", "CLOSED"})
    void changedCustomerStatusStopsBeforeAccountAccess(CustomerStatus status) throws Exception {
        when(customers.findProfile(connection, 1, true)).thenReturn(Optional.of(customer(1, status)));
        assertThrows(WithdrawalException.class, () -> service.withdraw(7, "25"));
        verifyNoInteractions(accounts, ledger);
    }

    @Test void failedLedgerInsertRollsBackThenClosesBeforeAuditingAndDoesNotRetry() throws Exception {
        doThrow(new SQLException("SECRET", "23000", 1062)).when(ledger).insertWithdrawal(any(), any());
        var failure = assertThrows(WithdrawalException.class, () -> service.withdraw(7, "25"));
        assertFalse(failure.getMessage().contains("SECRET"));
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        var order = inOrder(connection, audits);
        order.verify(connection).rollback();
        order.verify(connection).close();
        order.verify(audits).append(argThat(e -> e.outcome() == AuditOutcome.ROLLED_BACK));
        verify(connection, never()).commit();
        verify(ledger).insertWithdrawal(any(), any());
        verify(references).get();
    }

    @Test void balanceWriteFailurePreventsLedgerInsertion() throws Exception {
        doThrow(new SQLException("SECRET")).when(accounts).updateBalance(any(), anyLong(), anyLong(), any(), any());
        assertThrows(WithdrawalException.class, () -> service.withdraw(7, "25"));
        verifyNoInteractions(ledger);
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test void readBackMismatchRollsBackInsteadOfPrintingSuccess() throws Exception {
        when(accounts.findOwned(connection, 1, 7)).thenReturn(Optional.of(account(1, AccountType.SAVINGS, AccountStatus.ACTIVE, "100")));
        assertThrows(WithdrawalException.class, () -> service.withdraw(7, "25"));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test void commitFailureBlocksBothWithdrawalAndDepositWithoutReplaying() throws Exception {
        doThrow(new SQLException("SECRET")).when(connection).commit();
        assertThrows(WithdrawalException.class, () -> service.withdraw(7, "25"));
        var pending = service.pending().orElseThrow();
        assertEquals(TransactionService.MoneyState.UNCONFIRMED, pending.moneyState());
        assertEquals(TransactionService.AuditState.SAVED, pending.auditState());
        assertEquals("COMMIT_UNCONFIRMED", pending.evidence().failureCode().orElseThrow());
        assertThrows(WithdrawalException.class, () -> service.withdraw(7, "25"));
        assertThrows(WithdrawalException.class, () -> service.deposit(7, "25"));
        assertSame(pending, service.pending().orElseThrow());
        verify(ledger).insertWithdrawal(any(), any());
        verify(ledger, never()).insertDeposit(any(), any());
    }

    @Test void failedRollbackRetainsUnconfirmedWithdrawalEvidence() throws Exception {
        doThrow(new SQLException("SECRET")).when(ledger).insertWithdrawal(any(), any());
        doThrow(new SQLException("PRIVATE")).when(connection).rollback();
        assertThrows(WithdrawalException.class, () -> service.withdraw(7, "25"));
        assertEquals("ROLLBACK_UNCONFIRMED", service.pending().orElseThrow().evidence().failureCode().orElseThrow());
    }

    @Test void failedCloseAfterCommitDoesNotClaimConfirmedSuccess() throws Exception {
        doThrow(new SQLException("SECRET")).when(connection).close();
        assertThrows(WithdrawalException.class, () -> service.withdraw(7, "25"));
        assertEquals("CLEANUP_UNCONFIRMED", service.pending().orElseThrow().evidence().failureCode().orElseThrow());
        assertEquals(TransactionService.MoneyState.UNCONFIRMED, service.pending().orElseThrow().moneyState());
    }

    @Test void independentAuditFailureRetainsExactCommittedEvidenceAndBlocksOppositeOperation() throws Exception {
        doThrow(new PersistenceException(null)).when(audits).append(any());
        var failure = assertThrows(WithdrawalException.class, () -> service.withdraw(7, "25"));
        assertTrue(failure.getMessage().contains("Withdrawal committed"));
        var pending = service.pending().orElseThrow();
        assertEquals(TransactionType.WITHDRAWAL, pending.evidence().eventType());
        assertEquals(AuditOutcome.SUCCESS, pending.evidence().outcome());
        assertEquals(TransactionService.MoneyState.COMMITTED, pending.moneyState());
        assertThrows(WithdrawalException.class, () -> service.deposit(7, "1"));
        assertSame(pending.evidence(), service.pending().orElseThrow().evidence());
        verify(audits).append(same(pending.evidence()));
        verify(ledger).insertWithdrawal(any(), any());
    }

    @Test void unsupportedAuditTimeIsPreservedWithoutAnyMoneySql() {
        Instant exact = TIME.plusNanos(1);
        service = service(Clock.fixed(exact, ZoneOffset.UTC), audits);
        doThrow(new IllegalArgumentException("unsupported")).when(audits).append(any());
        assertThrows(WithdrawalException.class, () -> service.withdraw(7, "25"));
        var pending = service.pending().orElseThrow();
        assertEquals(exact, pending.evidence().createdAt());
        assertEquals(TransactionService.AuditState.REJECTED_NO_WRITE, pending.auditState());
        verifyNoInteractions(connections, accounts, ledger);
    }

    @Test void connectionFailureCreatesFailedNotRolledBackEvidence() throws Exception {
        when(connections.getConnection()).thenThrow(new SQLException("SECRET"));
        assertThrows(WithdrawalException.class, () -> service.withdraw(7, "25"));
        verify(audits).append(argThat(e -> e.outcome() == AuditOutcome.FAILED
                && e.eventType() == TransactionType.WITHDRAWAL));
    }

    @Test void sessionCallbackFailureDoesNotAllocateAReferenceOrWriteAnAudit() {
        when(identity.get()).thenThrow(new SessionExpiredException());
        assertThrows(SessionExpiredException.class, () -> service.withdraw(7, "25"));
        verifyNoInteractions(references, connections, accounts, ledger, audits);
    }

    @Test void realFinancialWriterUsesASeparateConnectionAfterPrimaryCleanup() throws Exception {
        Connection independent = mock(Connection.class);
        PreparedStatement query = mock(PreparedStatement.class);
        when(connections.getConnection()).thenReturn(connection, independent);
        when(independent.prepareStatement(anyString())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);
        service = service(CLOCK, new JdbcTransactionAuditDAO(connections, 10));
        service.withdraw(7, "25");
        var order = inOrder(connections, connection, independent);
        order.verify(connections).getConnection();
        order.verify(connection).commit();
        order.verify(connection).close();
        order.verify(connections).getConnection();
        order.verify(independent).commit();
        order.verify(independent).close();
    }
}

