package com.example.banking.unit;

import com.example.banking.dao.*;
import com.example.banking.exception.*;
import com.example.banking.model.*;
import com.example.banking.security.*;
import com.example.banking.service.AuthService;
import com.example.banking.util.ConnectionFactory;
import com.example.banking.validation.CustomerValidator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.sql.*;
import java.time.*;
import java.util.Optional;
import static com.example.banking.unit.DepositFixtures.*;
import static com.example.banking.unit.HistoryFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HistoryAuthTest {
    private AuthService auth;
    private ConnectionFactory connections;
    private CustomerDAO customers;
    private TransactionAuditDAO audit;
    private Clock clock;

    @BeforeEach void setup() throws Exception {
        connections = mock(ConnectionFactory.class);
        var connection = mock(Connection.class);
        customers = mock(CustomerDAO.class); audit = mock(TransactionAuditDAO.class); clock = mock(Clock.class);
        when(clock.instant()).thenReturn(TIME);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(connections.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(mock(Statement.class));
        var hasher = new BcryptPasswordHasher(4);
        var hash = new PasswordHash(hasher.hash("Private-Demo-1!".toCharArray()));
        when(customers.lockByUsername(connection, "demo")).thenReturn(Optional.of(
                new CustomerDAO.AuthenticationRow(customer(1, CustomerStatus.ACTIVE), hash, 0, null)));
        auth = new AuthService(connections, customers, hasher, new CustomerValidator(clock),
                new SessionManager(clock, Duration.ofSeconds(300)), new LoginAuditDelivery(mock(LoginAuditDAO.class)),
                clock, 5, Duration.ofMinutes(15), 10, mock(SecurityAuditDAO.class), Money.parse("10"), audit);
    }

    @Test void noSessionCannotReadHistory() {
        assertThrows(AuthenticationException.class, () -> auth.history(7, query(0, 20)));
        verifyNoInteractions(connections, customers, audit);
    }

    @Test void loggedOutSessionCannotReadHistory() {
        login();
        auth.logout();
        clearInvocations(connections, customers, audit);
        assertThrows(AuthenticationException.class, () -> auth.history(7, query(0, 20)));
        verifyNoInteractions(connections, customers, audit);
    }

    @Test void expiryAfterFormIsCheckedBeforeHistorySql() {
        login();
        when(clock.instant()).thenReturn(TIME.plusSeconds(300));
        assertThrows(SessionExpiredException.class, () -> auth.history(7, query(0, 20)));
        verifyNoInteractions(connections, customers, audit);
    }

    @ParameterizedTest @EnumSource(value = TransactionType.class, names = {"DEPOSIT", "WITHDRAWAL", "TRANSFER"})
    void pendingFinancialEvidenceBlocksTheNewHistoryPath(TransactionType type) {
        login();
        doThrow(new PersistenceException(null)).when(audit).append(any());
        assertThrows(FinancialOperationException.class, () -> {
            switch (type) {
                case DEPOSIT -> auth.deposit(7, "0");
                case WITHDRAWAL -> auth.withdraw(7, "0");
                case TRANSFER -> auth.transfer(7, "0".repeat(24), "0");
                default -> throw new AssertionError();
            }
        });
        clearInvocations(connections, customers, audit);
        assertThrows(FinancialOperationException.class, () -> auth.history(7, query(0, 20)));
        verifyNoInteractions(connections, customers, audit);
    }

    private void login() {
        auth.login("demo", "Private-Demo-1!".toCharArray());
        clearInvocations(connections, customers, audit);
    }
}

