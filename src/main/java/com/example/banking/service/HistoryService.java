package com.example.banking.service;

import com.example.banking.dao.*;
import com.example.banking.exception.*;
import com.example.banking.model.*;
import com.example.banking.util.ConnectionFactory;
import com.example.banking.validation.AccountValidator;
import java.sql.*;
import java.util.*;
import java.util.function.Supplier;

/** Read-only snapshot per page. New inserts between requests can shift offset pages. */
public final class HistoryService {
    private final ConnectionFactory connections;
    private final AccountDAO accounts;
    private final CustomerDAO customers;
    private final TransactionHistoryDAO history;
    private final Supplier<Customer> identity;
    private final int timeout;

    public HistoryService(ConnectionFactory connections, AccountDAO accounts, CustomerDAO customers,
                          TransactionHistoryDAO history, Supplier<Customer> identity, int timeout) {
        this.connections = Objects.requireNonNull(connections);
        this.accounts = Objects.requireNonNull(accounts);
        this.customers = Objects.requireNonNull(customers);
        this.history = Objects.requireNonNull(history);
        this.identity = Objects.requireNonNull(identity);
        if (timeout <= 0) throw new IllegalArgumentException("A positive history timeout is required.");
        this.timeout = timeout;
    }

    public HistoryPage page(long id, HistoryQuery query) {
        long owner = identity.get().customerId(); // session/pending guard after form entry, before SQL
        AccountValidator.accountId(id);
        if (query == null) throw new ValidationException("History filters are required.");
        try (Connection connection = connections.getConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                try (var setup = connection.createStatement()) {
                    setup.setQueryTimeout(timeout);
                    setup.execute("SET SESSION time_zone = '+00:00'");
                }
                Customer profile = customers.findProfile(connection, owner, false).orElse(null);
                if (profile == null || profile.customerId() != owner || profile.status() != CustomerStatus.ACTIVE)
                    throw new AuthenticationException();
                Account account = accounts.findOwned(connection, owner, id).orElse(null);
                if (account == null || account.customerId() != owner || account.accountId() != id)
                    throw new ValidationException("Account not found.");
                List<HistoryEntry> found = List.copyOf(history.findOwned(connection, owner, id, query));
                if (found.size() > query.fetchLimit()) throw new SQLException("History page limit did not match.");
                Set<Long> ids = new HashSet<>();
                HistoryEntry previous = null;
                for (HistoryEntry entry : found) {
                    if (!ids.add(entry.transactionId()) || (previous != null &&
                            (entry.createdAt().isAfter(previous.createdAt())
                                    || (entry.createdAt().equals(previous.createdAt())
                                    && entry.transactionId() >= previous.transactionId()))))
                        throw new SQLException("History order did not match.");
                    previous = entry;
                }
                return new HistoryPage(id, account.number(), query,
                        found.subList(0, Math.min(query.pageSize(), found.size())), found.size() > query.pageSize());
            } finally {
                connection.rollback(); // end this read-only snapshot; no financial writes/commit
            }
        } catch (AuthenticationException safe) {
            throw new AuthenticationException();
        } catch (ValidationException safe) {
            throw new ValidationException(safe.getMessage());
        } catch (SQLException | RuntimeException failure) {
            throw new PersistenceException(null); // never expose driver causes/suppressed graphs
        }
    }
}

