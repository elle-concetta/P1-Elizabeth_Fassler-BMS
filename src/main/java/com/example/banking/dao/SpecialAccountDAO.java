package com.example.banking.dao;

import com.example.banking.model.*;
import java.sql.*;
import java.time.LocalDate;

/** Caller owns all locks, transaction, cleanup and audit delivery. Rates are zero metadata. */
public interface SpecialAccountDAO {
    record DepositTerms(Money principal, LocalDate maturity) {}
    long insert(Connection c, long owner, AccountNumber number, AccountType type, Money balance, LocalDate opened) throws SQLException;
    void depositDetails(Connection c, long id, Money principal, int months, LocalDate maturity) throws SQLException;
    void loanDetails(Connection c, long id, Money principal, int months, LocalDate start) throws SQLException;
    DepositTerms lockDeposit(Connection c, long id) throws SQLException;
    Money lockLoan(Connection c, long id) throws SQLException;
    void update(Connection c, Account before, Money balance, AccountStatus status) throws SQLException;
    void matureOwned(Connection c, long owner, LocalDate today) throws SQLException;
}

