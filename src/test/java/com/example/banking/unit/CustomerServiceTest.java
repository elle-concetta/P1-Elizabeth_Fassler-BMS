package com.example.banking.unit;

import com.example.banking.dao.CustomerDAO;
import com.example.banking.exception.AuthenticationException;
import com.example.banking.exception.PersistenceException;
import com.example.banking.exception.SessionExpiredException;
import com.example.banking.exception.ValidationException;
import com.example.banking.model.ContactDetails;
import com.example.banking.model.Customer;
import com.example.banking.model.CustomerStatus;
import com.example.banking.service.CustomerService;
import com.example.banking.util.ConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerServiceTest {
    private ConnectionFactory connections;
    private Connection connection;
    private CustomerDAO customers;
    private Supplier<Customer> identity;
    private CustomerService service;
    private final ContactDetails desired = new ContactDetails("new@example.invalid", "0123456789", "New Street");
    private final Customer before = profile(7, CustomerStatus.ACTIVE,
            new ContactDetails("old@example.invalid", "2025550123", "Old Street"));
    private final Customer after = profile(7, CustomerStatus.ACTIVE, desired);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        connections = mock(ConnectionFactory.class);
        connection = mock(Connection.class);
        customers = mock(CustomerDAO.class);
        identity = mock(Supplier.class);
        when(identity.get()).thenReturn(before);
        when(connections.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(mock(Statement.class));
        when(customers.findProfile(connection, 7, true)).thenReturn(Optional.of(before));
        when(customers.findProfile(connection, 7, false)).thenReturn(Optional.of(after));
        service = new CustomerService(connections, customers, identity, 10);
    }

    @Test
    void updateUsesSessionIdentityLocksReadsBackCommitsAndCloses() throws Exception {
        assertSame(after, service.updateContact(desired));
        var order = inOrder(identity, connections, customers, connection);
        order.verify(identity).get();
        order.verify(connections).getConnection();
        order.verify(connection).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        order.verify(connection).setAutoCommit(false);
        order.verify(customers).findProfile(connection, 7, true);
        order.verify(customers).updateContact(connection, 7, desired);
        order.verify(customers).findProfile(connection, 7, false);
        order.verify(connection).commit();
        order.verify(connection).close();
        verify(connection, never()).rollback();
    }

    @Test
    void profileIsFreshReadOnlyAndUsesOnlyAuthenticatedId() throws Exception {
        assertSame(after, service.profile());
        verify(connection).setReadOnly(true);
        verify(customers).findProfile(connection, 7, false);
        verify(connection, never()).commit();
        verify(connection).close();
    }

    @Test
    void expiredOrLoggedOutIdentityCannotReachJdbc() {
        when(identity.get()).thenThrow(new SessionExpiredException());
        assertThrows(SessionExpiredException.class, () -> service.updateContact(desired));
        doThrow(new AuthenticationException()).when(identity).get();
        assertThrows(AuthenticationException.class, service::profile);
        verifyNoInteractions(connections, customers);
    }

    @ParameterizedTest
    @MethodSource("invalidDetails")
    void invalidContactsAreRejectedBeforeJdbc(ContactDetails details) {
        assertThrows(ValidationException.class, () -> service.updateContact(details));
        verifyNoInteractions(connections, customers);
    }

    static Stream<ContactDetails> invalidDetails() {
        return Stream.of(null,
                new ContactDetails("bad", "2025550123", null),
                new ContactDetails(null, "2025550123", null),
                new ContactDetails("ok@example.invalid", "123", null),
                new ContactDetails("ok@example.invalid", "202-555-0123", null),
                new ContactDetails("ok@example.invalid", "2025550123", "x".repeat(256)),
                new ContactDetails("ok@example.invalid", "2025550123", "Line\nbreak"));
    }

    @Test
    void missingOrForeignCustomerCannotBeUpdated() throws Exception {
        when(customers.findProfile(connection, 7, true)).thenReturn(Optional.empty());
        assertThrows(AuthenticationException.class, () -> service.updateContact(desired));
        when(customers.findProfile(connection, 7, true))
                .thenReturn(Optional.of(profile(99, CustomerStatus.ACTIVE, desired)));
        assertThrows(AuthenticationException.class, () -> service.updateContact(desired));
        verify(customers, never()).updateContact(any(), anyLong(), any());
        verify(connection, times(2)).rollback();
    }

    @Test
    void inactiveCustomerCannotBeUpdated() throws Exception {
        when(customers.findProfile(connection, 7, true))
                .thenReturn(Optional.of(profile(7, CustomerStatus.CLOSED, desired)));
        assertThrows(AuthenticationException.class, () -> service.updateContact(desired));
        verify(customers, never()).updateContact(any(), anyLong(), any());
        verify(connection).rollback();
    }

    @Test
    void missingProfileReadDeniesAccessAndClosesConnection() throws Exception {
        when(customers.findProfile(connection, 7, false)).thenReturn(Optional.empty());
        assertThrows(AuthenticationException.class, service::profile);
        verify(connection).close();
    }

    @Test
    void unchangedDetailsSucceedWithoutAnUpdateOrReadBack() throws Exception {
        var unchanged = new ContactDetails(before.email(), before.phone(), before.address());
        assertSame(before, service.updateContact(unchanged));
        verify(customers, never()).updateContact(any(), anyLong(), any());
        verify(customers, never()).findProfile(connection, 7, false);
        verify(connection).commit();
    }

    @Test
    void emptyAddressExplicitlyClearsIt() throws Exception {
        ContactDetails cleared = new ContactDetails(desired.email(), desired.phone(), null);
        when(customers.findProfile(connection, 7, false))
                .thenReturn(Optional.of(profile(7, CustomerStatus.ACTIVE, cleared)));
        service.updateContact(new ContactDetails(desired.email(), desired.phone(), ""));
        verify(customers).updateContact(connection, 7, cleared);
    }

    @Test
    void duplicateEmailRollsBackWithoutLeakingDriverCleanupDetails() throws Exception {
        doThrow(new SQLException("PRIVATE duplicate contact", "23000", 1062))
                .when(customers).updateContact(connection, 7, desired);
        doThrow(new SQLException("PRIVATE cleanup")).when(connection).close();
        ValidationException failure = assertThrows(ValidationException.class,
                () -> service.updateContact(desired));
        assertEquals("That email is already registered.", failure.getMessage());
        assertEquals(0, failure.getSuppressed().length);
        assertNull(failure.getCause());
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test
    void otherIntegrityFailuresAreNotMislabeledAsDuplicateEmail() throws Exception {
        doThrow(new SQLException("PRIVATE foreign key", "23000", 1452))
                .when(customers).updateContact(connection, 7, desired);
        PersistenceException failure = assertThrows(PersistenceException.class,
                () -> service.updateContact(desired));
        assertFalse(failure.getCause().toString().contains("PRIVATE"));
        verify(connection).rollback();
    }

    @Test
    void unexpectedReadBackRollsBackInsteadOfAcceptingAlteredFields() throws Exception {
        when(customers.findProfile(connection, 7, false)).thenReturn(Optional.of(before));
        assertThrows(PersistenceException.class, () -> service.updateContact(desired));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test
    void commitFailureIsSanitizedAndNeverRetried() throws Exception {
        doThrow(new SQLException("PRIVATE commit")).when(connection).commit();
        doThrow(new SQLException("PRIVATE rollback")).when(connection).rollback();
        PersistenceException failure = assertThrows(PersistenceException.class,
                () -> service.updateContact(desired));
        assertFalse(failure.getCause().toString().contains("PRIVATE"));
        assertEquals(0, failure.getSuppressed().length);
        verify(connections).getConnection();
        verify(customers).updateContact(connection, 7, desired);
    }

    @Test
    void connectionCloseFailureAfterCommitDoesNotRepeatTheUpdate() throws Exception {
        doThrow(new SQLException("PRIVATE close")).when(connection).close();
        assertThrows(PersistenceException.class, () -> service.updateContact(desired));
        verify(connection).commit();
        verify(connections).getConnection();
        verify(customers).updateContact(connection, 7, desired);
    }

    @Test
    void inputCannotSupplyIdentityOrSecurityFieldsAndRedactsContactRendering() {
        assertEquals(List.of("email", "phone", "address"), Arrays.stream(
                ContactDetails.class.getRecordComponents()).map(component -> component.getName()).toList());
        assertEquals("ContactDetails[REDACTED]", desired.toString());
    }

    private static Customer profile(long id, CustomerStatus status, ContactDetails contact) {
        return new Customer(id, "C-fixture", "Test", null, "Person", null,
                contact.address(), contact.email(), contact.phone(), "tester", status);
    }
}
