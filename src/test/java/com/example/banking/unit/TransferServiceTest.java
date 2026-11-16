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
import java.util.OptionalLong;
import java.util.function.Supplier;

import static com.example.banking.unit.DepositFixtures.*;
import static com.example.banking.unit.TransferFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TransferServiceTest {
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
        connections = mock(ConnectionFactory.class); connection = mock(Connection.class);
        accounts = mock(AccountDAO.class); customers = mock(CustomerDAO.class);
        ledger = mock(TransactionDAO.class); audits = mock(TransactionAuditDAO.class);
        identity = mock(Supplier.class); references = mock(Supplier.class);
        when(identity.get()).thenReturn(customer(1, CustomerStatus.ACTIVE));
        when(references.get()).thenReturn(REF);
        when(connections.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(mock(Statement.class));
        when(customers.findProfile(connection, 1, true)).thenReturn(Optional.of(customer(1, CustomerStatus.ACTIVE)));
        when(accounts.findOwned(connection, 1, 7)).thenReturn(Optional.of(source("100")), Optional.of(source("75")));
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.of(source("100")));
        destination(11, 2, "20", "45");
        service = service(CLOCK, audits);
    }

    private void destination(long id, long owner, String before, String after) throws SQLException {
        when(accounts.findDestinationId(connection, RECIPIENT)).thenReturn(OptionalLong.of(id));
        when(accounts.lockDestination(connection, id)).thenReturn(Optional.of(recipient(id, owner, before)));
        when(accounts.findOwned(connection, owner, id)).thenReturn(Optional.of(recipient(id, owner, after)));
    }

    private TransactionService service(Clock clock, TransactionAuditDAO writer) {
        return new TransactionService(connections, accounts, customers, ledger, writer,
                identity, references, clock, LIMIT, 10);
    }

    private TransferReceipt submit() { return service.transfer(7, RECIPIENT.value(), "25.00"); }

    @ParameterizedTest @ValueSource(longs = {3, 11})
    void locksByAscendingIdAndCommitsBothLegsBeforeIndependentAudit(long destinationId) throws Exception {
        destination(destinationId, 2, "20", "45");
        var receipt = submit();
        assertEquals(Money.parse("75"), receipt.sourceBalanceAfter());
        var order = inOrder(connection, customers, accounts, ledger, audits);
        order.verify(connection).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        order.verify(connection).setAutoCommit(false);
        order.verify(customers).findProfile(connection, 1, true);
        order.verify(accounts).findOwned(connection, 1, 7);
        order.verify(accounts).findDestinationId(connection, RECIPIENT);
        if (destinationId < 7) {
            order.verify(accounts).lockDestination(connection, destinationId);
            order.verify(accounts).lockOwned(connection, 1, 7);
        } else {
            order.verify(accounts).lockOwned(connection, 1, 7);
            order.verify(accounts).lockDestination(connection, destinationId);
        }
        order.verify(accounts).updateBalance(connection, 1, 7, Money.parse("100"), Money.parse("75"));
        order.verify(accounts).updateBalance(connection, 2, destinationId, Money.parse("20"), Money.parse("45"));
        order.verify(ledger).insertTransfer(eq(connection), argThat(tx -> tx.type() == TransactionType.TRANSFER));
        order.verify(accounts).findOwned(connection, 1, 7);
        order.verify(accounts).findOwned(connection, 2, destinationId);
        order.verify(connection).commit();
        order.verify(connection).close();
        order.verify(audits).append(argThat(e -> e.eventType() == TransactionType.TRANSFER && e.outcome() == AuditOutcome.SUCCESS));
        verify(connection, never()).rollback();
        verify(ledger, never()).insertDeposit(any(), any());
        verify(ledger, never()).insertWithdrawal(any(), any());
    }

    @Test void destinationMayAlsoBelongToTheSender() throws Exception {
        destination(11, 1, "20", "45");
        assertNotNull(submit());
        verify(accounts).updateBalance(connection, 1, 11, Money.parse("20"), Money.parse("45"));
    }

    @ParameterizedTest @ValueSource(strings = {"", "0", "-1", "1.001", "1e2", "1000000.01"})
    void invalidAmountsAuditWithoutOpeningAMoneyConnection(String amount) {
        assertThrows(TransferException.class, () -> service.transfer(7, RECIPIENT.value(), amount));
        verifyNoInteractions(connections, accounts, ledger);
        verify(audits).append(argThat(e -> e.failureCode().orElseThrow().equals("INVALID_AMOUNT")));
    }

    @Test void malformedRecipientAndInvalidSourceIdCannotProbeAnyAccount() {
        for (String supplied : new String[]{null, "", "****1234", "0".repeat(24), "9".repeat(10000)}) {
            assertThrows(TransferException.class, () -> service.transfer(7, supplied, "25"));
        }
        assertThrows(TransferException.class, () -> service.transfer(0, RECIPIENT.value(), "25"));
        verifyNoInteractions(connections, accounts, ledger);
    }

    @Test void unauthorizedSourceIsRejectedBeforeResolvingTheRecipient() throws Exception {
        when(accounts.findOwned(connection, 1, 7)).thenReturn(Optional.empty());
        assertThrows(TransferException.class, this::submit);
        verify(accounts, never()).findDestinationId(any(), any());
        verify(accounts, never()).lockDestination(any(), anyLong());
        verifyNoInteractions(ledger);
    }

    @Test void missingRecipientAndChangedRoutingAreRejectedWithoutMutation() throws Exception {
        when(accounts.findDestinationId(connection, RECIPIENT)).thenReturn(OptionalLong.empty());
        assertThrows(TransferException.class, this::submit);
        verifyNoInteractions(ledger);
        verify(accounts, never()).updateBalance(any(), anyLong(), anyLong(), any(), any());
        when(accounts.findDestinationId(connection, RECIPIENT)).thenReturn(OptionalLong.of(11));
        when(accounts.lockDestination(connection, 11)).thenReturn(Optional.of(source("20")));
        assertThrows(TransferException.class, this::submit);
        verifyNoInteractions(ledger);
    }

    @Test void sourceOwnershipIsRecheckedUnderTheLock() throws Exception {
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.of(endpoint(7, 2, AccountType.SAVINGS, AccountStatus.ACTIVE, "100")));
        assertThrows(TransferException.class, this::submit);
        verifyNoInteractions(ledger);
        verify(accounts, never()).updateBalance(any(), anyLong(), anyLong(), any(), any());
    }

    @Test void selfTransferRejectsBeforeTakingAccountLocks() throws Exception {
        when(accounts.findDestinationId(connection, RECIPIENT)).thenReturn(OptionalLong.of(7));
        assertThrows(TransferException.class, this::submit);
        verify(accounts, never()).lockOwned(any(), anyLong(), anyLong());
        verify(accounts, never()).lockDestination(any(), anyLong());
        verify(audits).append(argThat(e -> e.failureCode().orElseThrow().equals("SAME_ACCOUNT")));
    }

    @ParameterizedTest @EnumSource(value = AccountStatus.class, names = {"FROZEN", "MATURED", "CLOSED"})
    void inactiveRecipientCannotReceiveAMoneyTransfer(AccountStatus status) throws Exception {
        when(accounts.lockDestination(connection, 11)).thenReturn(Optional.of(endpoint(11, 2, AccountType.CURRENT, status, "20")));
        assertThrows(TransferException.class, this::submit);
        verifyNoInteractions(ledger);
        verify(audits).append(argThat(e -> e.failureCode().orElseThrow().equals("ACCOUNT_UNAVAILABLE")));
    }

    @ParameterizedTest @EnumSource(value = AccountStatus.class, names = {"FROZEN", "MATURED", "CLOSED"})
    void inactiveSourceCannotSend(AccountStatus status) throws Exception {
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.of(endpoint(7, 1, AccountType.SAVINGS, status, "100")));
        assertThrows(TransferException.class, this::submit);
        verifyNoInteractions(ledger);
    }

    @ParameterizedTest @EnumSource(value = AccountType.class, names = {"DEPOSIT", "LOAN"})
    void dedicatedPrincipalOnEitherSideRejectsTheTransfer(AccountType type) throws Exception {
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.of(endpoint(7, 1, type, AccountStatus.ACTIVE, "100")));
        assertThrows(TransferException.class, this::submit);
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.of(source("100")));
        when(accounts.lockDestination(connection, 11)).thenReturn(Optional.of(endpoint(11, 2, type, AccountStatus.ACTIVE, "20")));
        assertThrows(TransferException.class, this::submit);
        verifyNoInteractions(ledger);
    }

    @ParameterizedTest @EnumSource(value = CustomerStatus.class, names = {"LOCKED", "CLOSED"})
    void changedSenderStatusStopsBeforeAccountReads(CustomerStatus status) throws Exception {
        when(customers.findProfile(connection, 1, true)).thenReturn(Optional.of(customer(1, status)));
        assertThrows(TransferException.class, this::submit);
        verifyNoInteractions(accounts, ledger);
    }

    @Test void sufficientFundsComeFromTheLockedRowNotTheEarlierOwnedRead() throws Exception {
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.of(source("24.99")));
        assertThrows(TransferException.class, this::submit);
        verify(audits).append(argThat(e -> e.failureCode().orElseThrow().equals("INSUFFICIENT_FUNDS")));
        verify(accounts, never()).updateBalance(any(), anyLong(), anyLong(), any(), any());
        verifyNoInteractions(ledger);
    }

    @Test void storedMinimumAndRecipientOverflowRejectBeforeEitherWrite() throws Exception {
        var minimum = new SavingsAccount(7, 1, NUMBER, Money.parse("100"), BigDecimal.ZERO,
                Money.parse("80"), LocalDate.of(2026, 11, 15), AccountStatus.ACTIVE);
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.of(minimum));
        assertThrows(TransferException.class, this::submit);
        verify(audits).append(argThat(e -> e.failureCode().orElseThrow().equals("MINIMUM_BALANCE")));
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.of(source("100")));
        when(accounts.lockDestination(connection, 11)).thenReturn(Optional.of(recipient(11, 2, "99999999999999999.99")));
        assertThrows(TransferException.class, this::submit);
        verify(audits).append(argThat(e -> e.failureCode().orElseThrow().equals("BALANCE_OVERFLOW")));
        verify(accounts, never()).updateBalance(any(), anyLong(), anyLong(), any(), any());
    }

    @Test void failedCreditRollsBackTheDebitAndCannotInsertALedgerRow() throws Exception {
        doThrow(new SQLException("SECRET")).when(accounts).updateBalance(connection, 2, 11, Money.parse("20"), Money.parse("45"));
        assertThrows(TransferException.class, this::submit);
        verify(accounts).updateBalance(connection, 1, 7, Money.parse("100"), Money.parse("75"));
        verifyNoInteractions(ledger);
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test void ledgerFailureRollsBackBothLegsClosesThenAuditsWithoutRetrying() throws Exception {
        doThrow(new SQLException("SECRET", "23000", 1062)).when(ledger).insertTransfer(any(), any());
        var failure = assertThrows(TransferException.class, this::submit);
        assertFalse(failure.getMessage().contains("SECRET"));
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        var order = inOrder(connection, audits);
        order.verify(connection).rollback();
        order.verify(connection).close();
        order.verify(audits).append(argThat(e -> e.outcome() == AuditOutcome.ROLLED_BACK));
        verify(connection, never()).commit();
        verify(ledger).insertTransfer(any(), any());
        verify(references).get();
    }

    @Test void recipientReadBackMismatchRollsBackTheCompleteOperation() throws Exception {
        when(accounts.findOwned(connection, 2, 11)).thenReturn(Optional.of(recipient(11, 2, "20")));
        assertThrows(TransferException.class, this::submit);
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test void commitFailureIsUnconfirmedEvenWhenRollbackReturnsAndBlocksAllMoneyTypes() throws Exception {
        doThrow(new SQLException("SECRET")).when(connection).commit();
        assertThrows(TransferException.class, this::submit);
        var pending = service.pending().orElseThrow();
        assertEquals(TransactionService.MoneyState.UNCONFIRMED, pending.moneyState());
        assertEquals("COMMIT_UNCONFIRMED", pending.evidence().failureCode().orElseThrow());
        assertThrows(TransferException.class, () -> service.deposit(7, "1"));
        assertThrows(TransferException.class, () -> service.withdraw(7, "1"));
        assertThrows(TransferException.class, this::submit);
        assertSame(pending, service.pending().orElseThrow());
        verify(ledger).insertTransfer(any(), any());
    }

    @Test void failedRollbackRetainsTheExactUnconfirmedTransfer() throws Exception {
        doThrow(new SQLException("SECRET")).when(ledger).insertTransfer(any(), any());
        doThrow(new SQLException("PRIVATE")).when(connection).rollback();
        assertThrows(TransferException.class, this::submit);
        assertEquals("ROLLBACK_UNCONFIRMED", service.pending().orElseThrow().evidence().failureCode().orElseThrow());
    }

    @Test void closeFailureAfterCommitCannotBePresentedAsSuccess() throws Exception {
        doThrow(new SQLException("SECRET")).when(connection).close();
        assertThrows(TransferException.class, this::submit);
        assertEquals("CLEANUP_UNCONFIRMED", service.pending().orElseThrow().evidence().failureCode().orElseThrow());
        assertEquals(TransactionService.MoneyState.UNCONFIRMED, service.pending().orElseThrow().moneyState());
    }

    @Test void postCommitAuditFailurePreservesSuccessEvidenceAndNeverReplaysEitherLeg() throws Exception {
        doThrow(new PersistenceException(null)).when(audits).append(any());
        var failure = assertThrows(TransferException.class, this::submit);
        assertTrue(failure.getMessage().contains("Transfer committed"));
        var pending = service.pending().orElseThrow();
        assertEquals(TransactionType.TRANSFER, pending.evidence().eventType());
        assertEquals(AuditOutcome.SUCCESS, pending.evidence().outcome());
        assertEquals(TransactionService.MoneyState.COMMITTED, pending.moneyState());
        assertThrows(TransferException.class, this::submit);
        verify(audits).append(same(pending.evidence()));
        verify(ledger).insertTransfer(any(), any());
    }

    @Test void fractionalTimeRetainsExactEvidenceWithoutMoneySql() {
        var exact = TIME.plusNanos(1);
        service = service(Clock.fixed(exact, ZoneOffset.UTC), audits);
        doThrow(new IllegalArgumentException("unsupported")).when(audits).append(any());
        assertThrows(TransferException.class, this::submit);
        assertEquals(exact, service.pending().orElseThrow().evidence().createdAt());
        assertEquals(TransactionService.AuditState.REJECTED_NO_WRITE, service.pending().orElseThrow().auditState());
        verifyNoInteractions(connections, accounts, ledger);
    }

    @Test void sessionFailureDoesNotAllocateReferencesOrProbeAccounts() {
        when(identity.get()).thenThrow(new SessionExpiredException());
        assertThrows(SessionExpiredException.class, this::submit);
        verifyNoInteractions(references, connections, accounts, ledger, audits);
    }

    @Test void realAuditWriterGetsItsConnectionOnlyAfterThePrimaryIsClosed() throws Exception {
        var independent = mock(Connection.class);
        var query = mock(PreparedStatement.class);
        when(connections.getConnection()).thenReturn(connection, independent);
        when(independent.prepareStatement(anyString())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);
        service = service(CLOCK, new JdbcTransactionAuditDAO(connections, 10));
        submit();
        var order = inOrder(connections, connection, independent);
        order.verify(connections).getConnection();
        order.verify(connection).commit();
        order.verify(connection).close();
        order.verify(connections).getConnection();
        order.verify(independent).commit();
        order.verify(independent).close();
    }
}

