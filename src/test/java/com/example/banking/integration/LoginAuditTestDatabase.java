package com.example.banking.integration;

import com.example.banking.util.ConnectionFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Deliberately narrow, opt-in test harness. Never reads DB_* or executes the
 * full application schema script. Extracts only the two required table DDLs.
 */
public final class LoginAuditTestDatabase implements AutoCloseable {
    public static final String APPROVAL = "CREATE_LOGIN_AUDIT_FIXTURES_IN_BANKING_TEST";
    /**
     * Fixed test-only options, not user-supplied JDBC properties. In particular,
     * none can override the guarded URL, credentials, catalog or timeouts.
     */
    enum DriverTimeZones {
        DEFAULT(null, null),
        LOCAL_WEST("LOCAL", "-07:00"),
        SERVER_EAST("SERVER", "+09:00"),
        TOKYO_WEST("Asia/Tokyo", "-07:00"),
        LOS_ANGELES_EAST("America/Los_Angeles", "+09:00");

        final String connectionTimeZone;
        final String sessionTimeZone;

        DriverTimeZones(String connectionTimeZone, String sessionTimeZone) {
            this.connectionTimeZone = connectionTimeZone;
            this.sessionTimeZone = sessionTimeZone;
        }
    }

    /**
     * Read-only expectations for separately provisioned servers, never SQL to
     * change their modes. Omit the setting for existing non-matrix suites.
     */
    enum SqlModeProfile {
        DEFAULT_STRICT(Set.of("ONLY_FULL_GROUP_BY", "STRICT_TRANS_TABLES",
                "NO_ZERO_IN_DATE", "NO_ZERO_DATE", "ERROR_FOR_DIVISION_BY_ZERO",
                "NO_ENGINE_SUBSTITUTION")),
        PERMISSIVE(Set.of()),
        TRUNCATE_FRACTIONAL(Set.of("TIME_TRUNCATE_FRACTIONAL"));

        private final Set<String> modes;

        SqlModeProfile(Set<String> modes) {
            this.modes = modes;
        }

        boolean matches(String actual) {
            return actual != null && modes.equals(actual.isEmpty()
                    ? Set.of() : Set.copyOf(java.util.Arrays.asList(actual.split(",", -1))));
        }

        static SqlModeProfile from(Map<String, String> settings) {
            String profile = settings.get("LOGIN_AUDIT_IT_SQL_MODE_PROFILE");
            return profile == null ? null : valueOf(profile);
        }

        void verify(Connection connection) throws SQLException {
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery(
                         "SELECT VERSION(), @@global.sql_mode, @@session.sql_mode")) {
                if (!rows.next() || !rows.getString(1).startsWith("8.4.")
                        || !matches(rows.getString(2)) || !matches(rows.getString(3))) {
                    throw new IllegalStateException(
                            "Expected MySQL 8.4 with startup SQL mode profile " + name()
                                    + "; neither global nor session modes were changed.");
                }
            }
        }
    }

    private final ConnectionFactory factory;
    private boolean customersCreated;
    private boolean auditCreated;

    public static void validateSettings(Map<String, String> settings) {
        if (!APPROVAL.equals(settings.get("LOGIN_AUDIT_IT_APPROVED"))) {
            throw new IllegalArgumentException("Explicit login audit fixture approval is required.");
        }
        String url = settings.getOrDefault("TEST_DB_URL", "");
        // No URL options, alternate hosts, failover, credentials, encoded catalog,
        // or database overrides. Remote databases require a local tunnel.
        if (!url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]{1,5}/banking_test")) {
            throw new IllegalArgumentException("Use an explicit loopback port and banking_test catalog only.");
        }
        if (!"banking_test_runner".equals(settings.get("TEST_DB_USERNAME"))) {
            throw new IllegalArgumentException("A dedicated banking_test_runner principal is required.");
        }
        if (settings.getOrDefault("TEST_DB_PASSWORD", "").isBlank()) {
            throw new IllegalArgumentException("TEST_DB_PASSWORD is required; no application fallback exists.");
        }
        SqlModeProfile.from(settings); // Reject typos and arbitrary SQL before connecting.
    }

    public LoginAuditTestDatabase(Map<String, String> settings) {
        this(settings, DriverTimeZones.DEFAULT);
    }

    LoginAuditTestDatabase(Map<String, String> settings, DriverTimeZones timeZones) {
        validateSettings(settings);
        java.util.Objects.requireNonNull(timeZones);
        SqlModeProfile sqlModeProfile = SqlModeProfile.from(settings);
        factory = new ConnectionFactory(settings.get("TEST_DB_URL"),
                settings.get("TEST_DB_USERNAME"), settings.get("TEST_DB_PASSWORD"),
                (url, credentials) -> {
                    Properties properties = new Properties();
                    properties.putAll(credentials);
                    properties.setProperty("connectTimeout", "5000");
                    properties.setProperty("socketTimeout", "10000");
                    if (timeZones != DriverTimeZones.DEFAULT) {
                        properties.setProperty("connectionTimeZone", timeZones.connectionTimeZone);
                        properties.setProperty("sessionVariables",
                                "time_zone='" + timeZones.sessionTimeZone + "'");
                    }
                    Connection connection = DriverManager.getConnection(url, properties);
                    try {
                        verifyTarget(connection);
                        if (sqlModeProfile != null) {
                            sqlModeProfile.verify(connection);
                        }
                        try (var statement = connection.createStatement()) {
                            statement.execute("SET SESSION innodb_lock_wait_timeout = 3");
                        }
                        return connection;
                    } catch (SQLException | RuntimeException | Error failure) {
                        connection.close();
                        throw failure;
                    }
                });
    }

    private static void verifyTarget(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT DATABASE(), CURRENT_USER(), VERSION()")) {
            if (!rows.next() || !"banking_test".equals(rows.getString(1))
                    || !rows.getString(2).startsWith("banking_test_runner@")
                    || !rows.getString(3).startsWith("8.")) {
                throw new IllegalStateException("Expected dedicated banking_test principal on MySQL 8.");
            }
        }
        // Refuse a nominally test-only user with application/global privileges.
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SHOW GRANTS")) {
            while (rows.next()) {
                String grant = rows.getString(1);
                if (!(grant.startsWith("GRANT USAGE ON *.* TO ")
                        || grant.matches("GRANT .+ ON `banking_test`\\.\\* TO .+"))
                        || grant.contains("WITH GRANT OPTION")) {
                    throw new IllegalStateException("Test principal must have banking_test-only privileges.");
                }
            }
        }
    }

    public ConnectionFactory factory() {
        return factory;
    }

    public Connection open() throws SQLException {
        return factory.getLoginAuditConnection();
    }

    public void createFixtures() throws Exception {
        try (Connection connection = open();
             var statement = connection.createStatement()) {
            // Never drop, truncate, or reuse pre-existing objects.
            try (var tables = statement.executeQuery("""
                    SELECT COUNT(*) FROM information_schema.tables
                    WHERE table_schema = 'banking_test'
                    """)) {
                tables.next();
                if (tables.getLong(1) != 0) {
                    throw new IllegalStateException("banking_test must be empty; nothing was changed.");
                }
            }
            String schema = Files.readString(Path.of("sql/mysql/schema.sql"));
            statement.execute(tableDefinition(schema, "customers"));
            customersCreated = true;
            statement.execute(tableDefinition(schema, "login_audit"));
            auditCreated = true;
        }
    }

    private static String tableDefinition(String schema, String table) {
        String startMarker = "CREATE TABLE " + table + " (";
        int start = schema.indexOf(startMarker);
        int end = start < 0 ? -1 : schema.indexOf(";", start);
        if (start < 0 || end < 0) {
            throw new IllegalStateException("Expected test table definition is missing.");
        }
        return schema.substring(start, end);
    }

    // The guarded disposable schema need not exist in the IDE's data sources.
    @SuppressWarnings("SqlResolve")
    @Override
    public void close() throws SQLException {
        if (!auditCreated && !customersCreated) {
            return;
        }
        try (Connection connection = open();
             var statement = connection.createStatement()) {
            if (auditCreated) {
                statement.execute("DROP TABLE banking_test.login_audit");
                auditCreated = false;
            }
            if (customersCreated) {
                statement.execute("DROP TABLE banking_test.customers");
                customersCreated = false;
            }
        }
    }
}
