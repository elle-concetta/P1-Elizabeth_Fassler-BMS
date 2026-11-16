package com.example.banking.dao;

import com.example.banking.model.BankTransaction;
import java.sql.Connection;
import java.sql.SQLException;

/** Ledger insertion shares the caller's money transaction; never commits independently. */
public interface TransactionDAO {
    void insertDeposit(Connection connection, BankTransaction transaction) throws SQLException;
    void insertWithdrawal(Connection connection, BankTransaction transaction) throws SQLException;
    void insertTransfer(Connection connection, BankTransaction transaction) throws SQLException;
    void insertRepayment(Connection connection, BankTransaction transaction) throws SQLException;
}
