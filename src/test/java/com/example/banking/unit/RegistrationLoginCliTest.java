package com.example.banking.unit;

import com.example.banking.app.ConsoleInput;
import com.example.banking.app.MainMenu;
import com.example.banking.exception.SessionExpiredException;
import com.example.banking.exception.ValidationException;
import com.example.banking.model.*;
import com.example.banking.security.LoginAuditDelivery;
import com.example.banking.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real terminal routing with injected streams; no database, console, or credentials needed. */
class RegistrationLoginCliTest {
    @Test
    void registerLoginViewProfileAndLogoutRouteThroughOneCliSession() {
        AuthService auth = mock(AuthService.class);
        Customer profile = new Customer(7, "C-fixture", "Test", null, "Person", null,
                null, "test@example.invalid", null, "tester", CustomerStatus.ACTIVE);
        when(auth.currentCustomer()).thenReturn(profile);
        when(auth.currentProfile()).thenReturn(profile);
        when(auth.login(eq("tester"), any())).thenReturn(profile);
        var passwords = new ArrayDeque<>(List.of(
                "Valid-Password-123!".toCharArray(), "Valid-Password-123!".toCharArray()));
        String script = "1\nTest\n\nPerson\n2000-01-01\n\n"
                + "test@example.invalid\n2025550123\ntester\n2\ntester\n1\n2\n3\n";
        StringWriter output = new StringWriter();
        var input = new ConsoleInput(new StringReader(script), new PrintWriter(output), passwords::remove);
        new MainMenu(auth, input).run();
        verify(auth).register(argThat(row -> row.username().equals("tester")
                && row.phone().equals("2025550123")
                && row.dateOfBirth().toString().equals("2000-01-01")), any());
        verify(auth).login(eq("tester"), any());
        verify(auth).logout();
        String rendered = output.toString();
        assertTrue(rendered.contains("Registration complete."));
        assertTrue(rendered.contains("Login successful."));
        assertTrue(rendered.contains("Customer code: C-fixture"));
        assertTrue(rendered.contains("Logged out."));
        assertFalse(rendered.contains("Valid-Password"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "123456789", "12345678901", "202-555-0123",
            "202555012A", " 2025550123", "２０２５５５０１２３"})
    void badPhoneReturnsToWelcomeBeforeRequestingPasswordOrCallingRegistration(String phone) {
        AuthService auth = mock(AuthService.class);
        StringWriter output = new StringWriter();
        String script = "1\nTest\n\nPerson\n\n\ntest@example.invalid\n" + phone + "\n3\n";
        var input = new ConsoleInput(new StringReader(script), new PrintWriter(output),
                () -> { throw new AssertionError("Invalid phone must be rejected before password entry."); });
        new MainMenu(auth, input).run();
        verify(auth, never()).register(any(), any());
        assertTrue(output.toString().contains("exactly 10 digits"));
    }

    @Test
    void welcomeBannerAppearsOnceAndInvalidChoiceKeepsNavigationWorking() {
        AuthService auth = mock(AuthService.class);
        StringWriter output = new StringWriter();
        var input = new ConsoleInput(new StringReader("invalid\n3\n"), new PrintWriter(output),
                () -> new char[0]);
        new MainMenu(auth, input).run();
        String rendered = output.toString();
        assertEquals(1, rendered.split("S E C U R E   B A N K I N G", -1).length - 1);
        assertTrue(rendered.contains("GET STARTED"));
        assertTrue(rendered.contains("Choose 1, 2 or 3."));
        verify(auth, never()).login(anyString(), any());
        verify(auth, never()).register(any(), any());
    }

    @Test
    void boundedInputDrainsOversizedLineAndSupportsWindowsLineEndings() {
        StringWriter output = new StringWriter();
        var input = new ConsoleInput(new StringReader("x".repeat(10000) + "\r\nnext\r\n"),
                new PrintWriter(output), () -> new char[0]);
        assertThrows(ValidationException.class, () -> input.text("Input: ", 10));
        assertEquals("next", input.text("Input: ", 10));
        assertNull(input.text("Input: ", 10));
    }

    @Test
    void cancellationAndInvalidBirthDateReturnToWelcomeWithoutCallingRegistration() {
        AuthService auth = mock(AuthService.class);
        StringWriter output = new StringWriter();
        var input = new ConsoleInput(new StringReader("1\n/cancel\n1\nTest\n\nPerson\n2026-02-30\n3\n"),
                new PrintWriter(output), () -> new char[0]);
        new MainMenu(auth, input).run();
        verify(auth, never()).register(any(), any());
        assertTrue(output.toString().contains("Cancelled."));
        assertTrue(output.toString().contains("valid YYYY-MM-DD"));
    }

    @Test
    void expiryAfterBlockedInputDoesNotDisplayProfile() {
        AuthService auth = mock(AuthService.class);
        when(auth.currentCustomer()).thenThrow(new SessionExpiredException());
        StringWriter output = new StringWriter();
        var input = new ConsoleInput(new StringReader("2\ntester\n1\n3\n"),
                new PrintWriter(output), () -> "Valid-Password-123!".toCharArray());
        new MainMenu(auth, input).run();
        assertTrue(output.toString().contains("Session expired."));
        assertFalse(output.toString().contains("Customer code:"));
    }

    @Test
    void unsavedAuditBlocksCommandsAndWarnsBeforeAcknowledgedExit() {
        AuthService auth = mock(AuthService.class);
        LoginAudit evidence = new LoginAudit(null, null, "tester", LoginOutcome.FAILURE,
                "INVALID_CREDENTIALS", Instant.parse("2026-11-11T12:00:00Z"));
        when(auth.pendingAudit()).thenReturn(Optional.of(new LoginAuditDelivery.Result(
                evidence, LoginAuditDelivery.Status.SAVE_UNCERTAIN)));
        StringWriter output = new StringWriter();
        var input = new ConsoleInput(new StringReader("2\nEXIT\n"), new PrintWriter(output),
                () -> new char[0]);
        new MainMenu(auth, input).run();
        verify(auth, never()).login(anyString(), any());
        assertTrue(output.toString().contains("Exiting loses unsaved evidence"));
        assertFalse(output.toString().contains("tester"));
    }
}
