package com.example.banking.dao;

import com.example.banking.model.Account;
import com.example.banking.model.AccountNumber;
import com.example.banking.model.AccountType;
import com.example.banking.model.Money;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Every read requires the session owner; caller owns transaction and connection. */
public interface AccountDAO {
    /** Only an INSERT rejected by MySQL's account-number UNIQUE constraint. */
    final class NumberCollision extends SQLException {
        public NumberCollision() { super("Generated account number was already allocated."); }
    }

    long insert(Connection connection, long owner, AccountNumber number, AccountType type,
                LocalDate opened) throws SQLException;
    List<Account> findOwned(Connection connection, long owner) throws SQLException;
    Optional<Account> findOwned(Connection connection, long owner, long accountId) throws SQLException;
    Optional<Account> lockOwned(Connection connection, long owner, long accountId) throws SQLException;
    void updateBalance(Connection connection, long owner, long accountId, Money before, Money after)
            throws SQLException;
}
