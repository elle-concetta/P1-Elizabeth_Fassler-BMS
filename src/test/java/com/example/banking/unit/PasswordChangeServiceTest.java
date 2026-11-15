package com.example.banking.unit;

import com.example.banking.dao.*;
import com.example.banking.exception.*;
import com.example.banking.model.*;
import com.example.banking.security.PasswordHasher;
import com.example.banking.service.PasswordChangeService;
import com.example.banking.util.ConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordChangeServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final PasswordHash OLD = new PasswordHash("$2a$04$" + "A".repeat(53));
    private static final PasswordHash NEW = new PasswordHash("$2a$04$" + "B".repeat(53));
    private final Customer owner = new Customer(7, "C-fixture", "Test", null, "Person",
            null, null, "test@example.invalid", "2025550123", "tester", CustomerStatus.ACTIVE);
    private ConnectionFactory connections;
    private Connection connection;
    private CustomerDAO customers;
    private SecurityAuditDAO audit;
    private PasswordHasher passwords;
    private Supplier<Customer> identity;
    private PasswordChangeService service;
    private char[] current, replacement, confirmation;

    @BeforeEach @SuppressWarnings("unchecked")
    void setup() throws Exception {
        connections = mock(ConnectionFactory.class);
        connection = mock(Connection.class);
        customers = mock(CustomerDAO.class);
        audit = mock(SecurityAuditDAO.class);
        passwords = mock(PasswordHasher.class);
        identity = mock(Supplier.class);
        when(identity.get()).thenReturn(owner);
        when(connections.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(mock(Statement.class));
        when(customers.lockByUsername(connection, "tester")).thenReturn(
                Optional.of(row(OLD, 0, null)), Optional.of(row(NEW, 0, null)));
        when(passwords.verify(any(char[].class), anyString())).thenReturn(true);
        when(passwords.hash(any(char[].class))).thenReturn(NEW.value());
        service = new PasswordChangeService(connections, customers, audit, passwords, identity,
                Clock.fixed(NOW, ZoneOffset.UTC), 5, Duration.ofMinutes(15), 10, OLD.value());
        current = "Previous-Pass-1!".toCharArray();
        replacement = "Replacement-Pass-2!".toCharArray();
        confirmation = replacement.clone();
    }

    @Test void successfulReplacementAndAuditCommitOnceUnderTheSameLock() throws Exception {
        assertEquals(PasswordChangeService.Result.SUCCESS, change());
        var order = inOrder(customers, passwords, audit, connection);
        order.verify(connection).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        order.verify(connection).setAutoCommit(false);
        order.verify(customers).lockByUsername(connection, "tester");
        order.verify(passwords).verify(same(current), eq(OLD.value()));
        order.verify(passwords).hash(same(replacement));
        order.verify(customers).updatePasswordHash(connection, 7, NEW);
        order.verify(customers).lockByUsername(connection, "tester");
        order.verify(customers).updateLoginState(connection, 7, 0, null);
        order.verify(audit).append(eq(connection), argThat(event -> event.customerId() == 7
                && event.outcome() == SecurityAuditEvent.Outcome.SUCCESS
                && event.failureReason() == null && event.createdAt().equals(NOW)));
        order.verify(connection).commit();
        order.verify(connection).close();
        verify(connection, never()).rollback();
        var statement = connection.createStatement();
        verify(statement).execute("SET SESSION time_zone = '+00:00'");
        verify(statement).execute("SET SESSION innodb_lock_wait_timeout = 10");
        cleared();
    }

    @Test void wrongCurrentPasswordRecordsFailureAndCounterButNeverGeneratesAHash() throws Exception {
        when(passwords.verify(any(char[].class), anyString())).thenReturn(false);
        assertEquals(PasswordChangeService.Result.REJECTED, change());
        verify(customers).updateLoginState(connection, 7, 1, null);
        verify(customers, never()).updatePasswordHash(any(), anyLong(), any());
        verify(passwords, never()).hash(any());
        verify(audit).append(eq(connection), argThat(e -> e.outcome() == SecurityAuditEvent.Outcome.FAILURE
                && "INVALID_CURRENT_PASSWORD".equals(e.failureReason())));
        verify(connection).commit();
        cleared();
    }

    @Test void fifthWrongCurrentPasswordLocksTheAccountAndAuditsIt() throws Exception {
        when(customers.lockByUsername(connection, "tester")).thenReturn(Optional.of(row(OLD, 4, null)));
        when(passwords.verify(any(char[].class), anyString())).thenReturn(false);
        assertEquals(PasswordChangeService.Result.LOCKED, change());
        verify(customers).updateLoginState(connection, 7, 5, NOW.plusSeconds(900));
        verify(audit).append(eq(connection), argThat(e -> "TOO_MANY_ATTEMPTS".equals(e.failureReason())));
        verify(connection).commit();
    }

    @Test void unavailableAccountDoesNotReplacePasswordOrResetLock() throws Exception {
        when(customers.lockByUsername(connection, "tester"))
                .thenReturn(Optional.of(row(OLD, 5, NOW.plusSeconds(60))));
        assertEquals(PasswordChangeService.Result.LOCKED, change());
        verify(customers, never()).updatePasswordHash(any(), anyLong(), any());
        verify(customers, never()).updateLoginState(any(), anyLong(), anyInt(), any());
        verify(audit).append(eq(connection), argThat(e -> "ACCOUNT_UNAVAILABLE".equals(e.failureReason())));
    }

    @Test void expiredLockStartsANewFailureCount() throws Exception {
        when(customers.lockByUsername(connection, "tester"))
                .thenReturn(Optional.of(row(OLD, 5, NOW.minusSeconds(1))));
        when(passwords.verify(any(char[].class), anyString())).thenReturn(false);
        assertEquals(PasswordChangeService.Result.REJECTED, change());
        verify(customers).updateLoginState(connection, 7, 1, null);
    }

    @Test void unrepresentableLockExpiryCannotSilentlyBecomeAnUnlockedAccount() throws Exception {
        service = new PasswordChangeService(connections, customers, audit, passwords, identity,
                Clock.fixed(Instant.parse("2038-01-19T03:14:07Z"), ZoneOffset.UTC),
                5, Duration.ofMinutes(15), 10, OLD.value());
        when(customers.lockByUsername(connection, "tester")).thenReturn(Optional.of(row(OLD, 4, null)));
        when(passwords.verify(any(char[].class), anyString())).thenReturn(false);
        assertThrows(PasswordChangeUnconfirmedException.class, this::change);
        verify(customers, never()).updateLoginState(any(), anyLong(), anyInt(), any());
        verifyNoInteractions(audit);
        verify(connection).rollback();
        cleared();
    }

    @ParameterizedTest @EnumSource(value = CustomerStatus.class, names = {"CLOSED", "LOCKED"})
    void inactiveStatusCannotReplacePassword(CustomerStatus status) throws Exception {
        var inactive = new Customer(7, "C-fixture", "Test", null, "Person", null, null,
                "test@example.invalid", "2025550123", "tester", status);
        when(customers.lockByUsername(connection, "tester")).thenReturn(Optional.of(
                new CustomerDAO.AuthenticationRow(inactive, OLD, 0, null)));
        assertEquals(PasswordChangeService.Result.LOCKED, change());
        verify(customers, never()).updatePasswordHash(any(), anyLong(), any());
        verify(audit).append(eq(connection), argThat(e -> "ACCOUNT_UNAVAILABLE".equals(e.failureReason())));
    }

    @ParameterizedTest @ValueSource(strings = {"short", "alllowercase123!", "Whitespace 12!",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void invalidNewPasswordIsRejectedBeforeJdbc(String value) {
        replacement = value.toCharArray();
        confirmation = replacement.clone();
        assertThrows(ValidationException.class, this::change);
        verifyNoInteractions(connections, customers, audit, passwords);
        cleared();
    }

    @Test void mismatchDoesNotReadOrWriteTheDatabase() {
        confirmation = "Different-Pass-3!".toCharArray();
        assertThrows(ValidationException.class, this::change);
        verifyNoInteractions(connections, customers, audit, passwords);
        cleared();
    }

    @Test void currentPasswordCannotBeReused() throws Exception {
        replacement = current.clone(); confirmation = current.clone();
        assertThrows(ValidationException.class, this::change);
        verify(customers, never()).updatePasswordHash(any(), anyLong(), any());
        verifyNoInteractions(audit);
        verify(connection).rollback();
        cleared();
    }

    @Test void expiredSessionCannotReachJdbcAndStillClearsAllArrays() {
        doThrow(new SessionExpiredException()).when(identity).get();
        assertThrows(SessionExpiredException.class, this::change);
        verifyNoInteractions(connections, customers, passwords, audit);
        cleared();
    }

    @Test void wrongOwnerIsDeniedUnderTheRowLock() throws Exception {
        var other = new Customer(99, "C-other", "Other", null, "Person",
                null, null, "other@example.invalid", "2025550199", "tester", CustomerStatus.ACTIVE);
        when(customers.lockByUsername(connection, "tester")).thenReturn(
                Optional.of(new CustomerDAO.AuthenticationRow(other, OLD, 0, null)));
        assertThrows(AuthenticationException.class, this::change);
        verifyNoInteractions(passwords, audit);
        verify(connection).rollback();
        cleared();
    }

    @Test void auditFailureRollsBackThePasswordAndIsNeverRetried() throws Exception {
        doThrow(new SQLException("PRIVATE audit detail")).when(audit).append(any(), any());
        var failure = assertThrows(PasswordChangeUnconfirmedException.class, this::change);
        assertTrue(failure.getMessage().contains("Reference:"));
        assertFalse(failure.getMessage().contains("PRIVATE"));
        assertNull(failure.getCause());
        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(audit).append(any(), any());
        cleared();
    }

    @Test void readBackMismatchCannotCommitSuccessEvidence() throws Exception {
        when(customers.lockByUsername(connection, "tester")).thenReturn(Optional.of(row(OLD, 0, null)));
        assertThrows(PasswordChangeUnconfirmedException.class, this::change);
        verifyNoInteractions(audit);
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test void commitAndCleanupFailuresKeepOnlyASafeReference() throws Exception {
        doThrow(new SQLException("PRIVATE commit")).when(connection).commit();
        doThrow(new SQLException("PRIVATE close")).when(connection).close();
        var failure = assertThrows(PasswordChangeUnconfirmedException.class, this::change);
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        verify(connections).getConnection();
        verify(connection).rollback();
        cleared();
    }

    @Test void closeFailureAfterCommitIsUnconfirmedAndNeverReplayed() throws Exception {
        doThrow(new SQLException("PRIVATE cleanup")).when(connection).close();
        var failure = assertThrows(PasswordChangeUnconfirmedException.class, this::change);
        assertNull(failure.getCause());
        verify(connection).commit();
        verify(audit).append(any(), any());
        verify(connections).getConnection();
        cleared();
    }

    private PasswordChangeService.Result change() { return service.change(current, replacement, confirmation); }
    private CustomerDAO.AuthenticationRow row(PasswordHash hash, int count, Instant until) {
        return new CustomerDAO.AuthenticationRow(owner, hash, count, until);
    }
    private void cleared() {
        for (char[] value : List.of(current, replacement, confirmation))
            for (char character : value) assertEquals('\0', character);
    }
}

