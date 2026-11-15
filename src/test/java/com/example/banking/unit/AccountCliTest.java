package com.example.banking.unit;

import com.example.banking.app.*;
import com.example.banking.exception.*;
import com.example.banking.model.*;
import com.example.banking.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.util.List;

import static com.example.banking.unit.AccountFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountCliTest {
    private AuthService auth;
    private StringWriter output;

    @BeforeEach void setup() {
        auth = mock(AuthService.class);
        output = new StringWriter();
        when(auth.currentCustomer()).thenReturn(customer(1, CustomerStatus.ACTIVE));
        when(auth.openAccount(AccountType.SAVINGS)).thenReturn(
                account(AccountType.SAVINGS, 7, 1, NUMBER, AccountStatus.ACTIVE));
        when(auth.openAccount(AccountType.CURRENT)).thenReturn(
                account(AccountType.CURRENT, 8, 1, SECOND, AccountStatus.ACTIVE));
    }

    @Test void opensBothTypesOnlyAfterExplicitConfirmationAndMasksTheirNumbers() {
        run("1\nOPEN\n2\nOPEN\n0\n");
        verify(auth).openAccount(AccountType.SAVINGS);
        verify(auth).openAccount(AccountType.CURRENT);
        assertTrue(output.toString().contains("Account opened successfully."));
        assertTrue(output.toString().contains(NUMBER.masked()));
        assertTrue(output.toString().contains(SECOND.masked()));
        assertTrue(output.toString().contains("Balance: 0.00"));
        assertFalse(output.toString().contains(NUMBER.value()));
        assertFalse(output.toString().contains(SECOND.value()));
    }

    @ParameterizedTest @ValueSource(strings = {"", "/cancel", "NO"})
    void cancellationDoesNotOpenAnAccount(String confirmation) {
        run("1\n" + confirmation + "\n0\n");
        verify(auth, never()).openAccount(any());
        assertTrue(output.toString().contains("cancelled"));
        assertFalse(output.toString().contains("Account opened successfully."));
    }

    @Test void emptyListAndMissingDetailsHaveSafeMessages() {
        when(auth.accounts()).thenReturn(List.of());
        when(auth.accountDetails(99)).thenThrow(new ValidationException("Account not found."));
        run("3\n4\n99\n0\n");
        assertTrue(output.toString().contains("You do not have any accounts yet."));
        assertTrue(output.toString().contains("Account not found."));
    }

    @Test void listsFrozenAndClosedAccountsWithoutExposingFullNumbers() {
        when(auth.accounts()).thenReturn(List.of(
                account(AccountType.SAVINGS, 7, 1, NUMBER, AccountStatus.FROZEN),
                account(AccountType.LOAN, 8, 1, SECOND, AccountStatus.CLOSED)));
        run("3\n0\n");
        assertTrue(output.toString().contains("FROZEN"));
        assertTrue(output.toString().contains("CLOSED"));
        assertTrue(output.toString().contains("Outstanding principal (not spendable)"));
        assertFalse(output.toString().contains(NUMBER.value()));
    }

    @Test void detailsUsesNumericIdNotMaskedSuffix() {
        when(auth.accountDetails(7)).thenReturn(account(AccountType.SAVINGS, 7, 1, NUMBER, AccountStatus.ACTIVE));
        run("4\n7\n0\n");
        verify(auth).accountDetails(7);
        assertTrue(output.toString().contains(NUMBER.masked()));
        assertFalse(output.toString().contains(NUMBER.value()));
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "abc", "9999999999999999999", "****1234"})
    void invalidIdsNeverReachTheService(String supplied) {
        run("4\n" + supplied + "\n0\n");
        verify(auth, never()).accountDetails(anyLong());
        assertTrue(output.toString().contains("Choose a positive account ID"));
    }

    @Test void sessionExpiryAfterConfirmationDoesNotClaimAnAccountWasOpened() {
        when(auth.openAccount(AccountType.SAVINGS)).thenThrow(new SessionExpiredException());
        assertThrows(SessionExpiredException.class, () -> run("1\nOPEN\n"));
        assertFalse(output.toString().contains("Account opened successfully."));
    }

    @Test void uncertainOpeningShowsNoSuccessAndDoesNotAutomaticallyReplay() {
        when(auth.openAccount(any())).thenThrow(new AccountOpeningUnconfirmedException());
        run("1\nOPEN\n0\n");
        verify(auth).openAccount(AccountType.SAVINGS);
        assertTrue(output.toString().contains("Do not open another account"));
        assertFalse(output.toString().contains("Account opened successfully."));
    }

    @Test void customerMenuRoutesToAccountsAndRetainsLogoutAsOptionTwo() {
        try (var input = new ConsoleInput(new StringReader("5\n0\n2\n"), new PrintWriter(output), () -> null)) {
            new CustomerMenu(auth, input).run();
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
        assertTrue(output.toString().contains("MY ACCOUNTS"));
        assertTrue(output.toString().contains("Logged out."));
        verify(auth).logout();
    }

    private void run(String text) {
        try (var input = new ConsoleInput(new StringReader(text), new PrintWriter(output), () -> null)) {
            new AccountMenu(auth, input).run();
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }
}

