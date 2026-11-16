package com.example.banking.service;

import com.example.banking.dao.CustomerDAO;
import com.example.banking.dao.SecurityAuditDAO;
import com.example.banking.dao.TransactionAuditDAO;
import com.example.banking.dao.jdbc.JdbcSecurityAuditDAO;
import com.example.banking.dao.jdbc.JdbcAccountDAO;
import com.example.banking.dao.jdbc.JdbcTransactionDAO;
import com.example.banking.dao.jdbc.JdbcTransactionHistoryDAO;
import com.example.banking.dao.jdbc.JdbcTransactionAuditDAO;
import com.example.banking.exception.*;
import com.example.banking.model.*;
import com.example.banking.security.*;
import com.example.banking.util.ConnectionFactory;
import com.example.banking.util.ReferenceGenerator;
import com.example.banking.util.AppConfig;
import com.example.banking.validation.CustomerValidator;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Registration and serialized login-state updates. No password arrays escape.
 * Independent audit delivery happens AFTER the primary transaction closes.
 */
public final class AuthService {
    private final ConnectionFactory connections;
    private final CustomerDAO customers;
    private final PasswordHasher passwords;
    private final CustomerValidator validator;
    private final SessionManager sessions;
    private final LoginAuditDelivery delivery;
    private final Clock clock;
    private final int maxFailures;
    private final Duration lockDuration;
    private final int queryTimeout;
    private final String dummyHash;
    private final CustomerService profiles;
    private final PasswordChangeService passwordChanges;
    private final AccountService accountService;
    private final TransactionService transactions;
    private final HistoryService history;
    private PasswordChangeUnconfirmedException pendingPasswordChange;
    private Customer currentCustomer;
    private LoginAuditDelivery.Result pendingAudit;

    public AuthService(ConnectionFactory connections, CustomerDAO customers,
                       PasswordHasher passwords, CustomerValidator validator,
                       SessionManager sessions, LoginAuditDelivery delivery,
                       Clock clock, int maxFailures, Duration lockDuration, int queryTimeout) {
        this(connections, customers, passwords, validator, sessions, delivery, clock,
                maxFailures, lockDuration, queryTimeout, new JdbcSecurityAuditDAO(queryTimeout));
    }

    public AuthService(ConnectionFactory connections, CustomerDAO customers,
                       PasswordHasher passwords, CustomerValidator validator,
                       SessionManager sessions, LoginAuditDelivery delivery,
                       Clock clock, int maxFailures, Duration lockDuration, int queryTimeout,
                       SecurityAuditDAO securityAudit) {
        this(connections, customers, passwords, validator, sessions, delivery, clock, maxFailures,
                lockDuration, queryTimeout, securityAudit,
                new Money(AppConfig.load().getPositiveDecimal("tx.max.amount")),
                new JdbcTransactionAuditDAO(connections, queryTimeout));
    }

    public AuthService(ConnectionFactory connections, CustomerDAO customers,
                       PasswordHasher passwords, CustomerValidator validator,
                       SessionManager sessions, LoginAuditDelivery delivery,
                       Clock clock, int maxFailures, Duration lockDuration, int queryTimeout,
                       SecurityAuditDAO securityAudit, Money maximumDeposit,
                       TransactionAuditDAO financialAudit) {
        this.connections = Objects.requireNonNull(connections);
        this.customers = Objects.requireNonNull(customers);
        this.passwords = Objects.requireNonNull(passwords);
        this.validator = Objects.requireNonNull(validator);
        this.sessions = Objects.requireNonNull(sessions);
        this.delivery = Objects.requireNonNull(delivery);
        this.clock = Objects.requireNonNull(clock);
        if (maxFailures <= 0 || lockDuration == null || lockDuration.isZero()
                || lockDuration.isNegative() || queryTimeout <= 0) {
            throw new IllegalArgumentException("Positive authentication settings are required.");
        }
        this.maxFailures = maxFailures;
        this.lockDuration = lockDuration;
        this.queryTimeout = queryTimeout;
        this.profiles = new CustomerService(connections, customers, this::currentCustomer, queryTimeout);
        this.accountService = new AccountService(connections, new JdbcAccountDAO(queryTimeout),
                customers, this::currentCustomer, new ReferenceGenerator()::generateAccountNumber,
                clock, queryTimeout);
        var referenceGenerator = new ReferenceGenerator();
        this.transactions = new TransactionService(connections, new JdbcAccountDAO(queryTimeout),
                customers, new JdbcTransactionDAO(queryTimeout), financialAudit, this::currentCustomer,
                () -> new TransactionReference(referenceGenerator.generateTransactionReference()),
                clock, maximumDeposit, queryTimeout);
        this.history = new HistoryService(connections, new JdbcAccountDAO(queryTimeout), customers,
                new JdbcTransactionHistoryDAO(queryTimeout), this::currentCustomer, queryTimeout);
        char[] dummy = "Not-A-Real-Account-1!".toCharArray();
        try {
            dummyHash = passwords.hash(dummy);
        } finally {
            Arrays.fill(dummy, '\0');
        }
        this.passwordChanges = new PasswordChangeService(connections, customers, securityAudit,
                passwords, this::currentCustomer, clock, maxFailures, lockDuration, queryTimeout, dummyHash);
    }

    public Customer register(Registration input, char[] raw) {
        try {
            ensureAvailable();
            validator.validate(input, raw);
            // Precheck is friendly; UNIQUE constraints remain the race guard.
            try (Connection connection = connections.getConnection()) {
                begin(connection);
                try {
                    if (customers.identityExists(connection, input.username(), input.email())) {
                        throw duplicateIdentity();
                    }
                    PasswordHash hash = new PasswordHash(passwords.hash(raw));
                    String code = "C-" + UUID.randomUUID().toString().replace("-", "").substring(0, 28);
                    Customer customer;
                    try {
                        customer = customers.insert(connection, input, code, hash);
                    } catch (SQLException failure) {
                        // Only MySQL duplicate-key insertion failure, not every integrity error.
                        if (failure.getErrorCode() == 1062) throw duplicateIdentity();
                        throw failure;
                    }
                    connection.commit();
                    return customer;
                } catch (SQLException | RuntimeException | Error failure) {
                    rollback(connection);
                    throw failure;
                }
            } catch (SQLException failure) {
                throw safeDatabaseFailure();
            }
        } finally {
            clear(raw);
        }
    }

    public Customer login(String attempted, char[] raw) {
        try {
            ensureAvailable();
            if (currentCustomer != null) throw new ValidationException("Log out before logging in again.");
            if (attempted == null) throw new IllegalArgumentException("Attempted username is required.");
            Instant now = eventTime();
            if (attempted.codePointCount(0, attempted.length()) > 80) {
                deliver(LoginAuditCapture.failedOversizedUsername(attempted, now));
                throw new AuthenticationException();
            }
            if (attempted.isBlank() || !com.example.banking.model.policy.PasswordPolicy.hasSupportedLength(raw)) {
                deliver(new LoginAudit(null, null, attempted, LoginOutcome.FAILURE,
                        "INVALID_CREDENTIALS", now));
                throw new AuthenticationException();
            }

            Attempt result;
            try {
                result = authenticate(attempted, raw, now);
            } catch (PersistenceException unconfirmed) {
                deliver(new LoginAudit(null, null, attempted, LoginOutcome.FAILURE,
                        "AUTHENTICATION_UNCONFIRMED", now));
                throw unconfirmed;
            }
            deliver(new LoginAudit(null, result.customer() == null ? null : result.customer().customerId(),
                    attempted, result.outcome(), result.reason(), now));
            if (result.outcome() != LoginOutcome.SUCCESS) throw new AuthenticationException();
            sessions.start(result.customer().customerId());
            currentCustomer = result.customer();
            return currentCustomer;
        } finally {
            clear(raw);
        }
    }

    private Attempt authenticate(String attempted, char[] raw, Instant now) {
        try (Connection connection = connections.getConnection()) {
            begin(connection);
            try {
                Optional<CustomerDAO.AuthenticationRow> found = customers.lockByUsername(connection, attempted);
                Attempt result;
                if (found.isEmpty()) {
                    passwords.verify(raw, dummyHash);
                    result = new Attempt(null, LoginOutcome.FAILURE, "INVALID_CREDENTIALS");
                } else {
                    var row = found.orElseThrow();
                    Customer customer = row.customer();
                    boolean blocked = customer.status() != CustomerStatus.ACTIVE
                            || row.lockedUntil() != null && now.isBefore(row.lockedUntil());
                    // Keep the generic denial path cryptographically active as well.
                    boolean verified = passwords.verify(raw, blocked ? dummyHash : row.hash().value());
                    if (blocked) {
                        result = new Attempt(customer, LoginOutcome.LOCKED, "ACCOUNT_UNAVAILABLE");
                    } else if (!verified) {
                        int oldFailures = row.lockedUntil() != null ? 0 : row.failedCount();
                        int failures = Math.min(Math.max(0, oldFailures), maxFailures - 1) + 1;
                        Instant until = failures >= maxFailures ? now.plus(lockDuration) : null;
                        customers.updateLoginState(connection, customer.customerId(), failures, until);
                        result = new Attempt(customer,
                                until == null ? LoginOutcome.FAILURE : LoginOutcome.LOCKED,
                                until == null ? "INVALID_CREDENTIALS" : "TOO_MANY_ATTEMPTS");
                    } else {
                        customers.updateLoginState(connection, customer.customerId(), 0, null);
                        result = new Attempt(customer, LoginOutcome.SUCCESS, null);
                    }
                }
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | Error failure) {
                rollback(connection);
                throw failure;
            }
        } catch (SQLException failure) {
            // A commit failure may be uncertain; never replay authentication.
            throw safeDatabaseFailure();
        }
    }

    public void changePassword(char[] current, char[] replacement, char[] confirmation) {
        try {
            var result = passwordChanges.change(current, replacement, confirmation);
            if (result == PasswordChangeService.Result.LOCKED) {
                invalidateSession();
                throw new AuthenticationException();
            }
            if (result == PasswordChangeService.Result.REJECTED)
                throw new ValidationException("Current password could not be verified. Password was not changed.");
        } catch (PasswordChangeUnconfirmedException unconfirmed) {
            pendingPasswordChange = unconfirmed;
            invalidateSession();
            throw unconfirmed;
        } catch (AuthenticationException denied) {
            invalidateSession();
            throw denied;
        }
    }

    public Optional<PasswordChangeUnconfirmedException> pendingPasswordChange() {
        return Optional.ofNullable(pendingPasswordChange);
    }

    private void invalidateSession() {
        sessions.end();
        currentCustomer = null;
    }

    public Customer currentProfile() {
        Customer profile = profiles.profile();
        currentCustomer = profile;
        return profile;
    }

    public Customer updateContact(ContactDetails details) {
        Customer profile = profiles.updateContact(details);
        currentCustomer = profile;
        return profile;
    }

    public Account openAccount(AccountType type) { return accountService.open(type); }

    public java.util.List<Account> accounts() { return accountService.list(); }

    public Account accountDetails(long accountId) { return accountService.details(accountId); }

    public BankTransaction deposit(long accountId, String amount) {
        return transactions.deposit(accountId, amount);
    }

    public BankTransaction withdraw(long accountId, String amount) {
        return transactions.withdraw(accountId, amount);
    }

    public TransferReceipt transfer(long sourceId, String destinationNumber, String amount) {
        return transactions.transfer(sourceId, destinationNumber, amount);
    }

    public HistoryPage history(long accountId, HistoryQuery query) {
        return history.page(accountId, query);
    }

    /** Legacy name: the single pending slot guards all ordinary money operations. */
    public Optional<TransactionService.PendingDeposit> pendingDeposit() { return transactions.pending(); }

    public Customer currentCustomer() {
        ensureAvailable();
        try {
            SessionContext active = sessions.requireActive();
            if (currentCustomer == null || currentCustomer.customerId() != active.customerId()) {
                throw new AuthenticationException();
            }
            return currentCustomer;
        } catch (SessionExpiredException expired) {
            Customer ended = currentCustomer;
            currentCustomer = null;
            if (ended != null) {
                deliver(new LoginAudit(null, ended.customerId(), ended.username(),
                        LoginOutcome.TIMEOUT, null, eventTime()));
            }
            throw expired;
        }
    }

    public void logout() {
        Customer ended = currentCustomer;
        currentCustomer = null;
        Optional<SessionContext> active = sessions.end();
        if (ended != null && active.isPresent()) {
            deliver(new LoginAudit(null, ended.customerId(), ended.username(),
                    LoginOutcome.LOGOUT, null, eventTime()));
        }
    }

    /** Sensitive support boundary. Do not log, stringify, retry or discard the evidence. */
    public Optional<LoginAuditDelivery.Result> pendingAudit() {
        return Optional.ofNullable(pendingAudit);
    }

    private void deliver(LoginAudit evidence) {
        ensureAvailable();
        LoginAuditDelivery.Result result = delivery.deliver(evidence);
        if (result.status() != LoginAuditDelivery.Status.SAVED) {
            pendingAudit = result;
            sessions.end();
            currentCustomer = null;
            throw new AuditUnavailableException();
        }
    }

    private void ensureAvailable() {
        if (transactions.pending().isPresent()) throw transactions.pendingFailure();
        if (pendingPasswordChange != null) throw pendingPasswordChange;
        if (pendingAudit != null) throw new AuditUnavailableException();
    }

    private Instant eventTime() {
        Instant now = clock.instant();
        // Clock resolution is explicit at application composition. Never alter
        // an already-captured fractional Instant to make persistence accept it.
        if (now.getNano() != 0 || now.isBefore(Instant.parse("1970-01-01T00:00:01Z"))
                || now.isAfter(Instant.parse("2038-01-19T03:14:07Z"))) {
            throw new IllegalStateException("Authentication requires a whole-second audit clock in the supported SQL range.");
        }
        return now;
    }

    private void begin(Connection connection) throws SQLException {
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        connection.setAutoCommit(false);
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(queryTimeout);
            statement.execute("SET SESSION innodb_lock_wait_timeout = " + queryTimeout);
        }
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve only safe customer errors; never print driver details.
        }
    }

    private static ValidationException duplicateIdentity() {
        return new ValidationException("That username or email is already registered.");
    }

    private static PersistenceException safeDatabaseFailure() {
        return new PersistenceException(new SQLException(
                "Database operation was not confirmed. Do not retry automatically."));
    }

    private static void clear(char[] raw) {
        if (raw != null) Arrays.fill(raw, '\0');
    }

    private record Attempt(Customer customer, LoginOutcome outcome, String reason) {}
}
