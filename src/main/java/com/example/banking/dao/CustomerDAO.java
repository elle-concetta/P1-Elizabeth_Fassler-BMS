package com.example.banking.dao;

import com.example.banking.model.Customer;
import com.example.banking.model.PasswordHash;
import com.example.banking.model.Registration;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/** Caller owns transactions; password data is separate from public profiles. */
public interface CustomerDAO {
    record AuthenticationRow(Customer customer, PasswordHash hash,
                             int failedCount, Instant lockedUntil) {
        @Override
        public String toString() {
            return "AuthenticationRow[REDACTED]";
        }
    }

    boolean identityExists(Connection connection, String username, String email) throws SQLException;
    Customer insert(Connection connection, Registration input, String code, PasswordHash hash)
            throws SQLException;
    Optional<AuthenticationRow> lockByUsername(Connection connection, String username) throws SQLException;
    void updateLoginState(Connection connection, long id, int failures, Instant lockedUntil)
            throws SQLException;
}
