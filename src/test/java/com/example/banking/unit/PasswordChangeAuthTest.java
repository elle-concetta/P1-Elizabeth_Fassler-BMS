package com.example.banking.unit;

import com.example.banking.dao.*;
import com.example.banking.exception.*;
import com.example.banking.model.*;
import com.example.banking.security.*;
import com.example.banking.service.AuthService;
import com.example.banking.util.ConnectionFactory;
import com.example.banking.validation.CustomerValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordChangeAuthTest {
    private AuthService auth;
    private CustomerDAO customers;
    private SecurityAuditDAO security;
    private Connection connection;
    private ConnectionFactory connections;
    private Clock clock;
    private final Customer owner = new Customer(7, "C-fixture", "Test", null, "Person",
            null, null, "test@example.invalid", "2025550123", "tester", CustomerStatus.ACTIVE);
    private final Instant now = Instant.parse("2026-11-14T12:00:00Z");

    @BeforeEach void setup() throws Exception {
        connections = mock(ConnectionFactory.class);
        connection = mock(Connection.class);
        customers = mock(CustomerDAO.class);
        security = mock(SecurityAuditDAO.class);
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(now);
        var hasher = new BcryptPasswordHasher(4); // Test-only low cost; app.properties stays cost 12.
        var stored = new AtomicReference<>(new PasswordHash(hasher.hash("Previous-Pass-1!".toCharArray())));
        when(connections.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(mock(Statement.class));
        when(customers.lockByUsername(connection, "tester"))
                .thenAnswer(call -> Optional.of(new CustomerDAO.AuthenticationRow(owner, stored.get(), 0, null)));
        doAnswer(call -> { stored.set(call.getArgument(2)); return null; })
                .when(customers).updatePasswordHash(eq(connection), eq(7L), any());
        auth = new AuthService(connections, customers, hasher, new CustomerValidator(clock),
                new SessionManager(clock, Duration.ofSeconds(300)),
                new LoginAuditDelivery(mock(LoginAuditDAO.class)), clock, 5, Duration.ofMinutes(15), 10, security);
    }

    @Test void changedBcryptRejectsOldPasswordAcceptsNewAndRetainsTheSession() {
        auth.login("tester", "Previous-Pass-1!".toCharArray());
        auth.changePassword("Previous-Pass-1!".toCharArray(),
                "Replacement-Pass-2!".toCharArray(), "Replacement-Pass-2!".toCharArray());
        assertSame(owner, auth.currentCustomer());
        auth.logout();
        assertThrows(AuthenticationException.class,
                () -> auth.login("tester", "Previous-Pass-1!".toCharArray()));
        assertSame(owner, auth.login("tester", "Replacement-Pass-2!".toCharArray()));
    }

    @Test void expiredAndUnauthenticatedRequestsCannotChangeCredentials() {
        assertThrows(AuthenticationException.class, () -> change("Previous-Pass-1!"));
        auth.login("tester", "Previous-Pass-1!".toCharArray());
        clearInvocations(connections, customers, security);
        when(clock.instant()).thenReturn(now.plusSeconds(300));
        assertThrows(SessionExpiredException.class, () -> change("Previous-Pass-1!"));
        verifyNoInteractions(connections, customers, security);
    }

    @Test void wrongCurrentPasswordKeepsAnOtherwiseValidSession() throws Exception {
        auth.login("tester", "Previous-Pass-1!".toCharArray());
        clearInvocations(customers);
        assertThrows(ValidationException.class, () -> change("Wrong-Password-1!"));
        assertSame(owner, auth.currentCustomer());
        verify(customers, never()).updatePasswordHash(any(), anyLong(), any());
        verify(security).append(any(), argThat(e -> e.outcome() == SecurityAuditEvent.Outcome.FAILURE));
    }

    @Test void fifthWrongCurrentPasswordEndsTheSession() throws Exception {
        auth.login("tester", "Previous-Pass-1!".toCharArray());
        var row = customers.lockByUsername(connection, "tester").orElseThrow();
        when(customers.lockByUsername(connection, "tester")).thenReturn(Optional.of(
                new CustomerDAO.AuthenticationRow(owner, row.hash(), 4, null)));
        clearInvocations(customers);
        assertThrows(AuthenticationException.class, () -> change("Wrong-Password-1!"));
        assertThrows(AuthenticationException.class, auth::currentCustomer);
        verify(customers, never()).updatePasswordHash(any(), anyLong(), any());
        verify(security).append(any(), argThat(e -> e.outcome() == SecurityAuditEvent.Outcome.LOCKED));
    }

    @Test void unconfirmedCommitEndsSessionAndBlocksAllNewAuthenticationWithoutRetry() throws Exception {
        auth.login("tester", "Previous-Pass-1!".toCharArray());
        doThrow(new SQLException("PRIVATE commit")).when(connection).commit();
        assertThrows(PasswordChangeUnconfirmedException.class, () -> change("Previous-Pass-1!"));
        assertTrue(auth.pendingPasswordChange().isPresent());
        clearInvocations(connections, customers, security);
        assertThrows(PasswordChangeUnconfirmedException.class, auth::currentProfile);
        assertThrows(PasswordChangeUnconfirmedException.class,
                () -> auth.login("tester", "Replacement-Pass-2!".toCharArray()));
        assertThrows(PasswordChangeUnconfirmedException.class, () -> change("Previous-Pass-1!"));
        verifyNoInteractions(connections, customers, security);
    }

    private void change(String current) {
        auth.changePassword(current.toCharArray(), "Replacement-Pass-2!".toCharArray(),
                "Replacement-Pass-2!".toCharArray());
    }
}

