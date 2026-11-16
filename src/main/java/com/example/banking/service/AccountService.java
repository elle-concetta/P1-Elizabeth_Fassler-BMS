package com.example.banking.service;

import com.example.banking.dao.AccountDAO;
import com.example.banking.dao.CustomerDAO;
import com.example.banking.exception.*;
import com.example.banking.model.*;
import com.example.banking.util.ConnectionFactory;
import com.example.banking.validation.AccountValidator;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Session-owned opening and enquiry. No deposits, lending, or automatic schema changes. */
public final class AccountService {
    private final ConnectionFactory connections;
    private final AccountDAO accounts;
    private final CustomerDAO customers;
    private final Supplier<Customer> identity;
    private final Supplier<AccountNumber> numbers;
    private final Clock clock;
    private final int timeout;
    private boolean openingUnconfirmed;

    public AccountService(ConnectionFactory connections, AccountDAO accounts, CustomerDAO customers,
                          Supplier<Customer> identity, Supplier<AccountNumber> numbers,
                          Clock clock, int timeout) {
        this.connections = Objects.requireNonNull(connections);
        this.accounts = Objects.requireNonNull(accounts);
        this.customers = Objects.requireNonNull(customers);
        this.identity = Objects.requireNonNull(identity);
        this.numbers = Objects.requireNonNull(numbers);
        this.clock = Objects.requireNonNull(clock);
        if (timeout <= 0) throw new IllegalArgumentException("Query timeout must be positive.");
        this.timeout = timeout;
    }

    public Account open(AccountType type) {
        long owner = identity.get().customerId(); // checks expiry/pending evidence after form input
        AccountValidator.openingType(type);
        if (openingUnconfirmed) throw new AccountOpeningUnconfirmedException();
        LocalDate opened = LocalDate.now(clock);
        if (opened.getYear() < 1000 || opened.getYear() > 9999)
            throw new ValidationException("Opening date is outside supported storage bounds.");
        boolean insertAttempted = false;
        try (Connection connection = connections.getConnection()) {
            try {
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                connection.setAutoCommit(false);
                try (var statement = connection.createStatement()) {
                    statement.setQueryTimeout(timeout);
                    statement.execute("SET SESSION innodb_lock_wait_timeout = " + timeout);
                }
                requireActive(connection, owner, true);
                Account created = null;
                for (int attempt = 0; attempt < 3; attempt++) {
                    AccountNumber number = Objects.requireNonNull(numbers.get());
                    long id;
                    insertAttempted = true;
                    try {
                        id = accounts.insert(connection, owner, number, type, opened);
                    } catch (AccountDAO.NumberCollision rejected) {
                        // MySQL duplicate-key statement rejection is known not to insert.
                        // Never retry a commit, deadlock, connection error, or uncertain result.
                        insertAttempted = false;
                        continue;
                    }
                    created = accounts.findOwned(connection, owner, id)
                            .orElseThrow(() -> new SQLException("Inserted account was not confirmed."));
                    if (created.customerId() != owner || created.accountId() != id
                            || !created.number().equals(number) || created.type() != type
                            || !created.balance().equals(Money.ZERO)
                            || !created.minimumBalance().equals(Money.ZERO)
                            || created.interestRate().signum() != 0
                            || !created.openingDate().equals(opened)
                            || created.status() != AccountStatus.ACTIVE)
                        throw new SQLException("Inserted account did not match the request.");
                    break;
                }
                if (created == null)
                    throw new ValidationException("Could not allocate a unique account number. No account was opened.");
                connection.commit();
                return created;
            } catch (SQLException | RuntimeException | Error failure) {
                rollback(connection);
                throw failure;
            }
        } catch (AuthenticationException failure) {
            throw new AuthenticationException();
        } catch (ValidationException failure) {
            throw new ValidationException("Could not allocate a unique account number. No account was opened.");
        } catch (SQLException | RuntimeException failure) {
            if (insertAttempted) {
                openingUnconfirmed = true;
                throw new AccountOpeningUnconfirmedException();
            }
            throw new PersistenceException(null);
        }
    }

    public List<Account> list() {
        long owner = identity.get().customerId();
        try (Connection connection = connections.getConnection()) {
            connection.setReadOnly(true);
            requireActive(connection, owner, false);
            List<Account> found = List.copyOf(accounts.findOwned(connection, owner));
            if (found.stream().anyMatch(account -> account.customerId() != owner))
                throw new SQLException("Account ownership did not match.");
            return found;
        } catch (AuthenticationException failure) {
            throw new AuthenticationException();
        } catch (SQLException | RuntimeException failure) {
            throw new PersistenceException(null);
        }
    }

    public Account details(long id) {
        long owner = identity.get().customerId();
        AccountValidator.accountId(id);
        try (Connection connection = connections.getConnection()) {
            connection.setReadOnly(true);
            requireActive(connection, owner, false);
            Account account = accounts.findOwned(connection, owner, id).orElse(null);
            if (account == null || account.customerId() != owner || account.accountId() != id)
                throw new ValidationException("Account not found.");
            return account;
        } catch (AuthenticationException failure) {
            throw new AuthenticationException();
        } catch (ValidationException failure) {
            throw new ValidationException("Account not found.");
        } catch (SQLException | RuntimeException failure) {
            throw new PersistenceException(null);
        }
    }

    private void requireActive(Connection connection, long owner, boolean lock) throws SQLException {
        Customer customer = customers.findProfile(connection, owner, lock)
                .orElseThrow(AuthenticationException::new);
        if (customer.customerId() != owner || customer.status() != CustomerStatus.ACTIVE)
            throw new AuthenticationException();
    }

    private static void rollback(Connection connection) {
        try { connection.rollback(); } catch (SQLException ignored) { /* never replay */ }
    }
}
