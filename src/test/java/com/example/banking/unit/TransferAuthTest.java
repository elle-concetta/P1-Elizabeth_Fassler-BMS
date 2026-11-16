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
import static com.example.banking.unit.TransferFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TransferAuthTest {
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

    @Test void unauthenticatedTransferCannotProbeRecipientOrWriteEvidence() {
        assertThrows(AuthenticationException.class, () -> auth.transfer(7, RECIPIENT.value(), "5"));
        verifyNoInteractions(connections, customers, financialAudit);
    }

    @Test void expiredSessionAfterFormCannotSubmit() {
        login();
        when(clock.instant()).thenReturn(TIME.plusSeconds(300));
        assertThrows(SessionExpiredException.class, () -> auth.transfer(7, RECIPIENT.value(), "5"));
        verifyNoInteractions(connections, customers, financialAudit);
    }

    @Test void suppliedMaximumIsAppliedBeforeAnyMoneySql() {
        login();
        assertThrows(TransferException.class, () -> auth.transfer(7, RECIPIENT.value(), "10.01"));
        verifyNoInteractions(connections, customers);
        verify(financialAudit).append(argThat(e -> e.eventType() == TransactionType.TRANSFER
                && e.failureCode().orElseThrow().equals("INVALID_AMOUNT")));
    }

    @Test void pendingTransferBlocksEveryMoneyAndProtectedAuthenticationPath() {
        login();
        doThrow(new PersistenceException(null)).when(financialAudit).append(any());
        assertThrows(TransferException.class, () -> auth.transfer(7, RECIPIENT.value(), "0"));
        var exact = auth.pendingDeposit().orElseThrow();
        clearInvocations(connections, customers, financialAudit);
        assertThrows(TransferException.class, () -> auth.transfer(7, RECIPIENT.value(), "5"));
        assertThrows(TransferException.class, () -> auth.deposit(7, "5"));
        assertThrows(TransferException.class, () -> auth.withdraw(7, "5"));
        assertThrows(TransferException.class, auth::currentCustomer);
        assertThrows(TransferException.class, auth::currentProfile);
        assertThrows(TransferException.class, auth::accounts);
        assertThrows(TransferException.class, () -> auth.accountDetails(7));
        assertThrows(TransferException.class, () -> auth.openAccount(AccountType.SAVINGS));
        assertThrows(TransferException.class, () -> auth.updateContact(new ContactDetails("demo@example.invalid", "2025550123", null)));
        assertThrows(TransferException.class, () -> auth.changePassword("Private-Demo-1!".toCharArray(),
                "Replacement-Demo-2!".toCharArray(), "Replacement-Demo-2!".toCharArray()));
        assertThrows(TransferException.class, () -> auth.login("demo", "Private-Demo-1!".toCharArray()));
        assertThrows(TransferException.class, () -> auth.register(null, new char[0]));
        assertThrows(TransferException.class, auth::logout);
        assertSame(exact, auth.pendingDeposit().orElseThrow());
        verifyNoInteractions(connections, customers, financialAudit);
    }

    @Test void pendingWithdrawalCannotBeOverwrittenByANewTransfer() {
        login();
        doThrow(new PersistenceException(null)).when(financialAudit).append(any());
        assertThrows(WithdrawalException.class, () -> auth.withdraw(7, "0"));
        var exact = auth.pendingDeposit().orElseThrow();
        clearInvocations(connections, customers, financialAudit);
        assertThrows(WithdrawalException.class, () -> auth.transfer(7, RECIPIENT.value(), "5"));
        assertSame(exact, auth.pendingDeposit().orElseThrow());
        verifyNoInteractions(connections, customers, financialAudit);
    }

    private void login() {
        auth.login("demo", "Private-Demo-1!".toCharArray());
        clearInvocations(connections, customers, financialAudit);
    }
}

