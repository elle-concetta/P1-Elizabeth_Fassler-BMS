package com.example.banking.dao;

import com.example.banking.model.SecurityAuditEvent;
import java.sql.Connection;
import java.sql.SQLException;

/** Caller owns the transaction: password replacement and success evidence commit together. */
public interface SecurityAuditDAO {
    void append(Connection connection, SecurityAuditEvent event) throws SQLException;
}

