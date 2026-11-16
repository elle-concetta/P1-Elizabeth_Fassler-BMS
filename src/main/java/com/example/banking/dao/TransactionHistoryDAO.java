package com.example.banking.dao;

import com.example.banking.model.HistoryEntry;
import com.example.banking.model.HistoryQuery;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Read-only, owner-scoped projection; caller owns the connection and snapshot. */
public interface TransactionHistoryDAO {
    List<HistoryEntry> findOwned(Connection connection, long owner, long accountId, HistoryQuery query)
            throws SQLException;
}

