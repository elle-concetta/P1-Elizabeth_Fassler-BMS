package com.example.banking.service;

import com.example.banking.dao.*;
import com.example.banking.exception.*;
import com.example.banking.model.*;
import com.example.banking.util.ConnectionFactory;
import com.example.banking.validation.TransactionValidator;
import java.sql.*;
import java.time.*;
import java.util.Objects;
import java.util.function.Supplier;

/** Principal-only term deposits and debt. All financial writes share the existing one-shot runner. */
public final class SpecialAccountService {
    private final TransactionService runner;
    private final AccountDAO accounts;
    private final SpecialAccountDAO details;
    private final TransactionDAO ledger;
    private final ConnectionFactory connections;
    private final Supplier<Customer> identity;
    private final Supplier<AccountNumber> numbers;
    private final Clock clock;
    private final Money maximum, minimum;

    public SpecialAccountService(TransactionService runner, AccountDAO accounts, SpecialAccountDAO details,
                                 TransactionDAO ledger, ConnectionFactory connections, Supplier<Customer> identity,
                                 Supplier<AccountNumber> numbers, Clock clock, Money maximum, Money minimum) {
        this.runner = Objects.requireNonNull(runner); this.accounts = Objects.requireNonNull(accounts);
        this.details = Objects.requireNonNull(details); this.ledger = Objects.requireNonNull(ledger);
        this.connections = Objects.requireNonNull(connections); this.identity = Objects.requireNonNull(identity);
        this.numbers = Objects.requireNonNull(numbers); this.clock = Objects.requireNonNull(clock);
        this.maximum = Objects.requireNonNull(maximum); this.minimum = Objects.requireNonNull(minimum);
        if (!minimum.isPositive() || minimum.compareTo(maximum) > 0)
            throw new IllegalArgumentException("Deposit minimum must be positive and within the transaction limit.");
    }

    public SpecialReceipt openDeposit(long fundingId, String supplied, int months) {
        return runner.dedicatedPrepared(FinancialAuditEvent.TRANSFER, captured -> {
            Money principal = amount(supplied);
            if (principal.compareTo(minimum) < 0) reject("BELOW_MINIMUM", "Principal is below the configured term Deposit minimum.");
            if (fundingId <= 0) reject("INVALID_ACCOUNT_ID", "Choose a positive owned funding ID.");
            LocalDate today = captured.atZone(clock.getZone()).toLocalDate();
            LocalDate maturity;
            try {
                if (months <= 0) throw new DateTimeException("term");
                maturity = today.plusMonths(months);
                if (maturity.getYear() > 9999) throw new DateTimeException("date");
            } catch (DateTimeException invalid) {
                throw new TransactionService.Rejected("INVALID_TERM", "Choose positive months with a supported maturity date.");
            }
            LocalDate fixedMaturity = maturity;
            return (c, owner, ref, time) -> {
                Account funding = locked(c, owner, fundingId);
                ordinary(funding); debit(funding, principal);
                long id = details.insert(c, owner, numbers.get(), AccountType.DEPOSIT, Money.ZERO, today);
                details.depositDetails(c, id, principal, months, fixedMaturity);
                Account deposit = read(c, owner, id);
                if (deposit.type() != AccountType.DEPOSIT || deposit.status() != AccountStatus.ACTIVE
                        || !deposit.balance().equals(Money.ZERO)) throw new SQLException("New Deposit did not match.");
                BankTransaction tx = BankTransaction.dedicated(ref, TransactionType.TRANSFER, funding, deposit, principal, time);
                accounts.updateBalance(c, owner, fundingId, tx.balanceBefore(), tx.balanceAfter());
                details.update(c, deposit, tx.destinationBalanceAfter(), AccountStatus.ACTIVE);
                ledger.insertTransfer(c, tx);
                checked(c, funding, tx.balanceAfter(), AccountStatus.ACTIVE);
                Account after = checked(c, deposit, principal, AccountStatus.ACTIVE);
                return new SpecialReceipt(ref, "Term Deposit opened. Principal is locked until " + fixedMaturity + ".", after, tx);
            };
        });
    }

    public SpecialReceipt settleDeposit(long depositId, long destinationId) {
        return runner.dedicated(FinancialAuditEvent.TRANSFER, (c, owner, ref, time) -> {
            Account[] pair = pair(c, owner, depositId, destinationId);
            Account deposit = pair[0], destination = pair[1];
            ordinary(destination);
            if (deposit.type() != AccountType.DEPOSIT
                    || (deposit.status() != AccountStatus.ACTIVE && deposit.status() != AccountStatus.MATURED))
                reject("DEPOSIT_UNAVAILABLE", "An owned unclosed term Deposit is required.");
            var terms = details.lockDeposit(c, depositId);
            if (terms == null || terms.principal() == null || !terms.principal().isPositive()
                    || terms.maturity() == null || !terms.principal().equals(deposit.balance()))
                throw new SQLException("Deposit principal/details did not match.");
            if (terms.maturity().isAfter(time.atZone(clock.getZone()).toLocalDate()))
                reject("NOT_MATURED", "Deposit has not reached its maturity date.");
            if (deposit.status() == AccountStatus.ACTIVE) {
                details.update(c, deposit, deposit.balance(), AccountStatus.MATURED);
                deposit = new DepositAccount(deposit.accountId(), owner, deposit.number(), deposit.balance(),
                        deposit.interestRate(), deposit.minimumBalance(), deposit.openingDate(), AccountStatus.MATURED);
            }
            BankTransaction tx;
            try { tx = BankTransaction.dedicated(ref, TransactionType.TRANSFER, deposit, destination, terms.principal(), time); }
            catch (IllegalArgumentException invalid) { throw new TransactionService.Rejected("BALANCE_OVERFLOW", "Settlement balance is unsupported."); }
            details.update(c, deposit, Money.ZERO, AccountStatus.CLOSED);
            accounts.updateBalance(c, owner, destinationId, tx.destinationBalanceBefore(), tx.destinationBalanceAfter());
            ledger.insertTransfer(c, tx);
            Account after = checked(c, deposit, Money.ZERO, AccountStatus.CLOSED);
            checked(c, destination, tx.destinationBalanceAfter(), AccountStatus.ACTIVE);
            return new SpecialReceipt(ref, "Matured Deposit settled once and closed. No interest was added.", after, tx);
        });
    }

    public SpecialReceipt openLoan(String supplied, int months, LocalDate start) {
        return runner.dedicatedPrepared(FinancialAuditEvent.LOAN_CREATION, captured -> {
            Money principal = amount(supplied);
            LocalDate opened = captured.atZone(clock.getZone()).toLocalDate();
            LocalDate date = start == null ? opened : start;
            if (months <= 0 || date.getYear() < 1000 || date.getYear() > 9999)
                reject("INVALID_LOAN_TERMS", "Positive tenure and a supported start date are required.");
            return (c, owner, ref, time) -> {
                long id = details.insert(c, owner, numbers.get(), AccountType.LOAN, principal, opened);
                details.loanDetails(c, id, principal, months, date);
                Account loan = read(c, owner, id);
                if (loan.type() != AccountType.LOAN || loan.status() != AccountStatus.ACTIVE || !loan.balance().equals(principal))
                    throw new SQLException("Loan read-back did not match.");
                return new SpecialReceipt(ref, "Loan created as outstanding debt only. No spendable cash was credited.", loan, null);
            };
        });
    }

    public SpecialReceipt repayLoan(long fundingId, long loanId, String supplied) {
        return runner.dedicatedPrepared(FinancialAuditEvent.LOAN_REPAYMENT, ignored -> {
            Money amount = amount(supplied);
            return (c, owner, ref, time) -> {
                Account[] pair = pair(c, owner, fundingId, loanId);
                Account funding = pair[0], loan = pair[1];
                ordinary(funding);
                if (loan.type() != AccountType.LOAN || loan.status() != AccountStatus.ACTIVE || !loan.balance().isPositive())
                    reject("LOAN_UNAVAILABLE", "An owned ACTIVE Loan with outstanding principal is required.");
                Money original = details.lockLoan(c, loanId);
                if (original == null || !original.isPositive() || loan.balance().compareTo(original) > 0)
                    throw new SQLException("Loan principal/details did not match.");
                if (amount.compareTo(loan.balance()) > 0) reject("OVERPAYMENT", "Repayment exceeds outstanding principal.");
                debit(funding, amount);
                BankTransaction tx = BankTransaction.dedicated(ref, TransactionType.LOAN_REPAYMENT, funding, loan, amount, time);
                AccountStatus status = tx.destinationBalanceAfter().equals(Money.ZERO) ? AccountStatus.CLOSED : AccountStatus.ACTIVE;
                accounts.updateBalance(c, owner, fundingId, tx.balanceBefore(), tx.balanceAfter());
                details.update(c, loan, tx.destinationBalanceAfter(), status);
                ledger.insertRepayment(c, tx);
                checked(c, funding, tx.balanceAfter(), AccountStatus.ACTIVE);
                Account after = checked(c, loan, tx.destinationBalanceAfter(), status);
                return new SpecialReceipt(ref, status == AccountStatus.CLOSED
                        ? "Loan repaid in full and closed." : "Loan repayment saved. Outstanding principal reduced.", after, tx);
            };
        });
    }

    /** Idempotent derived status only; never changes principal or moves money. */
    public void refreshMaturity() {
        refreshMaturity(identity.get().customerId());
    }

    /** Only AuthService calls this after verifying credentials, before publishing a session. */
    void refreshMaturity(long owner) {
        if (owner <= 0) throw new IllegalArgumentException("Authenticated owner required.");
        try (Connection c = connections.getConnection()) {
            c.setAutoCommit(false);
            try { details.matureOwned(c, owner, today()); c.commit(); }
            catch (SQLException | RuntimeException failure) { c.rollback(); throw failure; }
        } catch (SQLException | RuntimeException failure) { throw new PersistenceException(null); }
    }

    private Money amount(String value) {
        try { return TransactionValidator.amount(value, maximum); }
        catch (ValidationException invalid) { throw new TransactionService.Rejected("INVALID_AMOUNT", "Use a positive amount within the configured limit and at most two decimal places."); }
    }
    private LocalDate today() {
        LocalDate date = LocalDate.now(clock);
        if (date.getYear() < 1000 || date.getYear() > 9999) reject("UNSUPPORTED_DATE", "Business date is unsupported.");
        return date;
    }
    private Account locked(Connection c, long owner, long id) throws SQLException {
        if (id <= 0) reject("INVALID_ACCOUNT_ID", "Choose a positive owned account ID.");
        Account a = accounts.lockOwned(c, owner, id).orElse(null);
        if (a == null || a.accountId() != id || a.customerId() != owner) reject("ACCOUNT_NOT_FOUND", "Account not found.");
        return a;
    }
    private Account read(Connection c, long owner, long id) throws SQLException {
        Account a = accounts.findOwned(c, owner, id).orElse(null);
        if (a == null || a.accountId() != id || a.customerId() != owner) throw new SQLException("Owned read-back missing.");
        return a;
    }
    private Account checked(Connection c, Account before, Money balance, AccountStatus status) throws SQLException {
        Account a = read(c, before.customerId(), before.accountId());
        if (a.type() != before.type() || !a.number().equals(before.number())
                || !a.balance().equals(balance) || a.status() != status) throw new SQLException("Dedicated read-back did not match.");
        return a;
    }
    private Account[] pair(Connection c, long owner, long source, long destination) throws SQLException {
        if (source == destination) reject("SAME_ACCOUNT", "Choose two different owned accounts.");
        Account low = locked(c, owner, Math.min(source, destination));
        Account high = locked(c, owner, Math.max(source, destination));
        return source < destination ? new Account[]{low, high} : new Account[]{high, low};
    }
    private static void ordinary(Account a) {
        if (a.status() != AccountStatus.ACTIVE || (a.type() != AccountType.SAVINGS && a.type() != AccountType.CURRENT))
            reject("ACCOUNT_TYPE_NOT_ELIGIBLE", "An owned ACTIVE Savings or Current account is required.");
    }
    private static void debit(Account a, Money amount) {
        if (a.balance().compareTo(amount) < 0) reject("INSUFFICIENT_FUNDS", "Insufficient funding balance.");
        if (a.balance().subtract(amount).compareTo(a.minimumBalance()) < 0)
            reject("MINIMUM_BALANCE", "Funding would breach the stored minimum balance.");
    }
    private static void reject(String code, String message) { throw new TransactionService.Rejected(code, message); }
}

