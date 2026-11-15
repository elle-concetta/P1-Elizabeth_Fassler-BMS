package com.example.banking.integration;

import com.example.banking.util.ConnectionFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

/**
 * Deliberately narrow, opt-in test harness. Never reads DB_* or executes the
 * full application schema script. Extracts only the two required table DDLs.
 */
public final class LoginAuditTestDatabase implements AutoCloseable {
    public static final String APPROVAL = "CREATE_LOGIN_AUDIT_FIXTURES_IN_BANKING_TEST";
    /** Fixed protocol choices; DEFAULT leaves Connector/J's existing behavior alone. */
    enum PreparedStatements {
        DEFAULT(null),
        CLIENT("false"),
        SERVER("true");

        final String useServerPrepStmts;

        PreparedStatements(String useServerPrepStmts) {
            this.useServerPrepStmts = useServerPrepStmts;
        }
    }

    /**
     * Fixed test-only options, not user-supplied JDBC properties. In particular,
     * none can override the guarded URL, credentials, catalog or timeouts.
     */
    enum DriverTimeZones {
        DEFAULT(null, null),
        LOCAL_WEST("LOCAL", "-07:00"),
        SERVER_EAST("SERVER", "+09:00"),
        TOKYO_WEST("Asia/Tokyo", "-07:00"),
        LOS_ANGELES_EAST("America/Los_Angeles", "+09:00"),
        // Numeric offsets need no MySQL zone tables. Do not set sessionVariables
        // for these profiles: Connector/J itself must establish the session zone.
        FORCED_WEST("-07:00", "-07:00", true),
        FORCED_EAST("+09:00", "+09:00", true);

        final String connectionTimeZone;
        final String sessionTimeZone;
        final boolean forceConnectionTimeZoneToSession;

        DriverTimeZones(String connectionTimeZone, String sessionTimeZone) {
            this(connectionTimeZone, sessionTimeZone, false);
        }

        DriverTimeZones(String connectionTimeZone, String sessionTimeZone,
                        boolean forceConnectionTimeZoneToSession) {
            this.connectionTimeZone = connectionTimeZone;
            this.sessionTimeZone = sessionTimeZone;
            this.forceConnectionTimeZoneToSession = forceConnectionTimeZoneToSession;
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
    }

    public LoginAuditTestDatabase(Map<String, String> settings) {
        this(settings, DriverTimeZones.DEFAULT);
    }

    LoginAuditTestDatabase(Map<String, String> settings, DriverTimeZones timeZones) {
        this(settings, timeZones, PreparedStatements.DEFAULT);
    }

    LoginAuditTestDatabase(Map<String, String> settings, DriverTimeZones timeZones,
                           PreparedStatements preparedStatements) {
        validateSettings(settings);
        java.util.Objects.requireNonNull(timeZones);
        java.util.Objects.requireNonNull(preparedStatements);
        factory = new ConnectionFactory(settings.get("TEST_DB_URL"),
                settings.get("TEST_DB_USERNAME"), settings.get("TEST_DB_PASSWORD"),
                (url, credentials) -> {
                    Properties properties = new Properties();
                    properties.putAll(credentials);
                    properties.setProperty("connectTimeout", "5000");
                    properties.setProperty("socketTimeout", "10000");
                    if (preparedStatements != PreparedStatements.DEFAULT) {
                        properties.setProperty("useServerPrepStmts",
                                preparedStatements.useServerPrepStmts);
                    }
                    if (timeZones != DriverTimeZones.DEFAULT) {
                        properties.setProperty("connectionTimeZone", timeZones.connectionTimeZone);
                        if (timeZones.forceConnectionTimeZoneToSession) {
                            properties.setProperty("forceConnectionTimeZoneToSession", "true");
                        } else {
                            properties.setProperty("sessionVariables",
                                    "time_zone='" + timeZones.sessionTimeZone + "'");
                        }
                    }
                    Connection connection = DriverManager.getConnection(url, properties);
                    try {
                        verifyTarget(connection);
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
                validateGrant(rows.getString(1));
            }
        }
    }

    /** Checks raw SHOW GRANTS text, not a client's quoted/exported rendering. */
    public static void validateGrant(String grant) {
        boolean testDatabaseGrant = grant != null
                && (grant.matches("GRANT .+ ON `banking_test`\\.\\* TO .+")
                || grant.matches("GRANT .+ ON "
                + java.util.regex.Pattern.quote("`banking\\_test`") + "\\.\\* TO .+"));
        if (grant == null
                || (!(grant.startsWith("GRANT USAGE ON *.* TO ") || testDatabaseGrant))
                || grant.contains("WITH GRANT OPTION")) {
            throw new IllegalStateException("Test principal must have banking_test-only privileges.");
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
