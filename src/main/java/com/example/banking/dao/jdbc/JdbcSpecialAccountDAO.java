package com.example.banking.dao.jdbc;

import com.example.banking.dao.SpecialAccountDAO;
import com.example.banking.model.*;
import java.sql.*;
import java.time.LocalDate;

/** Additive account/details inserts and guarded principal/status updates; no DDL. */
public final class JdbcSpecialAccountDAO implements SpecialAccountDAO {
    private final int timeout;
    public JdbcSpecialAccountDAO(int timeout) {
        if (timeout <= 0) throw new IllegalArgumentException("Positive timeout required.");
        this.timeout = timeout;
    }
    @Override public long insert(Connection c, long owner, AccountNumber number, AccountType type,
                                 Money balance, LocalDate opened) throws SQLException {
        if (owner <= 0 || number == null || balance == null || balance.compareTo(Money.ZERO) < 0
                || opened == null || (type != AccountType.DEPOSIT && type != AccountType.LOAN))
            throw new IllegalArgumentException("Invalid dedicated account.");
        try (var q = c.prepareStatement("""
                INSERT INTO accounts(customer_id, account_number, account_type, balance,
                    interest_rate, minimum_balance, opening_date, status)
                VALUES (?, ?, ?, ?, 0.00, 0.00, ?, 'ACTIVE')
                """, Statement.RETURN_GENERATED_KEYS)) {
            q.setQueryTimeout(timeout); q.setLong(1, owner); q.setString(2, number.value());
            q.setString(3, type.name()); q.setBigDecimal(4, balance.toBigDecimal());
            q.setObject(5, opened, Types.DATE); one(q.executeUpdate());
            try (var keys = q.getGeneratedKeys()) {
                if (!keys.next() || keys.getLong(1) <= 0) throw new SQLException("Missing dedicated identity.");
                return keys.getLong(1);
            }
        }
    }
    @Override public void depositDetails(Connection c, long id, Money principal, int months, LocalDate maturity) throws SQLException {
        try (var q = c.prepareStatement("""
                INSERT INTO deposit_details(account_id, principal, term_months, interest_rate, maturity_date)
                VALUES (?, ?, ?, 0.00, ?)
                """)) {
            q.setQueryTimeout(timeout); q.setLong(1, id); q.setBigDecimal(2, principal.toBigDecimal());
            q.setInt(3, months); q.setObject(4, maturity, Types.DATE); one(q.executeUpdate());
        }
    }
    @Override public void loanDetails(Connection c, long id, Money principal, int months, LocalDate start) throws SQLException {
        try (var q = c.prepareStatement("""
                INSERT INTO loan_details(account_id, principal_amount, annual_rate, tenure_months, start_date)
                VALUES (?, ?, 0.00, ?, ?)
                """)) {
            q.setQueryTimeout(timeout); q.setLong(1, id); q.setBigDecimal(2, principal.toBigDecimal());
            q.setInt(3, months); q.setObject(4, start, Types.DATE); one(q.executeUpdate());
        }
    }
    @Override public DepositTerms lockDeposit(Connection c, long id) throws SQLException {
        try (var q = c.prepareStatement("SELECT principal, maturity_date FROM deposit_details WHERE account_id = ? FOR UPDATE")) {
            q.setQueryTimeout(timeout); q.setLong(1, id);
            try (var r = q.executeQuery()) {
                if (!r.next()) throw new SQLException("Deposit details missing.");
                return new DepositTerms(new Money(r.getBigDecimal("principal")), r.getObject("maturity_date", LocalDate.class));
            }
        }
    }
    @Override public Money lockLoan(Connection c, long id) throws SQLException {
        try (var q = c.prepareStatement("SELECT principal_amount FROM loan_details WHERE account_id = ? FOR UPDATE")) {
            q.setQueryTimeout(timeout); q.setLong(1, id);
            try (var r = q.executeQuery()) {
                if (!r.next()) throw new SQLException("Loan details missing.");
                return new Money(r.getBigDecimal("principal_amount"));
            }
        }
    }
    @Override public void update(Connection c, Account before, Money balance, AccountStatus status) throws SQLException {
        if ((before.type() != AccountType.DEPOSIT && before.type() != AccountType.LOAN)
                || balance.compareTo(Money.ZERO) < 0 || status == null)
            throw new IllegalArgumentException("Invalid dedicated update.");
        try (var q = c.prepareStatement("""
                UPDATE accounts SET balance = ?, status = ?
                WHERE customer_id = ? AND account_id = ? AND account_type = ? AND status = ? AND balance = ?
                """)) {
            q.setQueryTimeout(timeout); q.setBigDecimal(1, balance.toBigDecimal()); q.setString(2, status.name());
            q.setLong(3, before.customerId()); q.setLong(4, before.accountId()); q.setString(5, before.type().name());
            q.setString(6, before.status().name()); q.setBigDecimal(7, before.balance().toBigDecimal());
            one(q.executeUpdate());
        }
    }
    @Override public void matureOwned(Connection c, long owner, LocalDate today) throws SQLException {
        if (owner <= 0 || today == null) throw new IllegalArgumentException("Maturity ownership/date required.");
        try (var q = c.prepareStatement("""
                UPDATE accounts AS a JOIN deposit_details AS d ON d.account_id = a.account_id
                JOIN customers AS customer ON customer.customer_id = a.customer_id AND customer.status = 'ACTIVE'
                SET a.status = 'MATURED'
                WHERE a.customer_id = ? AND a.account_type = 'DEPOSIT' AND a.status = 'ACTIVE'
                  AND d.maturity_date <= ?
                """)) {
            q.setQueryTimeout(timeout); q.setLong(1, owner); q.setObject(2, today, Types.DATE); q.executeUpdate();
        }
    }
    private static void one(int count) throws SQLException {
        if (count != 1) throw new SQLException("Dedicated write count was not one.");
    }
}

