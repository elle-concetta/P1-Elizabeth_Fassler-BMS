package com.example.banking.unit;

import com.example.banking.dao.CustomerDAO;
import com.example.banking.dao.LoginAuditDAO;
import com.example.banking.exception.*;
import com.example.banking.model.*;
import com.example.banking.security.*;
import com.example.banking.service.AuthService;
import com.example.banking.util.ConnectionFactory;
import com.example.banking.validation.CustomerValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private static final Instant TIME = Instant.parse("2026-11-11T12:00:00Z");
    private static final String HASH = "$2a$04$" + "A".repeat(53);
    private ConnectionFactory connections;
    private Connection connection;
    private CustomerDAO customers;
    private LoginAuditDAO audits;
    private PasswordHasher hasher;
    private Clock clock;
    private SessionManager sessions;
    private AuthService auth;
    private final Customer customer = new Customer(7, "C-fixture", "Test", null, "Person",
            null, null, "test@example.invalid", null, "tester", CustomerStatus.ACTIVE);
    private final Registration input = new Registration("Test", null, "Person", null, null,
            "test@example.invalid", null, "tester");

    @BeforeEach
    void setup() throws Exception {
        connections = mock(ConnectionFactory.class);
        connection = mock(Connection.class);
        customers = mock(CustomerDAO.class);
        audits = mock(LoginAuditDAO.class);
        hasher = mock(PasswordHasher.class);
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(TIME);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(connections.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(mock(Statement.class));
        when(hasher.hash(any(char[].class))).thenReturn(HASH);
        when(hasher.verify(any(char[].class), anyString())).thenReturn(true);
        when(customers.lockByUsername(connection, "tester")).thenReturn(Optional.of(
                new CustomerDAO.AuthenticationRow(customer, new PasswordHash(HASH), 0, null)));
        when(customers.insert(eq(connection), eq(input), anyString(), any(PasswordHash.class)))
                .thenReturn(customer);
        sessions = new SessionManager(clock, Duration.ofSeconds(300));
        auth = new AuthService(connections, customers, hasher, new CustomerValidator(clock),
                sessions, new LoginAuditDelivery(audits), clock, 5, Duration.ofMinutes(15), 10);
    }

    @Test
    void registrationStoresOnlyEncodedHashCommitsAndClearsRawBuffer() throws Exception {
        char[] raw = password();
        assertSame(customer, auth.register(input, raw));
        assertCleared(raw);
        verify(customers).insert(eq(connection), eq(input), argThat(s -> s.length() == 30),
                eq(new PasswordHash(HASH)));
        verify(connection).commit();
        verify(connection).close();
        verifyNoInteractions(audits);
    }

    @Test
    void invalidRegistrationDoesNotOpenConnectionAndStillClearsPassword() {
        char[] raw = "weak".toCharArray();
        assertThrows(ValidationException.class, () -> auth.register(input, raw));
        assertCleared(raw);
        verifyNoInteractions(connections, customers);
    }

    @Test
    void duplicatePrecheckRollsBackWithoutInsertion() throws Exception {
        when(customers.identityExists(connection, input.username(), input.email())).thenReturn(true);
        char[] raw = password();
        assertThrows(ValidationException.class, () -> auth.register(input, raw));
        assertCleared(raw);
        verify(connection).rollback();
        verify(customers, never()).insert(any(), any(), anyString(), any());
    }

    @Test
    void duplicateRaceIsHandledButOtherIntegrityFailuresAreNotMislabeled() throws Exception {
        when(customers.insert(eq(connection), eq(input), anyString(), any()))
                .thenThrow(new SQLException("PRIVATE", "23000", 1062))
                .thenThrow(new SQLException("PRIVATE", "23000", 1452));
        assertThrows(ValidationException.class, () -> auth.register(input, password()));
        var failure = assertThrows(PersistenceException.class, () -> auth.register(input, password()));
        assertFalse(failure.getCause().toString().contains("PRIVATE"));
        verify(connection, times(2)).rollback();
        verify(connection, never()).commit();
    }

    @Test
    void loginCommitsBeforeIndependentAuditAndStartsSessionOnlyAfterSavedEvidence() throws Exception {
        char[] raw = password();
        assertSame(customer, auth.login("tester", raw));
        assertCleared(raw);
        var order = inOrder(connection, customers, audits);
        order.verify(customers).lockByUsername(connection, "tester");
        order.verify(customers).updateLoginState(connection, 7, 0, null);
        order.verify(connection).commit();
        order.verify(connection).close();
        order.verify(audits).append(argThat(row -> row.outcome() == LoginOutcome.SUCCESS
                && row.createdAt().equals(TIME) && row.usernameAttempted().equals("tester")));
        assertSame(customer, auth.currentCustomer());
        verify(connection, never()).rollback();
    }

    @Test
    void unknownUsernameUsesDummyHashAndGenericFailureWithoutSession() throws Exception {
        when(customers.lockByUsername(connection, "unknown")).thenReturn(Optional.empty());
        char[] raw = password();
        AuthenticationException failure = assertThrows(AuthenticationException.class,
                () -> auth.login("unknown", raw));
        assertEquals("Invalid username or password.", failure.getMessage());
        assertCleared(raw);
        verify(hasher).verify(raw, HASH);
        verify(audits).append(argThat(row -> row.customerId().isEmpty()
                && row.usernameAttempted().equals("unknown") && row.outcome() == LoginOutcome.FAILURE));
        assertThrows(AuthenticationException.class, auth::currentCustomer);
    }

    @Test
    void wrongPasswordAtThresholdSetsTimedLockRatherThanPermanentStatus() throws Exception {
        when(customers.lockByUsername(connection, "tester")).thenReturn(Optional.of(
                new CustomerDAO.AuthenticationRow(customer, new PasswordHash(HASH), 4, null)));
        when(hasher.verify(any(), anyString())).thenReturn(false);
        assertThrows(AuthenticationException.class, () -> auth.login("tester", password()));
        verify(customers).updateLoginState(connection, 7, 5, TIME.plusSeconds(900));
        verify(audits).append(argThat(row -> row.outcome() == LoginOutcome.LOCKED
                && row.failureReason().orElseThrow().equals("TOO_MANY_ATTEMPTS")));
    }

    @Test
    void activeLockDoesNotUpdateCounterAndExpiredLockAllowsCorrectPassword() throws Exception {
        when(customers.lockByUsername(connection, "tester")).thenReturn(Optional.of(
                new CustomerDAO.AuthenticationRow(customer, new PasswordHash(HASH), 5, TIME.plusSeconds(1))));
        assertThrows(AuthenticationException.class, () -> auth.login("tester", password()));
        verify(customers, never()).updateLoginState(any(), anyLong(), anyInt(), any());
        when(customers.lockByUsername(connection, "tester")).thenReturn(Optional.of(
                new CustomerDAO.AuthenticationRow(customer, new PasswordHash(HASH), 5, TIME)));
        assertSame(customer, auth.login("tester", password()));
        verify(customers).updateLoginState(connection, 7, 0, null);
    }

    @Test
    void expiredLockStartsFailureCountAgainInsteadOfImmediatelyRelocking() throws Exception {
        when(customers.lockByUsername(connection, "tester")).thenReturn(Optional.of(
                new CustomerDAO.AuthenticationRow(customer, new PasswordHash(HASH), 5, TIME.minusSeconds(1))));
        when(hasher.verify(any(), anyString())).thenReturn(false);
        assertThrows(AuthenticationException.class, () -> auth.login("tester", password()));
        verify(customers).updateLoginState(connection, 7, 1, null);
    }

    @Test
    void administrativelyLockedAndClosedCustomersCannotAuthenticate() throws Exception {
        for (CustomerStatus status : new CustomerStatus[]{CustomerStatus.LOCKED, CustomerStatus.CLOSED}) {
            Customer unavailable = new Customer(7, "C-fixture", "Test", null, "Person",
                    null, null, "test@example.invalid", null, "tester", status);
            when(customers.lockByUsername(connection, "tester")).thenReturn(Optional.of(
                    new CustomerDAO.AuthenticationRow(unavailable, new PasswordHash(HASH), 0, null)));
            assertThrows(AuthenticationException.class, () -> auth.login("tester", password()));
        }
        verify(customers, never()).updateLoginState(any(), anyLong(), anyInt(), any());
    }

    @Test
    void oversizedAttemptIsMarkedBeforeLookupAndPasswordsAreCleared() throws Exception {
        char[] raw = password();
        assertThrows(AuthenticationException.class, () -> auth.login("z".repeat(81), raw));
        assertCleared(raw);
        verifyNoInteractions(connections, customers);
        verify(audits).append(argThat(row -> row.usernameAttempted().equals("[OVERSIZED_USERNAME]")
                && row.failureReason().orElseThrow().equals("USERNAME_TOO_LONG")));
    }

    @Test
    void auditFailureRetainsExactEvidenceBlocksMoreAttemptsAndDoesNotCreateSession() throws Exception {
        doThrow(new PersistenceException(new SQLException("PRIVATE"))).when(audits).append(any());
        char[] raw = password();
        assertThrows(AuditUnavailableException.class, () -> auth.login("tester", raw));
        assertCleared(raw);
        var retained = auth.pendingAudit().orElseThrow();
        assertEquals(LoginAuditDelivery.Status.SAVE_UNCERTAIN, retained.status());
        assertEquals(TIME, retained.evidence().createdAt());
        assertEquals("tester", retained.evidence().usernameAttempted());
        assertThrows(AuthenticationException.class, sessions::requireActive);
        assertThrows(AuditUnavailableException.class, () -> auth.login("tester", password()));
        verify(audits, times(1)).append(any());
        verify(customers, times(1)).lockByUsername(any(), anyString());
    }

    @Test
    void rejectedAuditIsDistinctFromUncertainSaveAndAlsoBlocksAuthentication() {
        doThrow(new IllegalArgumentException("unsupported")).when(audits).append(any());
        assertThrows(AuditUnavailableException.class, () -> auth.login("tester", password()));
        assertEquals(LoginAuditDelivery.Status.REJECTED_NO_WRITE,
                auth.pendingAudit().orElseThrow().status());
    }

    @Test
    void primaryDatabaseFailureIsRolledBackAuditedAndNeverRetried() throws Exception {
        when(customers.lockByUsername(connection, "tester")).thenThrow(new SQLException("PRIVATE"));
        char[] raw = password();
        var failure = assertThrows(PersistenceException.class, () -> auth.login("tester", raw));
        assertCleared(raw);
        assertFalse(failure.getCause().toString().contains("PRIVATE"));
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(customers, times(1)).lockByUsername(connection, "tester");
        verify(audits).append(argThat(row -> row.failureReason().orElseThrow()
                .equals("AUTHENTICATION_UNCONFIRMED")));
    }

    @Test
    void timeoutAndLogoutInvalidateOnceAndWriteSeparateEvents() {
        auth.login("tester", password());
        when(clock.instant()).thenReturn(TIME.plusSeconds(300));
        assertThrows(SessionExpiredException.class, auth::currentCustomer);
        assertThrows(AuthenticationException.class, auth::currentCustomer);
        ArgumentCaptor<LoginAudit> events = ArgumentCaptor.forClass(LoginAudit.class);
        verify(audits, times(2)).append(events.capture());
        assertEquals(LoginOutcome.TIMEOUT, events.getAllValues().get(1).outcome());
        when(clock.instant()).thenReturn(TIME.plusSeconds(301));
        auth.login("tester", password());
        auth.logout();
        auth.logout();
        assertThrows(AuthenticationException.class, auth::currentCustomer);
        verify(audits, times(1)).append(argThat(row -> row.outcome() == LoginOutcome.LOGOUT));
    }

    @Test
    void fractionalClockIsNotSilentlyRoundedAndFailsBeforeAuthenticationWork() {
        when(clock.instant()).thenReturn(TIME.plusNanos(1));
        char[] raw = password();
        assertThrows(IllegalStateException.class, () -> auth.login("tester", raw));
        assertCleared(raw);
        verifyNoInteractions(connections, customers, audits);
    }

    private char[] password() {
        return "Valid-Password-123!".toCharArray();
    }

    private void assertCleared(char[] raw) {
        char[] zeroes = new char[raw.length];
        assertTrue(Arrays.equals(zeroes, raw));
    }
}

