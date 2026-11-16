package com.example.banking.dao.jdbc;

import com.example.banking.dao.TransactionHistoryDAO;
import com.example.banking.model.*;
import java.sql.*;
import java.util.*;

/** Reads in a UTC session; never selects counterparty data or either balance snapshot. */
public final class JdbcTransactionHistoryDAO implements TransactionHistoryDAO {
    private final int timeout;

    public JdbcTransactionHistoryDAO(int timeout) {
        if (timeout <= 0) throw new IllegalArgumentException("A positive query timeout is required.");
        this.timeout = timeout;
    }

    @Override
    public List<HistoryEntry> findOwned(Connection connection, long owner, long accountId, HistoryQuery query)
            throws SQLException {
        if (owner <= 0 || accountId <= 0 || query == null)
            throw new IllegalArgumentException("Valid history ownership and query are required.");
        StringBuilder sql = new StringBuilder("""
                SELECT t.transaction_id, t.reference_number, t.transaction_type,
                       t.amount, t.status, t.created_at,
                       t.source_account_id = a.account_id AS selected_is_source,
                       t.destination_account_id = a.account_id AS selected_is_destination
                FROM accounts AS a
                JOIN transactions AS t
                  ON (t.source_account_id = a.account_id OR t.destination_account_id = a.account_id)
                WHERE a.customer_id = ? AND a.account_id = ?
                """);
        // Explicit DATETIME filter constants support calendar bounds outside the
        // TIMESTAMP storage window without attempting to insert/coerce a timestamp.
        if (query.start() != null) sql.append(" AND t.created_at >= CAST(? AS DATETIME)");
        if (query.end() != null) sql.append(" AND t.created_at < CAST(? AS DATETIME)");
        if (query.type() != null) sql.append(" AND t.transaction_type = ?");
        sql.append(" ORDER BY t.created_at DESC, t.transaction_id DESC LIMIT ? OFFSET ?");
        try (var statement = connection.prepareStatement(sql.toString())) {
            statement.setQueryTimeout(timeout);
            statement.setLong(1, owner);
            statement.setLong(2, accountId);
            int index = 3;
            if (query.start() != null) statement.setString(index++, query.start() + " 00:00:00");
            if (query.end() != null) statement.setString(index++, query.end() + " 00:00:00");
            if (query.type() != null) statement.setString(index++, query.type().name());
            statement.setInt(index++, query.fetchLimit());
            statement.setInt(index, query.offset());
            try (var rows = statement.executeQuery()) {
                List<HistoryEntry> entries = new ArrayList<>();
                while (rows.next()) {
                    if (entries.size() >= query.fetchLimit()) throw new SQLException("History limit was not respected.");
                    entries.add(map(rows));
                }
                return List.copyOf(entries);
            }
        }
    }

    private static HistoryEntry map(ResultSet row) throws SQLException {
        try {
            boolean source = row.getBoolean("selected_is_source");
            boolean destination = row.getBoolean("selected_is_destination");
            if (source == destination) throw new IllegalArgumentException("Ambiguous direction.");
            var timestamp = row.getTimestamp("created_at", Calendar.getInstance(TimeZone.getTimeZone("UTC")));
            return new HistoryEntry(row.getLong("transaction_id"),
                    new TransactionReference(row.getString("reference_number")),
                    TransactionType.valueOf(row.getString("transaction_type")),
                    new Money(row.getBigDecimal("amount")),
                    source ? HistoryEntry.Direction.DEBIT : HistoryEntry.Direction.CREDIT,
                    TransactionStatus.valueOf(row.getString("status")), timestamp.toInstant());
        } catch (RuntimeException invalid) {
            throw new SQLException("Stored history entry is invalid.");
        }
    }
}

