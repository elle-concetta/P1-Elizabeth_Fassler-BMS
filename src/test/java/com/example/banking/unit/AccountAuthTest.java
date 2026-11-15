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
import java.util.Optional;

import static com.example.banking.unit.AccountFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Verifies that the real AuthService account entry points retain its session guards. */
class AccountAuthTest {
    private AuthService auth;
    private ConnectionFactory connections;
    private Connection connection;
    private CustomerDAO customers;
    private Clock clock;
    private final Instant now = Instant.parse("2026-11-14T12:00:00Z");

    @BeforeEach void setup() throws Exception {
        connections = mock(ConnectionFactory.class);
        connection = mock(Connection.class);
        customers = mock(CustomerDAO.class);
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var hasher = new BcryptPasswordHasher(4);
        var hash = new PasswordHash(hasher.hash("Private-Demo-1!".toCharArray()));
        when(connections.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(mock(Statement.class));
        when(customers.lockByUsername(connection, "demo")).thenReturn(Optional.of(
                new CustomerDAO.AuthenticationRow(customer(1, CustomerStatus.ACTIVE), hash, 0, null)));
        auth = new AuthService(connections, customers, hasher, new CustomerValidator(clock),
                new SessionManager(clock, Duration.ofSeconds(300)),
                new LoginAuditDelivery(mock(LoginAuditDAO.class)), clock,
                5, Duration.ofMinutes(15), 10, mock(SecurityAuditDAO.class));
    }

    @Test void unauthenticatedAccountActionsNeverReachJdbc() {
        assertThrows(AuthenticationException.class, () -> auth.openAccount(AccountType.SAVINGS));
        assertThrows(AuthenticationException.class, auth::accounts);
        assertThrows(AuthenticationException.class, () -> auth.accountDetails(7));
        verifyNoInteractions(connections, customers);
    }

    @Test void expiredSessionBlocksOpeningAndFurtherEnquiryBeforeJdbc() {
        login();
        when(clock.instant()).thenReturn(now.plusSeconds(300));
        assertThrows(SessionExpiredException.class, () -> auth.openAccount(AccountType.CURRENT));
        assertThrows(AuthenticationException.class, auth::accounts);
        assertThrows(AuthenticationException.class, () -> auth.accountDetails(7));
        verifyNoInteractions(connections, customers);
    }

    @Test void logoutBlocksAccountActions() {
        login();
        auth.logout();
        clearInvocations(connections, customers);
        assertThrows(AuthenticationException.class, auth::accounts);
        assertThrows(AuthenticationException.class, () -> auth.openAccount(AccountType.SAVINGS));
        verifyNoInteractions(connections, customers);
    }

    @Test void unconfirmedPasswordChangeAlsoBlocksAccountEntryPoints() throws Exception {
        login();
        doThrow(new SQLException("PRIVATE")).when(connection).commit();
        assertThrows(PasswordChangeUnconfirmedException.class, () -> auth.changePassword(
                "Private-Demo-1!".toCharArray(), "Replacement-Demo-2!".toCharArray(),
                "Replacement-Demo-2!".toCharArray()));
        clearInvocations(connections, customers);
        assertThrows(PasswordChangeUnconfirmedException.class, () -> auth.openAccount(AccountType.SAVINGS));
        assertThrows(PasswordChangeUnconfirmedException.class, auth::accounts);
        assertThrows(PasswordChangeUnconfirmedException.class, () -> auth.accountDetails(7));
        verifyNoInteractions(connections, customers);
    }

    private void login() {
        auth.login("demo", "Private-Demo-1!".toCharArray());
        clearInvocations(connections, customers);
    }
}

