package com.example.banking.unit;

import com.example.banking.dao.*;
import com.example.banking.exception.*;
import com.example.banking.model.*;
import com.example.banking.service.*;
import com.example.banking.util.ConnectionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.sql.*;
import java.time.*;
import java.util.Optional;
import static com.example.banking.unit.DepositFixtures.*;
import static com.example.banking.unit.TransferFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SpecialAccountServiceTest {
    private ConnectionFactory factory;
    private Connection connection;
    private AccountDAO accounts;
    private SpecialAccountDAO details;
    private TransactionDAO ledger;
    private TransactionAuditDAO audits;
    private TransactionService runner;
    private SpecialAccountService service;
    private final LocalDate today = LocalDate.now(CLOCK);

    @BeforeEach void setup() throws Exception {
        factory = mock(ConnectionFactory.class); connection = mock(Connection.class);
        accounts = mock(AccountDAO.class); details = mock(SpecialAccountDAO.class);
        ledger = mock(TransactionDAO.class); audits = mock(TransactionAuditDAO.class);
        var customers = mock(CustomerDAO.class);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(mock(Statement.class));
        when(customers.findProfile(connection, 1, true)).thenReturn(Optional.of(customer(1, CustomerStatus.ACTIVE)));
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.of(source("100")));
        runner = new TransactionService(factory, accounts, customers, ledger, audits,
                () -> customer(1, CustomerStatus.ACTIVE), () -> REF, CLOCK, LIMIT, 10);
        service = new SpecialAccountService(runner, accounts, details, ledger, factory,
                () -> customer(1, CustomerStatus.ACTIVE), () -> RECIPIENT, CLOCK, LIMIT, Money.parse("1"));
    }
    private Account special(AccountType type, AccountStatus status, String balance) {
        return endpoint(11, 1, type, status, balance);
    }
    private void openingDeposit() throws Exception {
        when(details.insert(connection, 1, RECIPIENT, AccountType.DEPOSIT, Money.ZERO, today)).thenReturn(11L);
        when(accounts.findOwned(connection, 1, 11)).thenReturn(
                Optional.of(special(AccountType.DEPOSIT, AccountStatus.ACTIVE, "0")),
                Optional.of(special(AccountType.DEPOSIT, AccountStatus.ACTIVE, "25")));
        when(accounts.findOwned(connection, 1, 7)).thenReturn(Optional.of(source("75")));
    }
    private void repayment(String amount, String remaining, AccountStatus status) throws Exception {
        when(accounts.lockOwned(connection, 1, 11)).thenReturn(Optional.of(special(AccountType.LOAN, AccountStatus.ACTIVE, "25")));
        when(details.lockLoan(connection, 11)).thenReturn(Money.parse("25"));
        when(accounts.findOwned(connection, 1, 11)).thenReturn(Optional.of(special(AccountType.LOAN, status, remaining)));
        when(accounts.findOwned(connection, 1, 7)).thenReturn(Optional.of(source(Money.parse("100").subtract(Money.parse(amount)).toString())));
    }
    private void settlement(AccountStatus initial, LocalDate maturity) throws Exception {
        when(accounts.lockOwned(connection, 1, 11)).thenReturn(Optional.of(special(AccountType.DEPOSIT, initial, "25")));
        when(details.lockDeposit(connection, 11)).thenReturn(new SpecialAccountDAO.DepositTerms(Money.parse("25"), maturity));
        when(accounts.findOwned(connection, 1, 11)).thenReturn(Optional.of(special(AccountType.DEPOSIT, AccountStatus.CLOSED, "0")));
        when(accounts.findOwned(connection, 1, 7)).thenReturn(Optional.of(source("125")));
    }

    @Test void openingFundsPrincipalAndDetailsAndLedgerOnOneConnectionThenAuditsAfterClose() throws Exception {
        openingDeposit();
        var result = service.openDeposit(7, "25", 1);
        assertEquals(Money.parse("25"), result.account().balance());
        assertEquals(Money.ZERO, result.movement().destinationBalanceBefore());
        assertEquals(Money.parse("25"), result.movement().destinationBalanceAfter());
        verify(details).depositDetails(connection, 11, Money.parse("25"), 1, today.plusMonths(1));
        verify(accounts).updateBalance(connection, 1, 7, Money.parse("100"), Money.parse("75"));
        var order = inOrder(connection, ledger, audits);
        order.verify(ledger).insertTransfer(eq(connection), any());
        order.verify(connection).commit();
        order.verify(connection).close();
        order.verify(audits).append(argThat(e -> e.outcome() == AuditOutcome.SUCCESS));
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1.001", "1e2", "", "1000000.01"})
    void invalidPrincipalCannotAcquireAPrimaryConnection(String amount) {
        assertThrows(FinancialOperationException.class, () -> service.openDeposit(7, amount, 1));
        verifyNoInteractions(factory, accounts, details, ledger);
    }
    @Test void belowMinimumAndInvalidTermRejectBeforeAnyPrimarySql() {
        assertThrows(FinancialOperationException.class, () -> service.openDeposit(7, "0.50", 1));
        assertThrows(FinancialOperationException.class, () -> service.openDeposit(7, "25", 0));
        assertThrows(FinancialOperationException.class, () -> service.openDeposit(7, "25", Integer.MAX_VALUE));
        verifyNoInteractions(factory, accounts, details, ledger);
    }
    @Test void insufficientFundingAndWrongOwnershipCannotCreatePrincipal() throws Exception {
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.of(source("10")));
        assertThrows(FinancialOperationException.class, () -> service.openDeposit(7, "25", 1));
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.of(endpoint(7, 2, AccountType.SAVINGS, AccountStatus.ACTIVE, "100")));
        assertThrows(FinancialOperationException.class, () -> service.openDeposit(7, "25", 1));
        verifyNoInteractions(details, ledger);
    }
    @Test void missingDetailsOrLedgerFailureRollsBackTheEntireOpening() throws Exception {
        openingDeposit();
        doThrow(new SQLException("SECRET")).when(details).depositDetails(any(), anyLong(), any(), anyInt(), any());
        assertThrows(FinancialOperationException.class, () -> service.openDeposit(7, "25", 1));
        verify(connection).rollback(); verify(connection, never()).commit();
        verifyNoInteractions(ledger);
    }

    @ParameterizedTest @EnumSource(value = AccountStatus.class, names = {"ACTIVE", "MATURED"})
    void settlementAllowsTheExactMaturityDateAndClosesPrincipalOnce(AccountStatus status) throws Exception {
        settlement(status, today);
        var result = service.settleDeposit(11, 7);
        assertEquals(AccountStatus.CLOSED, result.account().status());
        assertEquals(Money.ZERO, result.account().balance());
        assertEquals(Money.parse("125"), result.movement().destinationBalanceAfter());
        var order = inOrder(accounts, details);
        order.verify(accounts).lockOwned(connection, 1, 7);
        order.verify(accounts).lockOwned(connection, 1, 11);
        order.verify(details).lockDeposit(connection, 11);
        verify(ledger).insertTransfer(eq(connection), any());
    }
    @Test void earlySettlementCannotReleasePrincipal() throws Exception {
        settlement(AccountStatus.ACTIVE, today.plusDays(1));
        assertThrows(FinancialOperationException.class, () -> service.settleDeposit(11, 7));
        verifyNoInteractions(ledger);
        verify(details, never()).update(any(), any(), any(), any());
    }
    @Test void repeatSettlementOfClosedDepositIsRejectedWithoutAnotherPayout() throws Exception {
        settlement(AccountStatus.MATURED, today);
        service.settleDeposit(11, 7);
        when(accounts.lockOwned(connection, 1, 11)).thenReturn(Optional.of(special(AccountType.DEPOSIT, AccountStatus.CLOSED, "0")));
        assertThrows(FinancialOperationException.class, () -> service.settleDeposit(11, 7));
        verify(ledger).insertTransfer(any(), any());
        verify(accounts).updateBalance(connection, 1, 7, Money.parse("100"), Money.parse("125"));
    }
    @Test void settlementLedgerFailureRollsBackBothSides() throws Exception {
        settlement(AccountStatus.MATURED, today);
        doThrow(new SQLException("SECRET")).when(ledger).insertTransfer(any(), any());
        assertThrows(FinancialOperationException.class, () -> service.settleDeposit(11, 7));
        verify(connection).rollback(); verify(connection, never()).commit();
    }
    @Test void loanCreationCreatesDebtAndDetailsButNoSpendableCreditOrFictionalLedger() throws Exception {
        when(details.insert(connection, 1, RECIPIENT, AccountType.LOAN, Money.parse("25"), today)).thenReturn(11L);
        when(accounts.findOwned(connection, 1, 11)).thenReturn(Optional.of(special(AccountType.LOAN, AccountStatus.ACTIVE, "25")));
        var result = service.openLoan("25", 12, null);
        assertNull(result.movement());
        assertEquals(AccountType.LOAN, result.account().type());
        verify(details).loanDetails(connection, 11, Money.parse("25"), 12, today);
        verifyNoInteractions(ledger);
        verify(accounts, never()).updateBalance(any(), anyLong(), anyLong(), any(), any());
        verify(audits).append(argThat(e -> e.event() == FinancialAuditEvent.LOAN_CREATION && e.outcome() == AuditOutcome.SUCCESS));
    }
    @ParameterizedTest @CsvSource({"10,15,ACTIVE", "25,0,CLOSED"})
    void partialAndExactLoanRepaymentsReduceDebtAndFundingTogether(String amount, String remaining, AccountStatus status) throws Exception {
        repayment(amount, remaining, status);
        var result = service.repayLoan(7, 11, amount);
        assertEquals(status, result.account().status());
        assertEquals(Money.parse(remaining), result.account().balance());
        assertEquals(TransactionType.LOAN_REPAYMENT, result.movement().type());
        verify(ledger).insertRepayment(eq(connection), any());
        verify(connection).commit();
    }
    @Test void overpaymentAndInsufficientFundsNeverWriteEitherBalance() throws Exception {
        repayment("10", "15", AccountStatus.ACTIVE);
        assertThrows(FinancialOperationException.class, () -> service.repayLoan(7, 11, "25.01"));
        when(accounts.lockOwned(connection, 1, 7)).thenReturn(Optional.of(source("1")));
        assertThrows(FinancialOperationException.class, () -> service.repayLoan(7, 11, "10"));
        verifyNoInteractions(ledger);
        verify(accounts, never()).updateBalance(any(), anyLong(), anyLong(), any(), any());
    }
    @ParameterizedTest @EnumSource(value = AccountStatus.class, names = {"CLOSED", "FROZEN", "MATURED"})
    void closedOrUnavailableLoanCannotBePaidAgain(AccountStatus status) throws Exception {
        repayment("10", "15", AccountStatus.ACTIVE);
        when(accounts.lockOwned(connection, 1, 11)).thenReturn(Optional.of(special(AccountType.LOAN, status, "25")));
        assertThrows(FinancialOperationException.class, () -> service.repayLoan(7, 11, "10"));
        verifyNoInteractions(ledger);
    }
    @Test void repaymentLedgerFailureRollsBackAndDoesNotExposeDriverGraph() throws Exception {
        repayment("10", "15", AccountStatus.ACTIVE);
        doThrow(new SQLException("SECRET")).when(ledger).insertRepayment(any(), any());
        var failure = assertThrows(FinancialOperationException.class, () -> service.repayLoan(7, 11, "10"));
        assertFalse(failure.getMessage().contains("SECRET"));
        assertNull(failure.getCause()); verify(connection).rollback();
    }
    @Test void dedicatedCommitUncertaintyRetainsExactEvidenceAndNeverReplays() throws Exception {
        openingDeposit();
        doThrow(new SQLException("SECRET")).when(connection).commit();
        assertThrows(FinancialOperationException.class, () -> service.openDeposit(7, "25", 1));
        var exact = runner.pending().orElseThrow();
        assertEquals(TransactionService.MoneyState.UNCONFIRMED, exact.moneyState());
        assertThrows(FinancialOperationException.class, () -> service.openLoan("1", 1, null));
        assertSame(exact, runner.pending().orElseThrow());
        verify(details).insert(any(), anyLong(), any(), any(), any(), any());
    }
    @Test void successfulMoneyWithAuditFailureIsBlockedWithoutRepeatedSettlement() throws Exception {
        settlement(AccountStatus.MATURED, today);
        doThrow(new PersistenceException(null)).when(audits).append(any());
        assertThrows(FinancialOperationException.class, () -> service.settleDeposit(11, 7));
        assertEquals(TransactionService.MoneyState.COMMITTED, runner.pending().orElseThrow().moneyState());
        assertThrows(FinancialOperationException.class, () -> service.settleDeposit(11, 7));
        verify(ledger).insertTransfer(any(), any());
    }
    @Test void maturityRefreshIsOwnedDerivedStateOnly() throws Exception {
        service.refreshMaturity();
        verify(details).matureOwned(connection, 1, today);
        verifyNoInteractions(ledger, audits, accounts);
        verify(connection).commit(); verify(connection).close();
    }

    @Test void nonCashLoanEventIsImmutableEvidenceAndCannotPretendToBeACashType() {
        var event = new TransactionAudit(FinancialAuditEvent.LOAN_CREATION, null, 1, REF,
                AuditOutcome.SUCCESS, null, TIME);
        assertEquals(FinancialAuditEvent.LOAN_CREATION, event.event());
        assertThrows(IllegalStateException.class, event::eventType);
        assertEquals(TIME, event.createdAt());
    }
}

