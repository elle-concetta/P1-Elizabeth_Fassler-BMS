package com.example.banking.unit;

import com.example.banking.app.ConsoleInput;
import com.example.banking.app.CustomerMenu;
import com.example.banking.exception.SessionExpiredException;
import com.example.banking.exception.ValidationException;
import com.example.banking.model.ContactDetails;
import com.example.banking.model.Customer;
import com.example.banking.model.CustomerStatus;
import com.example.banking.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerContactCliTest {
    private AuthService auth;
    private StringWriter output;
    private final Customer before = profile("old@example.invalid", "2025550123", "Old Street");

    @BeforeEach
    void setup() {
        auth = mock(AuthService.class);
        output = new StringWriter();
        when(auth.currentCustomer()).thenReturn(before);
        when(auth.currentProfile()).thenReturn(before);
    }

    @Test
    void editSaveViewAndLogoutRemainInTheCustomerMenu() {
        Customer after = profile("new@example.invalid", "0123456789", "New Street");
        when(auth.currentProfile()).thenReturn(before, after);
        when(auth.updateContact(any())).thenReturn(after);
        run("3\nnew@example.invalid\n0123456789\nNew Street\n1\n2\n");
        verify(auth).updateContact(new ContactDetails("new@example.invalid", "0123456789", "New Street"));
        verify(auth).logout();
        assertTrue(output.toString().contains("Contact details saved."));
        assertTrue(output.toString().contains("Email: new@example.invalid"));
        assertTrue(output.toString().contains("Phone: 0123456789"));
        assertTrue(output.toString().contains("Address: New Street"));
        assertTrue(output.toString().contains("Logged out."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/clear"})
    void enterKeepsContactFieldsAndClearRemovesOnlyAddress(String address) {
        run("3\n\n\n" + address + "\n2\n");
        verify(auth).updateContact(new ContactDetails(before.email(), before.phone(),
                address.isEmpty() ? before.address() : null));
    }

    @Test
    void cancellingEditDoesNotSaveOrLeaveTheCustomerSession() {
        run("3\n/cancel\n1\n2\n");
        verify(auth, never()).updateContact(any());
        assertTrue(output.toString().contains("Edit cancelled. No changes saved."));
        assertTrue(output.toString().contains("Customer code: C-fixture"));
        verify(auth).logout();
    }

    @Test
    void validationErrorDoesNotClaimSuccessAndAllowsViewingTheExistingProfile() {
        when(auth.updateContact(any())).thenThrow(new ValidationException("Phone needs ten digits."));
        run("3\n\n123\n\n1\n2\n");
        assertTrue(output.toString().contains("Phone needs ten digits."));
        assertFalse(output.toString().contains("Contact details saved."));
        assertTrue(output.toString().contains("Phone: 2025550123"));
    }

    @Test
    void expiryDuringFormDoesNotClaimAnUpdateSucceeded() {
        when(auth.updateContact(any())).thenThrow(new SessionExpiredException());
        assertThrows(SessionExpiredException.class, () -> run("3\n\n\n\n"));
        assertFalse(output.toString().contains("Contact details saved."));
        verify(auth, never()).logout();
    }

    private void run(String script) {
        var input = new ConsoleInput(new StringReader(script), new PrintWriter(output),
                () -> { throw new AssertionError("Contact editing must not request a password."); });
        new CustomerMenu(auth, input).run();
    }

    private Customer profile(String email, String phone, String address) {
        return new Customer(7, "C-fixture", "Test", null, "Person", null,
                address, email, phone, "tester", CustomerStatus.ACTIVE);
    }
}

