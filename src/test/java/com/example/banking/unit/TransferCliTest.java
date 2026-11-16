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
import static com.example.banking.unit.TransferFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TransferCliTest {
    private AuthService auth;
    private StringWriter output;

    @BeforeEach void setup() {
        auth = mock(AuthService.class);
        output = new StringWriter();
        when(auth.currentCustomer()).thenReturn(customer(1, CustomerStatus.ACTIVE));
        when(auth.transfer(7, RECIPIENT.value(), "25.00")).thenReturn(TransferReceipt.from(candidate()));
    }

    @Test void hiddenRecipientInputIsWipedAndReceiptHasOnlySourceBalancesAndMaskedNumbers() {
        char[] raw = RECIPIENT.value().toCharArray();
        run("7\n7\n25.00\nTRANSFER\n0\n", raw);
        verify(auth).transfer(7, RECIPIENT.value(), "25.00");
        assertTrue(output.toString().contains("Transfer complete."));
        assertTrue(output.toString().contains(REF.value()));
        assertTrue(output.toString().contains("Recipient: " + RECIPIENT.masked()));
        assertTrue(output.toString().contains("Source balance before: 100.00 | Source balance after: 75.00"));
        assertFalse(output.toString().contains(NUMBER.value()));
        assertFalse(output.toString().contains(RECIPIENT.value()));
        assertFalse(output.toString().contains("45.00"));
        assertFalse(output.toString().contains("20.00"));
        assertArrayEquals(new char[raw.length], raw);
    }

    @ParameterizedTest @ValueSource(strings = {"", "NO", "WITHDRAW", "/cancel"})
    void anyOtherConfirmationSubmitsNothing(String confirmation) {
        char[] raw = RECIPIENT.value().toCharArray();
        run("7\n7\n25.00\n" + confirmation + "\n0\n", raw);
        verifyNoSubmission();
        assertArrayEquals(new char[raw.length], raw);
    }

    @ParameterizedTest @ValueSource(strings = {"7\n/cancel\n0\n", "7\n7\n/cancel\n0\n",
            "7\n7\n", "7\n7\n25.00\n"})
    void cancellationOrEofDoesNotSubmit(String script) {
        run(script, RECIPIENT.value().toCharArray());
        verifyNoSubmission();
    }

    @Test void recipientCancelAndOversizedInputAreWipedWithoutSubmission() {
        char[] cancelled = "/cancel".toCharArray();
        run("7\n7\n0\n", cancelled);
        assertArrayEquals(new char[cancelled.length], cancelled);
        char[] tooLong = "1".repeat(1000).toCharArray();
        run("7\n7\n0\n", tooLong);
        assertArrayEquals(new char[tooLong.length], tooLong);
        verifyNoSubmission();
    }

    @Test void rejectedTransferDoesNotPrintSuccess() {
        when(auth.transfer(anyLong(), anyString(), anyString())).thenThrow(new TransferException("Insufficient funds.", REF));
        run("7\n7\n25.00\nTRANSFER\n0\n", RECIPIENT.value().toCharArray());
        assertTrue(output.toString().contains("Insufficient funds."));
        assertFalse(output.toString().contains("Transfer complete."));
    }

    @Test void expiryAfterFormInputCannotClaimSuccess() {
        when(auth.transfer(anyLong(), anyString(), anyString())).thenThrow(new SessionExpiredException());
        assertThrows(SessionExpiredException.class, () -> run("7\n7\n25.00\nTRANSFER\n", RECIPIENT.value().toCharArray()));
        assertFalse(output.toString().contains("Transfer complete."));
    }

    @Test void uncertainTransferEscapesAccountsAndBlocksMainMenuActions() {
        var evidence = new TransactionAudit(null, 1, REF, TransactionType.TRANSFER, AuditOutcome.SUCCESS, null, TIME);
        var pending = new TransactionService.PendingDeposit(evidence, TransactionService.MoneyState.COMMITTED,
                TransactionService.AuditState.SAVE_UNCERTAIN);
        when(auth.pendingDeposit()).thenReturn(Optional.of(pending));
        when(auth.transfer(anyLong(), anyString(), anyString())).thenThrow(new TransferException("Transfer committed; audit uncertain.", REF));
        assertThrows(TransferException.class, () -> run("7\n7\n25.00\nTRANSFER\n", RECIPIENT.value().toCharArray()));
        try (var input = new ConsoleInput(new StringReader("2\nEXIT\n"), new PrintWriter(output), () -> null)) {
            new MainMenu(auth, input).run();
        } catch (IOException failure) { throw new AssertionError(failure); }
        assertTrue(output.toString().contains("TRANSFER RECONCILIATION REQUIRED"));
        verify(auth, never()).login(anyString(), any());
        verify(auth, never()).logout();
    }

    private void verifyNoSubmission() {
        verify(auth, never()).transfer(anyLong(), anyString(), anyString());
        verify(auth, never()).deposit(anyLong(), anyString());
        verify(auth, never()).withdraw(anyLong(), anyString());
        assertFalse(output.toString().contains("Transfer complete."));
    }

    private void run(String script, char[] recipient) {
        try (var input = new ConsoleInput(new StringReader(script), new PrintWriter(output), () -> recipient)) {
            new AccountMenu(auth, input).run();
        } catch (IOException failure) { throw new AssertionError(failure); }
    }
}

