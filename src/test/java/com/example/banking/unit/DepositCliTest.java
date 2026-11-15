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

class DepositCliTest {
    private AuthService auth;
    private StringWriter output;

    @BeforeEach void setup() {
        auth = mock(AuthService.class);
        output = new StringWriter();
        when(auth.currentCustomer()).thenReturn(customer(1, CustomerStatus.ACTIVE));
        when(auth.deposit(7, "25.00")).thenReturn(receipt("25"));
    }

    @Test void depositRequiresConfirmationThenPrintsReferenceAndMaskedReceipt() {
        runAccount("5\n7\n25.00\nDEPOSIT\n0\n");
        verify(auth).deposit(7, "25.00");
        assertTrue(output.toString().contains("Deposit complete."));
        assertTrue(output.toString().contains(REF.value()));
        assertTrue(output.toString().contains(NUMBER.masked()));
        assertTrue(output.toString().contains("Balance before: 0.00 | Balance after: 25.00"));
        assertFalse(output.toString().contains(NUMBER.value()));
    }

    @ParameterizedTest @ValueSource(strings = {"", "/cancel", "NO"})
    void cancelledSubmissionNeverCallsMoneyService(String confirmation) {
        runAccount("5\n7\n25.00\n" + confirmation + "\n0\n");
        verify(auth, never()).deposit(anyLong(), anyString());
        assertFalse(output.toString().contains("Deposit complete."));
    }

    @Test void cancellationDuringAmountEntryNeverCallsMoneyService() {
        runAccount("5\n7\n/cancel\n0\n");
        verify(auth, never()).deposit(anyLong(), anyString());
    }

    @Test void invalidAccountIdIsAFormErrorNotAnUnownedReadOrDeposit() {
        runAccount("5\n****1234\n0\n");
        verify(auth, never()).deposit(anyLong(), anyString());
        verify(auth, never()).accountDetails(anyLong());
    }

    @Test void rejectedDepositReportsFailureAndAllowsAnotherMenuChoice() {
        when(auth.deposit(anyLong(), anyString())).thenThrow(new DepositException("Account not found.", REF));
        runAccount("5\n7\n25.00\nDEPOSIT\n0\n");
        assertTrue(output.toString().contains("Account not found."));
        assertFalse(output.toString().contains("Deposit complete."));
        verify(auth).deposit(7, "25.00");
    }

    @Test void expiryDuringFormDoesNotClaimDepositSucceeded() {
        when(auth.deposit(anyLong(), anyString())).thenThrow(new SessionExpiredException());
        assertThrows(SessionExpiredException.class, () -> runAccount("5\n7\n25.00\nDEPOSIT\n"));
        assertFalse(output.toString().contains("Deposit complete."));
    }

    @Test void pendingDepositEscapesTheAccountMenuAndCannotBeSilentlyDismissed() {
        var pending = pending();
        when(auth.deposit(anyLong(), anyString())).thenThrow(new DepositException("Audit unconfirmed.", REF));
        when(auth.pendingDeposit()).thenReturn(Optional.of(pending));
        assertThrows(DepositException.class, () -> runAccount("5\n7\n25.00\nDEPOSIT\n"));
        assertFalse(output.toString().contains("Deposit complete."));
    }

    @Test void mainMenuBlocksAuthenticationAndWarnsOfInMemoryEvidenceBeforeExit() throws IOException {
        when(auth.pendingDeposit()).thenReturn(Optional.of(pending()));
        try (var input = input("EXIT\n")) { new MainMenu(auth, input).run(); }
        assertTrue(output.toString().contains("DEPOSIT RECONCILIATION REQUIRED"));
        assertTrue(output.toString().contains("exiting loses it"));
        assertTrue(output.toString().contains(REF.value()));
        verify(auth, never()).login(anyString(), any());
        verify(auth, never()).logout();
    }

    private TransactionService.PendingDeposit pending() {
        return new TransactionService.PendingDeposit(new TransactionAudit(null, 1, REF,
                TransactionType.DEPOSIT, AuditOutcome.SUCCESS, null, TIME),
                TransactionService.MoneyState.COMMITTED, TransactionService.AuditState.SAVE_UNCERTAIN);
    }

    private ConsoleInput input(String text) {
        return new ConsoleInput(new StringReader(text), new PrintWriter(output), () -> null);
    }

    private void runAccount(String text) {
        try (var input = input(text)) { new AccountMenu(auth, input).run(); }
        catch (IOException failure) { throw new AssertionError(failure); }
    }
}

