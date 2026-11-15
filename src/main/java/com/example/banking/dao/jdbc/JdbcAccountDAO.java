package com.example.banking.dao.jdbc;

import com.example.banking.dao.AccountDAO;
import com.example.banking.model.*;
import com.example.banking.validation.AccountValidator;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Prepared owner-scoped SQL. Caller owns locks, transactions and ledger pairing. */
public final class JdbcAccountDAO implements AccountDAO {
    private static final String COLUMNS = """
            SELECT account_id, customer_id, account_number, account_type, balance,
                   interest_rate, minimum_balance, opening_date, status
            FROM accounts WHERE customer_id = ?
            """;
    private final int timeout;

    public JdbcAccountDAO(int timeout) {
        if (timeout <= 0) throw new IllegalArgumentException("Query timeout must be positive.");
        this.timeout = timeout;
    }

    @Override
    public long insert(Connection connection, long owner, AccountNumber number, AccountType type,
                       LocalDate opened) throws SQLException {
        AccountValidator.accountId(owner);
        AccountValidator.openingType(type);
        if (number == null || opened == null || opened.getYear() < 1000 || opened.getYear() > 9999)
            throw new IllegalArgumentException("Supported account number and opening date are required.");
        try (var query = connection.prepareStatement("""
                INSERT INTO accounts (customer_id, account_number, account_type, balance,
                    interest_rate, minimum_balance, opening_date, status)
                VALUES (?, ?, ?, 0.00, 0.00, 0.00, ?, 'ACTIVE')
                """, Statement.RETURN_GENERATED_KEYS)) {
            query.setQueryTimeout(timeout);
            query.setLong(1, owner);
            query.setString(2, number.value());
            query.setString(3, type.name());
            query.setObject(4, opened, Types.DATE);
            int count;
            try {
                count = query.executeUpdate();
            } catch (SQLException rejected) {
                if (rejected.getErrorCode() == 1062) throw new AccountDAO.NumberCollision();
                throw rejected;
            }
            if (count != 1) throw new SQLException("Account insert count was not one.");
            try (var keys = query.getGeneratedKeys()) {
                if (!keys.next() || keys.getLong(1) <= 0)
                    throw new SQLException("Missing account identity.");
                return keys.getLong(1);
            }
        }
    }

    @Override
    public List<Account> findOwned(Connection connection, long owner) throws SQLException {
        AccountValidator.accountId(owner);
        try (var query = connection.prepareStatement(COLUMNS + " ORDER BY account_id")) {
            query.setQueryTimeout(timeout);
            query.setLong(1, owner);
            try (var rows = query.executeQuery()) {
                List<Account> found = new ArrayList<>();
                while (rows.next()) found.add(map(rows));
                return List.copyOf(found);
            }
        }
    }

    @Override
    public Optional<Account> findOwned(Connection connection, long owner, long id) throws SQLException {
        return findOne(connection, owner, id, false);
    }

    @Override
    public Optional<Account> lockOwned(Connection connection, long owner, long id) throws SQLException {
        return findOne(connection, owner, id, true);
    }

    private Optional<Account> findOne(Connection connection, long owner, long id, boolean lock) throws SQLException {
        AccountValidator.accountId(owner);
        AccountValidator.accountId(id);
        try (var query = connection.prepareStatement(COLUMNS + " AND account_id = ?" + (lock ? " FOR UPDATE" : ""))) {
            query.setQueryTimeout(timeout);
            query.setLong(1, owner);
            query.setLong(2, id);
            try (var rows = query.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        }
    }

    @Override
    public void updateBalance(Connection connection, long owner, long id, Money before, Money after)
            throws SQLException {
        AccountValidator.accountId(owner);
        AccountValidator.accountId(id);
        if (before == null || after == null || before.compareTo(Money.ZERO) < 0
                || after.compareTo(Money.ZERO) < 0 || before.equals(after))
            throw new IllegalArgumentException("Distinct nonnegative balance snapshots are required.");
        try (var query = connection.prepareStatement("""
                UPDATE accounts SET balance = ?
                WHERE customer_id = ? AND account_id = ? AND balance = ?
                  AND status = 'ACTIVE' AND account_type IN ('SAVINGS', 'CURRENT')
                """)) {
            query.setQueryTimeout(timeout);
            query.setBigDecimal(1, after.toBigDecimal());
            query.setLong(2, owner);
            query.setLong(3, id);
            query.setBigDecimal(4, before.toBigDecimal());
            if (query.executeUpdate() != 1) throw new SQLException("Balance update was not confirmed.");
        }
    }

    private Account map(ResultSet rows) throws SQLException {
        try {
            long id = rows.getLong("account_id"), owner = rows.getLong("customer_id");
            AccountNumber number = new AccountNumber(rows.getString("account_number"));
            AccountType type = AccountType.valueOf(rows.getString("account_type"));
            Money balance = new Money(rows.getBigDecimal("balance"));
            BigDecimal rate = rows.getBigDecimal("interest_rate");
            Money minimum = new Money(rows.getBigDecimal("minimum_balance"));
            LocalDate opened = rows.getObject("opening_date", LocalDate.class);
            AccountStatus status = AccountStatus.valueOf(rows.getString("status"));
            return switch (type) {
                case SAVINGS -> new SavingsAccount(id, owner, number, balance, rate, minimum, opened, status);
                case CURRENT -> new CurrentAccount(id, owner, number, balance, rate, minimum, opened, status);
                case DEPOSIT -> new DepositAccount(id, owner, number, balance, rate, minimum, opened, status);
                case LOAN -> new LoanAccount(id, owner, number, balance, rate, minimum, opened, status);
            };
        } catch (IllegalArgumentException | NullPointerException invalid) {
            // Do not attach corrupt data or the original exception graph.
            throw new SQLException("Stored account could not be read safely.");
        }
    }
}
