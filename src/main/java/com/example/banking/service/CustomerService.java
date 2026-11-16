package com.example.banking.service;

import com.example.banking.dao.CustomerDAO;
import com.example.banking.exception.AuthenticationException;
import com.example.banking.exception.PersistenceException;
import com.example.banking.exception.ValidationException;
import com.example.banking.model.ContactDetails;
import com.example.banking.model.Customer;
import com.example.banking.model.CustomerStatus;
import com.example.banking.util.ConnectionFactory;
import com.example.banking.validation.CustomerValidator;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;

/** Session-owned profile operations. No caller-supplied customer ID is accepted. */
public final class CustomerService {
    private final ConnectionFactory connections;
    private final CustomerDAO customers;
    private final Supplier<Customer> authenticatedCustomer;
    private final int timeout;

    public CustomerService(ConnectionFactory connections, CustomerDAO customers,
                           Supplier<Customer> authenticatedCustomer, int timeout) {
        this.connections = Objects.requireNonNull(connections);
        this.customers = Objects.requireNonNull(customers);
        this.authenticatedCustomer = Objects.requireNonNull(authenticatedCustomer);
        if (timeout <= 0) throw new IllegalArgumentException("Query timeout must be positive.");
        this.timeout = timeout;
    }

    public Customer profile() {
        long id = authenticatedCustomer.get().customerId();
        try (Connection connection = connections.getConnection()) {
            connection.setReadOnly(true);
            return ownedActiveProfile(connection, id, false);
        } catch (SQLException failure) {
            throw safeFailure();
        } catch (AuthenticationException failure) {
            throw new AuthenticationException();
        }
    }

    public Customer updateContact(ContactDetails supplied) {
        // This callback checks expiry and pending audit failures at action time,
        // including after the user has spent time answering edit prompts.
        long id = authenticatedCustomer.get().customerId();
        CustomerValidator.validateContact(supplied);
        String address = supplied.address() == null || supplied.address().isEmpty()
                ? null : supplied.address();
        ContactDetails desired = new ContactDetails(supplied.email(), supplied.phone(), address);
        try (Connection connection = connections.getConnection()) {
            try {
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                connection.setAutoCommit(false);
                try (var statement = connection.createStatement()) {
                    statement.setQueryTimeout(timeout);
                    statement.execute("SET SESSION innodb_lock_wait_timeout = " + timeout);
                }
                Customer before = ownedActiveProfile(connection, id, true);
                Customer result = before;
                if (!sameContact(before, desired)) {
                    try {
                        customers.updateContact(connection, id, desired);
                    } catch (SQLException failure) {
                        if (failure.getErrorCode() == 1062) {
                            throw new ValidationException("That email is already registered.");
                        }
                        throw failure;
                    }
                    result = ownedActiveProfile(connection, id, false);
                    if (!sameContact(result, desired)) throw safeFailure();
                }
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | Error failure) {
                rollback(connection);
                throw failure;
            }
        } catch (ValidationException failure) {
            // Drop any suppressed driver cleanup exception attached during close.
            throw new ValidationException("That email is already registered.");
        } catch (AuthenticationException failure) {
            throw new AuthenticationException();
        } catch (PersistenceException failure) {
            throw safeFailure();
        } catch (SQLException failure) {
            // Driver and cleanup errors can contain credentials or contact data.
            // A failed commit/close can leave an uncertain result: never auto-retry.
            throw safeFailure();
        }
    }

    private Customer ownedActiveProfile(Connection connection, long id, boolean lock)
            throws SQLException {
        Customer profile = customers.findProfile(connection, id, lock)
                .orElseThrow(AuthenticationException::new);
        if (profile.customerId() != id || profile.status() != CustomerStatus.ACTIVE) {
            throw new AuthenticationException();
        }
        return profile;
    }

    private static boolean sameContact(Customer profile, ContactDetails details) {
        return Objects.equals(profile.email(), details.email())
                && Objects.equals(profile.phone(), details.phone())
                && Objects.equals(profile.address(), details.address());
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Do not replace or disclose the original failure.
        }
    }

    private static PersistenceException safeFailure() {
        return new PersistenceException(new SQLException(
                "Profile operation was not confirmed. Do not retry automatically."));
    }
}
