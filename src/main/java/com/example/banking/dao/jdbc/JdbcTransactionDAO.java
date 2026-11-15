package com.example.banking.dao.jdbc;

import com.example.banking.dao.TransactionDAO;
import com.example.banking.model.BankTransaction;
import com.example.banking.validation.TransactionValidator;
import java.sql.*;
import java.util.Calendar;
import java.util.TimeZone;

/** Writes only a validated deposit shape; primary writer session must be UTC. */
public final class JdbcTransactionDAO implements TransactionDAO {
    private final int timeout;
    public JdbcTransactionDAO(int timeout) {
        if (timeout <= 0) throw new IllegalArgumentException("Query timeout must be positive.");
        this.timeout = timeout;
    }

    @Override
    public void insertDeposit(Connection connection, BankTransaction tx) throws SQLException {
        if (tx == null || !TransactionValidator.supportedTime(tx.createdAt()))
            throw new IllegalArgumentException("Ledger time must fit the whole-second TIMESTAMP boundary; no write attempted.");
        try (var query = connection.prepareStatement("""
                INSERT INTO transactions (reference_number, source_account_id, destination_account_id,
                    transaction_type, amount, status, description, source_balance_before,
                    dest_balance_before, source_balance_after, dest_balance_after, created_at)
                VALUES (?, NULL, ?, 'DEPOSIT', ?, 'SUCCESS', NULL, NULL, ?, NULL, ?, ?)
                """)) {
            query.setQueryTimeout(timeout);
            query.setString(1, tx.reference().value());
            query.setLong(2, tx.destinationId());
            query.setBigDecimal(3, tx.amount().toBigDecimal());
            query.setBigDecimal(4, tx.balanceBefore().toBigDecimal());
            query.setBigDecimal(5, tx.balanceAfter().toBigDecimal());
            query.setTimestamp(6, Timestamp.from(tx.createdAt()),
                    Calendar.getInstance(TimeZone.getTimeZone("UTC")));
            if (query.executeUpdate() != 1) throw new SQLException("Ledger insert was not confirmed.");
        }
    }
}
