package com.example.banking.unit;

import com.example.banking.app.*;
import com.example.banking.exception.*;
import com.example.banking.model.*;
import com.example.banking.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.util.Optional;

import static com.example.banking.unit.DepositFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WithdrawalCliTest {
    private AuthService auth;
    private StringWriter output;

    @BeforeEach void setup() {
        auth = mock(AuthService.class);
        output = new StringWriter();
        when(auth.currentCustomer()).thenReturn(customer(1, CustomerStatus.ACTIVE));
        when(auth.withdraw(7, "25.00")).thenReturn(BankTransaction.withdrawal(REF,
                account(1, AccountType.SAVINGS, AccountStatus.ACTIVE, "100"), Money.parse("25"), TIME));
    }

    @Test void confirmedWithdrawalPrintsOnlyAMaskedReceiptAndReference() {
        runAccount("6\n7\n25.00\nWITHDRAW\n0\n");
        verify(auth).withdraw(7, "25.00");
        verify(auth, never()).deposit(anyLong(), anyString());
        assertTrue(output.toString().contains("Withdrawal complete."));
        assertTrue(output.toString().contains(REF.value()));
        assertTrue(output.toString().contains(NUMBER.masked()));
        assertTrue(output.toString().contains("Withdrawn: 25.00 | Balance before: 100.00 | Balance after: 75.00"));
        assertFalse(output.toString().contains(NUMBER.value()));
    }

    @ParameterizedTest @ValueSource(strings = {"", "/cancel", "NO", "DEPOSIT"})
    void unconfirmedOrWrongConfirmationNeverCallsMoneyService(String confirmation) {
        runAccount("6\n7\n25.00\n" + confirmation + "\n0\n");
        verifyNoMoneySubmission();
    }

    @ParameterizedTest @ValueSource(strings = {"6\n/cancel\n0\n", "6\n7\n/cancel\n0\n",
            "6\n", "6\n7\n", "6\n7\n25.00\n"})
    void cancelOrEndOfInputDuringAnyFieldNeverSubmits(String text) {
        runAccount(text);
        verifyNoMoneySubmission();
    }

    @Test void maskedNumberIsNotAcceptedAsAnAccountId() {
        runAccount("6\n****1234\n0\n");
        verifyNoMoneySubmission();
    }

    @Test void knownInsufficientFundsRejectionReturnsToAccountsWithoutSuccess() {
        when(auth.withdraw(7, "25.00")).thenThrow(new WithdrawalException("Insufficient funds. No money was changed.", REF));
        runAccount("6\n7\n25.00\nWITHDRAW\n0\n");
        assertTrue(output.toString().contains("Insufficient funds."));
        assertFalse(output.toString().contains("Withdrawal complete."));
    }

    @Test void sessionExpiryAfterTheFormCannotPrintSuccess() {
        when(auth.withdraw(7, "25.00")).thenThrow(new SessionExpiredException());
        assertThrows(SessionExpiredException.class, () -> runAccount("6\n7\n25.00\nWITHDRAW\n"));
        assertFalse(output.toString().contains("Withdrawal complete."));
    }

    @Test void pendingWithdrawalEscapesAccountsAndMainMenuBlocksLoginAndLogout() {
        var evidence = new TransactionAudit(null, 1, REF, TransactionType.WITHDRAWAL, AuditOutcome.SUCCESS, null, TIME);
        var pending = new TransactionService.PendingDeposit(evidence, TransactionService.MoneyState.COMMITTED,
                TransactionService.AuditState.SAVE_UNCERTAIN);
        when(auth.pendingDeposit()).thenReturn(Optional.of(pending));
        when(auth.withdraw(7, "25.00")).thenThrow(new WithdrawalException("Withdrawal committed; audit unconfirmed.", REF));
        assertThrows(WithdrawalException.class, () -> runAccount("6\n7\n25.00\nWITHDRAW\n"));
        try (var input = input("2\nEXIT\n")) { new MainMenu(auth, input).run(); }
        catch (IOException failure) { throw new AssertionError(failure); }
        assertTrue(output.toString().contains("WITHDRAWAL RECONCILIATION REQUIRED"));
        assertTrue(output.toString().contains(REF.value()));
        verify(auth, never()).login(anyString(), any());
        verify(auth, never()).logout();
    }

    private void verifyNoMoneySubmission() {
        verify(auth, never()).withdraw(anyLong(), anyString());
        verify(auth, never()).deposit(anyLong(), anyString());
        assertFalse(output.toString().contains("Withdrawal complete."));
    }

    private ConsoleInput input(String text) {
        return new ConsoleInput(new StringReader(text), new PrintWriter(output), () -> null);
    }

    private void runAccount(String text) {
        try (var input = input(text)) { new AccountMenu(auth, input).run(); }
        catch (IOException failure) { throw new AssertionError(failure); }
    }
}

