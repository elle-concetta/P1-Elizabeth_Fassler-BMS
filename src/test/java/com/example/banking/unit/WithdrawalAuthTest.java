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

import static com.example.banking.unit.DepositFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WithdrawalAuthTest {
    private AuthService auth;
    private ConnectionFactory connections;
    private CustomerDAO customers;
    private TransactionAuditDAO financialAudit;
    private Clock clock;

    @BeforeEach void setup() throws Exception {
        connections = mock(ConnectionFactory.class);
        var connection = mock(Connection.class);
        customers = mock(CustomerDAO.class);
        financialAudit = mock(TransactionAuditDAO.class);
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(TIME);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(connections.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(mock(Statement.class));
        var hasher = new BcryptPasswordHasher(4);
        var hash = new PasswordHash(hasher.hash("Private-Demo-1!".toCharArray()));
        when(customers.lockByUsername(connection, "demo")).thenReturn(Optional.of(
                new CustomerDAO.AuthenticationRow(customer(1, CustomerStatus.ACTIVE), hash, 0, null)));
        auth = new AuthService(connections, customers, hasher, new CustomerValidator(clock),
                new SessionManager(clock, Duration.ofSeconds(300)),
                new LoginAuditDelivery(mock(LoginAuditDAO.class)), clock, 5,
                Duration.ofMinutes(15), 10, mock(SecurityAuditDAO.class), Money.parse("10"), financialAudit);
    }

    @Test void unauthenticatedWithdrawalCannotReachMoneySqlOrFinancialAudit() {
        assertThrows(AuthenticationException.class, () -> auth.withdraw(7, "5"));
        verifyNoInteractions(connections, customers, financialAudit);
    }

    @Test void expiredSessionCannotSubmitAWithdrawal() {
        login();
        when(clock.instant()).thenReturn(TIME.plusSeconds(300));
        assertThrows(SessionExpiredException.class, () -> auth.withdraw(7, "5"));
        verifyNoInteractions(connections, customers, financialAudit);
    }

    @Test void configuredMaximumAlsoAppliesToWithdrawals() {
        login();
        assertThrows(WithdrawalException.class, () -> auth.withdraw(7, "10.01"));
        verifyNoInteractions(connections, customers);
        verify(financialAudit).append(argThat(e -> e.eventType() == TransactionType.WITHDRAWAL
                && e.failureCode().orElseThrow().equals("INVALID_AMOUNT")));
    }

    @Test void retainedWithdrawalEvidenceBlocksAllProtectedAndAuthenticationActions() {
        login();
        doThrow(new PersistenceException(null)).when(financialAudit).append(any());
        assertThrows(WithdrawalException.class, () -> auth.withdraw(7, "0"));
        var exact = auth.pendingDeposit().orElseThrow();
        clearInvocations(connections, customers, financialAudit);
        assertThrows(WithdrawalException.class, () -> auth.withdraw(7, "5"));
        assertThrows(WithdrawalException.class, () -> auth.deposit(7, "5"));
        assertThrows(WithdrawalException.class, auth::currentCustomer);
        assertThrows(WithdrawalException.class, auth::currentProfile);
        assertThrows(WithdrawalException.class, auth::accounts);
        assertThrows(WithdrawalException.class, () -> auth.accountDetails(7));
        assertThrows(WithdrawalException.class, () -> auth.openAccount(AccountType.SAVINGS));
        assertThrows(WithdrawalException.class, () -> auth.updateContact(new ContactDetails("demo@example.invalid", "2025550123", null)));
        assertThrows(WithdrawalException.class, () -> auth.changePassword("Private-Demo-1!".toCharArray(),
                "Replacement-Demo-2!".toCharArray(), "Replacement-Demo-2!".toCharArray()));
        assertThrows(WithdrawalException.class, () -> auth.login("demo", "Private-Demo-1!".toCharArray()));
        assertThrows(WithdrawalException.class, () -> auth.register(null, new char[0]));
        assertThrows(WithdrawalException.class, auth::logout);
        assertSame(exact, auth.pendingDeposit().orElseThrow());
        verifyNoInteractions(connections, customers, financialAudit);
    }

    @Test void retainedDepositEvidenceAlsoBlocksWithdrawalWithoutReplacingTheAttempt() {
        login();
        doThrow(new PersistenceException(null)).when(financialAudit).append(any());
        assertThrows(DepositException.class, () -> auth.deposit(7, "0"));
        var exact = auth.pendingDeposit().orElseThrow();
        clearInvocations(connections, customers, financialAudit);
        assertThrows(DepositException.class, () -> auth.withdraw(7, "5"));
        assertSame(exact, auth.pendingDeposit().orElseThrow());
        verifyNoInteractions(connections, customers, financialAudit);
    }

    private void login() {
        auth.login("demo", "Private-Demo-1!".toCharArray());
        clearInvocations(connections, customers, financialAudit);
    }
}

