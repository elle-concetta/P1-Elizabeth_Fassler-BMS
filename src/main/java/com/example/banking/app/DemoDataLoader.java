package com.example.banking.app;

import com.example.banking.dao.AccountDAO;
import com.example.banking.dao.jdbc.JdbcAccountDAO;
import com.example.banking.model.*;
import com.example.banking.model.policy.PasswordPolicy;
import com.example.banking.security.*;
import com.example.banking.util.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/**
 * Explicit, additive training loader for banking_db only.
 * Inserts 100 customers/200 empty ordinary accounts, never funding or invented audit/history.
 */
public final class DemoDataLoader {
    private static final int CUSTOMER_COUNT = 100;
    private static final List<String> FIRST_NAMES = List.of(
            "Avery", "Jordan", "Taylor", "Morgan", "Casey", "Riley", "Alex", "Jamie", "Quinn", "Cameron");
    private static final List<String> LAST_NAMES = List.of(
            "Carter", "Brooks", "Reed", "Parker", "Hayes", "Bennett", "Foster", "Ellis", "Bailey", "Sullivan");
    public record DemoAccount(String username, long id, AccountType type, AccountNumber number) {
        @Override public String toString() {
            return username + " | Account ID: " + id + " | " + type + " | " + number.masked();
        }
    }
    public static final class SeedFailure extends RuntimeException {
        private final boolean uncertain;
        SeedFailure(boolean uncertain) {
            super(uncertain
                    ? "Creation was not fully confirmed. Do not rerun. Check training_demo_001 through training_demo_100 with the README queries first."
                    : "Loader stopped. Confirm banking_db, required INSERT/SELECT access, and unused training_demo usernames. No new data was committed.");
            this.uncertain = uncertain;
        }
        public boolean uncertain() { return uncertain; }
    }
    private DemoDataLoader() {}

    public static void main(String[] args) {
        int status = run(args);
        if (status != 0) System.exit(status);
    }

    // These are intentional user-facing CLI messages, not diagnostic logpoints.
    @SuppressWarnings({"UseOfSystemOutOrSystemErr", "JavaPrintToLogpoint"})
    private static int run(String[] args) {
        if (args.length != 1 || !args[0].equals("--confirm-create-demo")) {
            System.out.println("Creates 100 customers, training_demo_001 through training_demo_100, and 200 ZERO-balance accounts in banking_db.");
            System.out.println("No deletes, existing-record updates, schema changes, money movements or fabricated audit/history.");
            System.out.println("Read the README, then explicitly pass --confirm-create-demo to proceed.");
            return 0;
        }
        var console = System.console();
        if (console == null) {
            System.out.println("Run from a real PowerShell terminal so the demo password can be hidden.");
            return 1;
        }
        char[] password = null, confirmation = null;
        try {
            password = console.readPassword("Choose a private password for the 100 DEMO users (10+ characters, all four classes): ");
            confirmation = console.readPassword("Confirm the demo password: ");
            if (password == null || confirmation == null || !Arrays.equals(password, confirmation)) {
                System.out.println("Passwords did not match; nothing was submitted."); return 1;
            }
            var config = AppConfig.load();
            System.out.println("Preparing 100 separately salted bcrypt hashes and 200 empty accounts. This may take a minute; do not start another loader.");
            var created = seed(ConnectionFactory.fromEnvironment(),
                    new BcryptPasswordHasher(config.getPositiveInt("password.hash.cost")),
                    new JdbcAccountDAO(config.getPositiveInt("db.query.timeout.seconds")),
                    Clock.tickSeconds(ZoneOffset.UTC), config.getPositiveInt("db.query.timeout.seconds"), password);
            System.out.println("Created 100 synthetic customers and 200 ZERO-balance accounts. Existing data was not changed.");
            created.forEach(System.out::println);
            System.out.println("Log in with a training_demo username and the private password you just chose.");
            System.out.println("Fund accounts only through the normal CLI deposit flow. Do not rerun this loader.");
            return 0;
        } catch (SeedFailure safe) { System.out.println(safe.getMessage()); return 1; }
        catch (RuntimeException safe) {
            System.out.println("Check DB_URL, DB_USERNAME, DB_PASSWORD, app.properties and password requirements. No loader retry was attempted.");
            return 1;
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }

    /** Injectable boundary used by tests; the caller must clear its password array. */
    public static List<DemoAccount> seed(ConnectionFactory factory, PasswordHasher hasher, AccountDAO accounts,
                                         Clock clock, int timeout, char[] password) {
        var policy = new PasswordPolicy();
        for (int index = 1; index <= CUSTOMER_COUNT; index++) policy.validate(username(index), password);
        if (timeout <= 0) throw new IllegalArgumentException("Positive timeout required.");
        LocalDate date = LocalDate.now(clock);
        if (date.getYear() < 1000 || date.getYear() > 9999) throw new IllegalArgumentException("Unsupported business date.");
        List<DemoAccount> created = new ArrayList<>();
        Connection c = null;
        boolean writes = false, commitAttempted = false;
        SeedFailure failure = null;
        try {
            c = factory.getConnection();
            if (!"MySQL".equals(c.getMetaData().getDatabaseProductName())) throw new SQLException("Unsupported server.");
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            try (var q = c.prepareStatement("SELECT DATABASE()")) {
                q.setQueryTimeout(timeout);
                try (var r = q.executeQuery()) {
                    if (!r.next() || !"banking_db".equals(r.getString(1))) throw new SQLException("Unapproved seed target.");
                }
            }
            String placeholders = String.join(",", Collections.nCopies(CUSTOMER_COUNT, "?"));
            try (var q = c.prepareStatement("SELECT COUNT(*) FROM customers WHERE username IN ("
                    + placeholders + ") OR email IN (" + placeholders + ")")) {
                q.setQueryTimeout(timeout);
                for (int index = 1; index <= CUSTOMER_COUNT; index++) {
                    q.setString(index, username(index));
                    q.setString(CUSTOMER_COUNT + index, username(index) + "@example.invalid");
                }
                try (var r = q.executeQuery()) {
                    if (!r.next() || r.getLong(1) != 0) throw new SQLException("Synthetic identities already exist.");
                }
            }
            // Fresh salt per customer, prepared before the first write so hashing
            // does not hold insertion locks. Never print the resulting hashes.
            List<String> hashes = new ArrayList<>(CUSTOMER_COUNT);
            for (int index = 0; index < CUSTOMER_COUNT; index++) hashes.add(hasher.hash(password));
            try (var utc = c.createStatement()) {
                utc.setQueryTimeout(timeout); utc.execute("SET SESSION time_zone = '+00:00'");
            }
            var generator = new ReferenceGenerator();
            for (int index = 1; index <= CUSTOMER_COUNT; index++) {
                String username = username(index);
                long customerId;
                writes = true;
                try (var q = c.prepareStatement("""
                        INSERT INTO customers(customer_code, first_name, last_name, date_of_birth,
                            address, email, phone, username, password_hash, status, failed_login_count)
                        VALUES (?, ?, ?, '2000-01-01', 'Synthetic training data only', ?, ?, ?, ?, 'ACTIVE', 0)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    q.setQueryTimeout(timeout);
                    q.setString(1, "C-DEMO-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
                    q.setString(2, FIRST_NAMES.get((index - 1) / LAST_NAMES.size()));
                    q.setString(3, LAST_NAMES.get((index - 1) % LAST_NAMES.size()));
                    q.setString(4, username + "@example.invalid");
                    q.setString(5, String.format(Locale.ROOT, "20255501%02d", index - 1));
                    q.setString(6, username); q.setString(7, hashes.get(index - 1));
                    if (q.executeUpdate() != 1) throw new SQLException("Synthetic insert count invalid.");
                    try (var keys = q.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Synthetic identity missing.");
                        customerId = keys.getLong(1);
                        if (customerId <= 0) throw new SQLException("Synthetic identity missing.");
                    }
                }
                for (var type : List.of(AccountType.SAVINGS, AccountType.CURRENT)) {
                    AccountNumber number = generator.generateAccountNumber();
                    long id = accounts.insert(c, customerId, number, type, date);
                    if (id <= 0) throw new SQLException("Synthetic account identity missing.");
                    created.add(new DemoAccount(username, id, type, number));
                }
            }
            commitAttempted = true;
            c.commit();
        } catch (SQLException | RuntimeException safe) {
            boolean uncertain = commitAttempted;
            if (c != null) {
                try { c.rollback(); }
                catch (SQLException | RuntimeException rollback) { uncertain |= writes; }
            }
            failure = new SeedFailure(uncertain);
        } finally {
            if (c != null) {
                try { c.close(); }
                catch (SQLException | RuntimeException close) {
                    // Report only after cleanup; never mask an earlier uncertain outcome.
                    failure = new SeedFailure(writes || (failure != null && failure.uncertain()));
                }
            }
        }
        if (failure != null) throw failure;
        return List.copyOf(created);
    }

    private static String username(int index) {
        return String.format(Locale.ROOT, "training_demo_%03d", index);
    }
}
