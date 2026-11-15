package com.example.banking.service;

import com.example.banking.dao.*;
import com.example.banking.exception.*;
import com.example.banking.model.*;
import com.example.banking.util.ConnectionFactory;
import com.example.banking.validation.TransactionValidator;

import java.sql.*;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** One-shot deposits. Financial-attempt evidence is delivered AFTER primary cleanup. */
public final class TransactionService {
    public enum MoneyState { UNCHANGED, COMMITTED, UNCONFIRMED }
    public enum AuditState { NOT_ATTEMPTED, SAVED, REJECTED_NO_WRITE, SAVE_UNCERTAIN }

    /** Exact evidence retained only in memory until manual reconciliation; never log it. */
    public record PendingDeposit(TransactionAudit evidence, MoneyState moneyState, AuditState auditState) {
        public PendingDeposit {
            Objects.requireNonNull(evidence);
            Objects.requireNonNull(moneyState);
            Objects.requireNonNull(auditState);
        }
        @Override public String toString() {
            return "PendingDeposit[money=" + moneyState + ", audit=" + auditState + "]";
        }
    }

    private record Execution(BankTransaction receipt, MoneyState money, AuditOutcome outcome,
                             String failureCode, String message) {}

    private final ConnectionFactory connections;
    private final AccountDAO accounts;
    private final CustomerDAO customers;
    private final TransactionDAO ledger;
    private final TransactionAuditDAO audits;
    private final Supplier<Customer> identity;
    private final Supplier<TransactionReference> references;
    private final Clock clock;
    private final Money maximum;
    private final int timeout;
    private PendingDeposit pending;

    public TransactionService(ConnectionFactory connections, AccountDAO accounts, CustomerDAO customers,
                              TransactionDAO ledger, TransactionAuditDAO audits, Supplier<Customer> identity,
                              Supplier<TransactionReference> references, Clock clock, Money maximum, int timeout) {
        this.connections = Objects.requireNonNull(connections);
        this.accounts = Objects.requireNonNull(accounts);
        this.customers = Objects.requireNonNull(customers);
        this.ledger = Objects.requireNonNull(ledger);
        this.audits = Objects.requireNonNull(audits);
        this.identity = Objects.requireNonNull(identity);
        this.references = Objects.requireNonNull(references);
        this.clock = Objects.requireNonNull(clock);
        this.maximum = Objects.requireNonNull(maximum);
        if (!maximum.isPositive() || timeout <= 0)
            throw new IllegalArgumentException("Positive deposit limit and query timeout are required.");
        this.timeout = timeout;
    }

    public Optional<PendingDeposit> pending() { return Optional.ofNullable(pending); }

    public DepositException pendingFailure() {
        if (pending == null) throw new IllegalStateException("No pending deposit.");
        String message = switch (pending.moneyState()) {
            case COMMITTED -> "Deposit committed, but audit storage is not confirmed.";
            case UNCHANGED -> "Balance is unchanged, but audit storage is not confirmed.";
            case UNCONFIRMED -> "Deposit outcome is unconfirmed.";
        };
        return new DepositException(message + " Stop; do not repeat the deposit or restart. "
                + "Keep this process open for manual reconciliation.",
                pending.evidence().referenceNumber().orElseThrow());
    }

    public BankTransaction deposit(long accountId, String suppliedAmount) {
        // The callback rechecks session expiry and pending evidence after form input.
        long owner = identity.get().customerId();
        if (pending != null) throw pendingFailure();
        TransactionReference reference = Objects.requireNonNull(references.get());
        Instant time = Objects.requireNonNull(clock.instant());
        Execution result;
        if (!TransactionValidator.supportedTime(time)) {
            result = rejected("UNSUPPORTED_TIMESTAMP", "Deposit clock is outside supported storage precision/range.");
        } else if (accountId <= 0) {
            result = rejected("INVALID_ACCOUNT_ID", "Choose a positive account ID.");
        } else {
            Money amount = null;
            try {
                amount = TransactionValidator.amount(suppliedAmount, maximum);
            } catch (ValidationException invalid) {
                // Only the constant code/message is retained, never the supplied string.
            }
            result = amount == null
                    ? rejected("INVALID_AMOUNT", "Amount must be positive, within the configured limit, "
                    + "and use at most two decimal places.")
                    : execute(owner, accountId, amount, reference, time);
        }

        TransactionAudit evidence = new TransactionAudit(null, owner, reference, TransactionType.DEPOSIT,
                result.outcome(), result.failureCode(), time);
        pending = new PendingDeposit(evidence, result.money(), AuditState.NOT_ATTEMPTED);
        try {
            // execute() has already closed its connection, whether commit or rollback won.
            audits.append(evidence);
            pending = result.money() == MoneyState.UNCONFIRMED
                    ? new PendingDeposit(evidence, result.money(), AuditState.SAVED) : null;
        } catch (IllegalArgumentException rejected) {
            pending = new PendingDeposit(evidence, result.money(), AuditState.REJECTED_NO_WRITE);
        } catch (RuntimeException uncertain) {
            pending = new PendingDeposit(evidence, result.money(), AuditState.SAVE_UNCERTAIN);
        }
        if (pending != null) throw pendingFailure();
        if (result.money() == MoneyState.COMMITTED) return result.receipt();
        throw new DepositException(result.message() + " No money was changed.", reference);
    }

    private Execution execute(long owner, long id, Money amount, TransactionReference reference, Instant time) {
        Connection connection = null;
        boolean commitAttempted = false;
        Execution result = failed("DATABASE_UNAVAILABLE", MoneyState.UNCHANGED);
        try {
            connection = connections.getConnection();
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(timeout);
                // This deposit-owned connection has an explicit UTC timestamp contract.
                statement.execute("SET SESSION time_zone = '+00:00'");
                statement.execute("SET SESSION innodb_lock_wait_timeout = " + timeout);
            }
            Customer customer = customers.findProfile(connection, owner, true).orElse(null);
            if (customer == null || customer.customerId() != owner || customer.status() != CustomerStatus.ACTIVE)
                throw new Rejected("CUSTOMER_UNAVAILABLE", "Customer is not available for deposits.");
            Account account = accounts.lockOwned(connection, owner, id).orElse(null);
            if (account == null || account.accountId() != id || account.customerId() != owner)
                throw new Rejected("ACCOUNT_NOT_FOUND", "Account not found.");
            if (account.status() != AccountStatus.ACTIVE)
                throw new Rejected("ACCOUNT_UNAVAILABLE", "Account is not active.");
            if (account.type() != AccountType.SAVINGS && account.type() != AccountType.CURRENT)
                throw new Rejected("ACCOUNT_TYPE_NOT_ELIGIBLE", "Deposits require a Savings or Current account.");
            BankTransaction receipt;
            try {
                receipt = BankTransaction.deposit(reference, account, amount, time);
            } catch (IllegalArgumentException overflow) {
                throw new Rejected("BALANCE_OVERFLOW", "The resulting balance exceeds supported storage.");
            }
            accounts.updateBalance(connection, owner, id, receipt.balanceBefore(), receipt.balanceAfter());
            ledger.insertDeposit(connection, receipt);
            Account after = accounts.findOwned(connection, owner, id).orElse(null);
            if (after == null || after.customerId() != owner || after.accountId() != id
                    || !after.number().equals(account.number()) || after.type() != account.type()
                    || after.status() != AccountStatus.ACTIVE || !after.balance().equals(receipt.balanceAfter()))
                throw new SQLException("Balance read-back did not match.");
            commitAttempted = true;
            connection.commit();
            result = new Execution(receipt, MoneyState.COMMITTED, AuditOutcome.SUCCESS, null, "");
        } catch (Rejected rejection) {
            result = rollback(connection)
                    ? rejected(rejection.code, rejection.getMessage())
                    : failed("ROLLBACK_UNCONFIRMED", MoneyState.UNCONFIRMED);
        } catch (SQLException | RuntimeException failure) {
            boolean rolledBack = connection == null || rollback(connection);
            result = commitAttempted
                    ? failed("COMMIT_UNCONFIRMED", MoneyState.UNCONFIRMED)
                    : rolledBack ? failed(connection == null ? "DATABASE_UNAVAILABLE" : "DATABASE_ERROR", MoneyState.UNCHANGED)
                    : failed("ROLLBACK_UNCONFIRMED", MoneyState.UNCONFIRMED);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException | RuntimeException cleanup) {
                    if (result.money() != MoneyState.UNCHANGED)
                        result = failed("CLEANUP_UNCONFIRMED", MoneyState.UNCONFIRMED);
                    // A confirmed rollback with no commit attempt remains unchanged.
                }
            }
        }
        return result;
    }

    private static boolean rollback(Connection connection) {
        if (connection == null) return true;
        try { connection.rollback(); return true; }
        catch (SQLException | RuntimeException failure) { return false; }
    }

    private static Execution rejected(String code, String message) {
        return new Execution(null, MoneyState.UNCHANGED, AuditOutcome.REJECTED, code, message);
    }

    private static Execution failed(String code, MoneyState state) {
        return new Execution(null, state, state == MoneyState.UNCHANGED && !code.equals("DATABASE_UNAVAILABLE")
                ? AuditOutcome.ROLLED_BACK : AuditOutcome.FAILED, code, "Deposit could not be completed.");
    }

    private static final class Rejected extends RuntimeException {
        final String code;
        Rejected(String code, String safeMessage) { super(safeMessage); this.code = code; }
    }
}
