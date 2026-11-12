package com.example.banking.dao.jdbc;

import com.example.banking.dao.CustomerDAO;
import com.example.banking.model.Customer;
import com.example.banking.model.CustomerStatus;
import com.example.banking.model.PasswordHash;
import com.example.banking.model.Registration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/** Prepared SQL only. Never commits, rolls back, or exposes hashes in profiles. */
public final class JdbcCustomerDAO implements CustomerDAO {
    private final int timeout;

    public JdbcCustomerDAO(int timeout) {
        if (timeout <= 0) throw new IllegalArgumentException("Query timeout must be positive.");
        this.timeout = timeout;
    }

    @Override
    public boolean identityExists(Connection connection, String username, String email) throws SQLException {
        try (var query = connection.prepareStatement(
                "SELECT 1 FROM customers WHERE username = ? OR email = ? LIMIT 1")) {
            query.setQueryTimeout(timeout);
            query.setString(1, username);
            query.setString(2, email);
            try (var rows = query.executeQuery()) {
                return rows.next();
            }
        }
    }

    @Override
    public Customer insert(Connection connection, Registration input, String code, PasswordHash hash)
            throws SQLException {
        try (var query = connection.prepareStatement("""
                INSERT INTO customers (customer_code, first_name, middle_initial,
                    last_name, date_of_birth, address, email, phone, username, password_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            query.setQueryTimeout(timeout);
            query.setString(1, code);
            query.setString(2, input.firstName());
            query.setString(3, emptyToNull(input.middleInitial()));
            query.setString(4, input.lastName());
            query.setObject(5, input.dateOfBirth(), Types.DATE);
            query.setString(6, emptyToNull(input.address()));
            query.setString(7, input.email());
            query.setString(8, emptyToNull(input.phone()));
            query.setString(9, input.username());
            query.setString(10, hash.value());
            if (query.executeUpdate() != 1) throw new SQLException("Unexpected insert count.");
            try (var keys = query.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Missing customer identity.");
                return new Customer(keys.getLong(1), code, input.firstName(), input.middleInitial(),
                        input.lastName(), input.dateOfBirth(), input.address(), input.email(),
                        input.phone(), input.username(), CustomerStatus.ACTIVE);
            }
        }
    }

    @Override
    public Optional<AuthenticationRow> lockByUsername(Connection connection, String username)
            throws SQLException {
        try (var query = connection.prepareStatement("""
                SELECT customer_id, customer_code, first_name, middle_initial, last_name,
                    date_of_birth, address, email, phone, username, status, password_hash,
                    failed_login_count, UNIX_TIMESTAMP(locked_until) AS locked_epoch
                FROM customers WHERE username = ? FOR UPDATE
                """)) {
            query.setQueryTimeout(timeout);
            query.setString(1, username);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                long epoch = rows.getLong("locked_epoch");
                Instant until = rows.wasNull() ? null : Instant.ofEpochSecond(epoch);
                return Optional.of(new AuthenticationRow(profile(rows),
                        new PasswordHash(rows.getString("password_hash")),
                        rows.getInt("failed_login_count"), until));
            }
        }
    }

    @Override
    public void updateLoginState(Connection connection, long id, int failures, Instant lockedUntil)
            throws SQLException {
        // FROM_UNIXTIME is interpreted in the current session, then stored as an
        // absolute TIMESTAMP. No ordinary connection's time zone is changed.
        try (var query = connection.prepareStatement("""
                UPDATE customers SET failed_login_count = ?, locked_until = FROM_UNIXTIME(?)
                WHERE customer_id = ?
                """)) {
            query.setQueryTimeout(timeout);
            query.setInt(1, failures);
            if (lockedUntil == null) query.setNull(2, Types.BIGINT);
            else query.setLong(2, lockedUntil.getEpochSecond());
            query.setLong(3, id);
            if (query.executeUpdate() != 1) throw new SQLException("Customer update was not confirmed.");
        }
    }

    private static Customer profile(ResultSet rows) throws SQLException {
        return new Customer(rows.getLong("customer_id"), rows.getString("customer_code"),
                rows.getString("first_name"), rows.getString("middle_initial"),
                rows.getString("last_name"), rows.getObject("date_of_birth", LocalDate.class),
                rows.getString("address"), rows.getString("email"), rows.getString("phone"),
                rows.getString("username"), CustomerStatus.valueOf(rows.getString("status")));
    }

    private static String emptyToNull(String text) {
        return text == null || text.isEmpty() ? null : text;
    }
}

