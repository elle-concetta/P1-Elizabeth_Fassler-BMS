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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordChangeCliTest {
    private AuthService auth;
    private StringWriter output;
    private final Customer owner = new Customer(7, "C-fixture", "Test", null, "Person",
            null, null, "test@example.invalid", "2025550123", "tester", CustomerStatus.ACTIVE);

    @BeforeEach void setup() {
        auth = mock(AuthService.class);
        output = new StringWriter();
        when(auth.currentCustomer()).thenReturn(owner);
        when(auth.currentProfile()).thenReturn(owner);
    }

    @Test void hiddenPromptsSuccessProfileAndExistingLogoutOptionWork() {
        var secrets = secrets();
        var queue = new ArrayDeque<>(secrets);
        run("4\n\n1\n2\n", queue::remove);
        verify(auth).changePassword(same(secrets.get(0)), same(secrets.get(1)), same(secrets.get(2)));
        verify(auth).logout();
        assertTrue(output.toString().contains("Password changed. Your session remains active."));
        assertTrue(output.toString().contains("Customer code: C-fixture"));
        for (var value : secrets) cleared(value);
        assertFalse(output.toString().contains("Previous-Pass-1!"));
        assertFalse(output.toString().contains("Replacement-Pass-2!"));
    }

    @Test void cancelAtTextGateDoesNotReadAnyPasswordsOrCallService() {
        run("4\n/cancel\n2\n", () -> { throw new AssertionError("Must not read passwords."); });
        verify(auth, never()).changePassword(any(), any(), any());
        assertTrue(output.toString().contains("Password change cancelled."));
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2})
    void eofDuringAnyHiddenPromptClearsAlreadyCollectedSecrets(int eofAt) {
        var secrets = secrets();
        var index = new AtomicInteger();
        run("4\n\n2\n", () -> {
            int next = index.getAndIncrement();
            return next == eofAt ? null : secrets.get(next);
        });
        verify(auth, never()).changePassword(any(), any(), any());
        for (int i = 0; i < eofAt; i++) cleared(secrets.get(i));
        assertTrue(output.toString().contains("Password change cancelled."));
    }

    @Test void validationFailureDoesNotClaimSuccessOrLoseCollectedArrays() {
        doThrow(new ValidationException("New password and confirmation do not match."))
                .when(auth).changePassword(any(), any(), any());
        var secrets = secrets();
        var queue = new ArrayDeque<>(secrets);
        run("4\n\n1\n2\n", queue::remove);
        assertFalse(output.toString().contains("Password changed."));
        assertTrue(output.toString().contains("do not match"));
        for (var value : secrets) cleared(value);
    }

    @Test void pendingUnconfirmedResultBlocksWelcomeActionsAndDisplaysOnlyTheReference() {
        var failure = new PasswordChangeUnconfirmedException(UUID.randomUUID());
        when(auth.pendingPasswordChange()).thenReturn(Optional.of(failure));
        var input = new ConsoleInput(new StringReader("1\nEXIT\n"), new PrintWriter(output), () -> null);
        new MainMenu(auth, input).run();
        assertTrue(output.toString().contains("Authentication is blocked pending manual reconciliation."));
        assertTrue(output.toString().contains("Reference:"));
        verify(auth, never()).login(anyString(), any());
        verify(auth, never()).register(any(), any());
    }

    private void run(String script, Supplier<char[]> passwords) {
        new CustomerMenu(auth, new ConsoleInput(new StringReader(script),
                new PrintWriter(output), passwords)).run();
    }
    private List<char[]> secrets() {
        return List.of("Previous-Pass-1!".toCharArray(), "Replacement-Pass-2!".toCharArray(),
                "Replacement-Pass-2!".toCharArray());
    }
    private void cleared(char[] value) { for (char c : value) assertEquals('\0', c); }
}

