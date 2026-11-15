package com.example.banking.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** Separate, explicitly approved live grant checks; no DAO or timestamp assertions. */
@Execution(ExecutionMode.SAME_THREAD)
class LoginAuditGrantGuardIT {
    private static final String PRINCIPAL = "'banking_test_runner'@'127.0.0.1'";
    private static final String REFUSAL = "Test principal must have banking_test-only privileges.";
    private static DisposableGrantGuardMysql mysql;

    enum UnsafeGrant {
        GLOBAL_WRITE, OUTSIDE_SCHEMA, OUTSIDE_TABLE, OUTSIDE_COLUMN, OUTSIDE_PROCEDURE,
        INSIDE_TABLE, INSIDE_CUSTOMERS_COLUMN, INSIDE_AUDIT_COLUMN,
        GRANT_OPTION, ASSIGNED_ROLE, DEFAULT_ROLE
    }

    @BeforeAll
    static void startOwnedServer() throws Exception {
        mysql = new DisposableGrantGuardMysql();
        // Independent server-side evidence includes failed mutation attempts, not
        // just final table state. Never print or export credential-bearing logs.
        sql("SET GLOBAL log_output = 'TABLE'", "SET GLOBAL general_log = 'ON'");
    }

    @AfterAll
    static void stopOwnedServer() throws Exception {
        if (mysql != null) {
            mysql.close();
        }
    }

    @BeforeEach
    void provisionIsolatedCase() throws Exception {
        // Only admin() on the process-owned fresh datadir can execute this reset.
        sql("DROP USER IF EXISTS " + PRINCIPAL,
                "DROP ROLE IF EXISTS 'grant_guard_role'",
                "DROP DATABASE IF EXISTS banking_test",
                "DROP DATABASE IF EXISTS grant_guard_outside",
                "CREATE DATABASE banking_test",
                "CREATE DATABASE grant_guard_outside",
                "SET GLOBAL general_log = 'OFF'",
                "TRUNCATE TABLE mysql.general_log",
                "SET GLOBAL general_log = 'ON'");
        try (Connection admin = mysql.admin();
             var create = admin.prepareStatement("CREATE USER " + PRINCIPAL + " IDENTIFIED BY ?")) {
            create.setString(1, System.getenv("TEST_DB_PASSWORD"));
            create.execute();
        }
        sql("GRANT SELECT, INSERT, CREATE, DROP, REFERENCES ON banking_test.* TO " + PRINCIPAL,
                "CREATE TABLE grant_guard_outside.sentinel (id INT PRIMARY KEY, evidence VARCHAR(80))",
                "INSERT INTO grant_guard_outside.sentinel VALUES (1, 'outside-evidence-unchanged')",
                // Only the verified process-owned server receives this synthetic routine.
                """
                CREATE PROCEDURE grant_guard_outside.read_sentinel()
                READS SQL DATA SQL SECURITY INVOKER
                SELECT evidence FROM grant_guard_outside.sentinel ORDER BY id
                """);
    }

    @Test
    void leastPrivilegeControlReallyCreatesAndRemovesOwnedFixtures() throws Exception {
        var outside = snapshot("grant_guard_outside");
        assertEquals(List.of(), snapshot("banking_test"));
        assertEquals(0, runnerMutationCount());
        try (var database = harness()) {
            database.createFixtures();
            assertEquals(List.of("customers", "login_audit"), tables("banking_test"));
            assertEquals(2, runnerMutationCount(), "The observer must see both real CREATE attempts.");
            seedOwnedEvidence();
        }
        assertEquals(4, runnerMutationCount(), "The observer must also see both real DROP attempts.");
        assertEquals(List.of(), snapshot("banking_test"));
        assertEquals(outside, snapshot("grant_guard_outside"));
    }

    @ParameterizedTest
    // MySQL column grants require an existing table/column. Exercise them in
    // both evidence-bearing lifecycle states below, not a falsely empty schema.
    @EnumSource(value = UnsafeGrant.class, mode = EnumSource.Mode.EXCLUDE,
            names = {"INSIDE_CUSTOMERS_COLUMN", "INSIDE_AUDIT_COLUMN"})
    void refusesBeforeCreatingAnyFixtureInEmptySchema(UnsafeGrant unsafe) throws Exception {
        elevate(unsafe);
        assertRealGrant(unsafe);
        var before = snapshot("banking_test");
        var outside = snapshot("grant_guard_outside");
        long mutations = runnerMutationCount();
        try (var database = harness()) {
            assertGrantRefusal(database::open);
            assertGrantRefusal(database::createFixtures);
        }
        assertEquals(mutations, runnerMutationCount(), "Refusal must precede every fixture write attempt.");
        assertEquals(List.of(), before);
        assertEquals(before, snapshot("banking_test"));
        assertEquals(outside, snapshot("grant_guard_outside"));
    }

    @ParameterizedTest
    @EnumSource(UnsafeGrant.class)
    void refusesBeforeTouchingPreExistingFixtureNamedSentinels(UnsafeGrant unsafe) throws Exception {
        sql("CREATE TABLE banking_test.customers (customer_id INT PRIMARY KEY, evidence VARCHAR(80))",
                "CREATE TABLE banking_test.login_audit (audit_id INT PRIMARY KEY, evidence VARCHAR(80))",
                "INSERT INTO banking_test.customers VALUES (7, 'customer-evidence-unchanged')",
                "INSERT INTO banking_test.login_audit VALUES (9, 'audit-evidence-unchanged')");
        elevate(unsafe);
        assertRealGrant(unsafe);
        var before = snapshot("banking_test");
        var outside = snapshot("grant_guard_outside");
        long mutations = runnerMutationCount();
        try (var database = harness()) {
            // Exact exception distinguishes the grant guard from the nonempty-schema guard.
            assertGrantRefusal(database::open);
            assertGrantRefusal(database::createFixtures);
        }
        assertEquals(mutations, runnerMutationCount(), "No writes may precede the grant refusal.");
        assertEquals(before, snapshot("banking_test"));
        assertEquals(outside, snapshot("grant_guard_outside"));
    }

    @ParameterizedTest
    @EnumSource(UnsafeGrant.class)
    void refusesCleanupWhenOwnedFixturesPrincipalBecomesUnsafe(UnsafeGrant unsafe) throws Exception {
        var database = harness();
        try {
            database.createFixtures();
            seedOwnedEvidence();
            var before = snapshot("banking_test");
            var outside = snapshot("grant_guard_outside");
            long mutations = runnerMutationCount();
            elevate(unsafe);
            assertRealGrant(unsafe);
            assertGrantRefusal(database::close);
            assertEquals(mutations, runnerMutationCount(), "Unsafe cleanup must not even attempt DROP.");
            // Repeated refusal must not clear ownership flags or drop either table.
            assertGrantRefusal(database::close);
            assertEquals(mutations, runnerMutationCount(), "Repeated unsafe cleanup must not attempt writes.");
            assertEquals(before, snapshot("banking_test"));
            assertEquals(outside, snapshot("grant_guard_outside"));
        } finally {
            // Restore the exact safe grants so only the owning harness removes its tables.
            sql("DROP USER " + PRINCIPAL);
            try (Connection admin = mysql.admin();
                 var create = admin.prepareStatement("CREATE USER " + PRINCIPAL + " IDENTIFIED BY ?")) {
                create.setString(1, System.getenv("TEST_DB_PASSWORD"));
                create.execute();
            }
            sql("GRANT SELECT, INSERT, CREATE, DROP, REFERENCES ON banking_test.* TO " + PRINCIPAL);
            database.close();
        }
        assertEquals(List.of(), tables("banking_test"));
    }

    private static LoginAuditTestDatabase harness() {
        return new LoginAuditTestDatabase(Map.of(
                "LOGIN_AUDIT_IT_APPROVED", LoginAuditTestDatabase.APPROVAL,
                "TEST_DB_URL", mysql.url() + "banking_test",
                "TEST_DB_USERNAME", "banking_test_runner",
                "TEST_DB_PASSWORD", System.getenv("TEST_DB_PASSWORD")));
    }

    private static void elevate(UnsafeGrant unsafe) throws Exception {
        switch (unsafe) {
            case GLOBAL_WRITE -> sql("GRANT INSERT ON *.* TO " + PRINCIPAL);
            case OUTSIDE_SCHEMA -> sql("GRANT SELECT, INSERT ON grant_guard_outside.* TO " + PRINCIPAL);
            case OUTSIDE_TABLE -> sql(
                    "GRANT SELECT, INSERT ON grant_guard_outside.sentinel TO " + PRINCIPAL);
            case OUTSIDE_COLUMN -> sql(
                    "GRANT SELECT (evidence), INSERT (evidence) ON grant_guard_outside.sentinel TO " + PRINCIPAL);
            case OUTSIDE_PROCEDURE -> sql(
                    "GRANT EXECUTE ON PROCEDURE grant_guard_outside.read_sentinel TO " + PRINCIPAL);
            // CREATE permits a grant even before the named fixture table exists.
            // This is intentionally refused despite staying inside banking_test:
            // the harness accepts schema-level grants only, not table-level ones.
            case INSIDE_TABLE -> sql(
                    "GRANT SELECT, CREATE ON banking_test.login_audit TO " + PRINCIPAL);
            // Sentinel tables use the same key column names as owned fixtures.
            // Redundant schema privileges do not make column syntax acceptable.
            case INSIDE_CUSTOMERS_COLUMN -> sql(
                    "GRANT SELECT (customer_id), INSERT (customer_id) ON banking_test.customers TO " + PRINCIPAL);
            case INSIDE_AUDIT_COLUMN -> sql(
                    "GRANT SELECT (audit_id), INSERT (audit_id) ON banking_test.login_audit TO " + PRINCIPAL);
            case GRANT_OPTION -> sql("GRANT SELECT ON banking_test.* TO " + PRINCIPAL + " WITH GRANT OPTION");
            case ASSIGNED_ROLE, DEFAULT_ROLE -> {
                sql("CREATE ROLE 'grant_guard_role'",
                        "GRANT INSERT ON grant_guard_outside.* TO 'grant_guard_role'",
                        "GRANT 'grant_guard_role' TO " + PRINCIPAL);
                if (unsafe == UnsafeGrant.DEFAULT_ROLE) {
                    sql("SET DEFAULT ROLE 'grant_guard_role' TO " + PRINCIPAL);
                }
            }
        }
    }

    private static void assertRealGrant(UnsafeGrant unsafe) throws Exception {
        // A raw runner connection proves authentication succeeds under the correctly
        // named principal. Observation bypasses the harness, not its safety guards.
        Properties credentials = new Properties();
        credentials.setProperty("user", "banking_test_runner");
        credentials.setProperty("password", System.getenv("TEST_DB_PASSWORD"));
        credentials.setProperty("connectTimeout", "5000");
        credentials.setProperty("socketTimeout", "5000");
        try (Connection runner = DriverManager.getConnection(mysql.url() + "banking_test", credentials);
             var statement = runner.createStatement()) {
            // MySQL 8 CURRENT_ROLE() is valid even when IDE metadata cannot resolve it.
            //noinspection SqlResolve
            try (var rows = statement.executeQuery("SELECT DATABASE(), CURRENT_USER(), CURRENT_ROLE()")) {
                assertTrue(rows.next());
                assertEquals("banking_test", rows.getString(1));
                assertEquals("banking_test_runner@127.0.0.1", rows.getString(2));
                assertEquals(unsafe == UnsafeGrant.DEFAULT_ROLE, !"NONE".equals(rows.getString(3)));
            }
            List<String> grants = new ArrayList<>();
            try (var rows = statement.executeQuery("SHOW GRANTS")) {
                while (rows.next()) {
                    grants.add(rows.getString(1));
                }
            }
            assertTrue(grants.stream().anyMatch(grant -> switch (unsafe) {
                case GLOBAL_WRITE -> grant.startsWith("GRANT INSERT ON *.* TO ");
                case OUTSIDE_SCHEMA -> grant.contains("ON `grant_guard_outside`.* TO ");
                case OUTSIDE_TABLE -> grant.equals(
                        "GRANT SELECT, INSERT ON `grant_guard_outside`.`sentinel` TO "
                                + "`banking_test_runner`@`127.0.0.1`");
                case OUTSIDE_COLUMN -> grant.equals(
                        "GRANT SELECT (`evidence`), INSERT (`evidence`) ON `grant_guard_outside`.`sentinel` TO "
                                + "`banking_test_runner`@`127.0.0.1`");
                case OUTSIDE_PROCEDURE -> grant.equals(
                        "GRANT EXECUTE ON PROCEDURE `grant_guard_outside`.`read_sentinel` TO "
                                + "`banking_test_runner`@`127.0.0.1`");
                case INSIDE_TABLE -> grant.equals(
                        "GRANT SELECT, CREATE ON `banking_test`.`login_audit` TO "
                                + "`banking_test_runner`@`127.0.0.1`");
                case INSIDE_CUSTOMERS_COLUMN -> grant.equals(
                        "GRANT SELECT (`customer_id`), INSERT (`customer_id`) ON `banking_test`.`customers` TO "
                                + "`banking_test_runner`@`127.0.0.1`");
                case INSIDE_AUDIT_COLUMN -> grant.equals(
                        "GRANT SELECT (`audit_id`), INSERT (`audit_id`) ON `banking_test`.`login_audit` TO "
                                + "`banking_test_runner`@`127.0.0.1`");
                case GRANT_OPTION -> grant.contains("WITH GRANT OPTION");
                case ASSIGNED_ROLE, DEFAULT_ROLE -> grant.startsWith("GRANT `grant_guard_role`@");
            }), "Expected unsafe grant must actually be visible to the runner.");
        }
    }

    private static void assertGrantRefusal(org.junit.jupiter.api.function.Executable operation) {
        assertEquals(REFUSAL, assertThrows(IllegalStateException.class, operation).getMessage());
    }

    private static void seedOwnedEvidence() throws Exception {
        sql("""
                INSERT INTO banking_test.customers
                (customer_code, first_name, last_name, email, username, password_hash)
                VALUES ('grant-sentinel', 'Synthetic', 'Sentinel',
                'sentinel@example.invalid', 'grant-sentinel', 'NOT_AN_AUTHENTICATION_HASH')
                """, """
                INSERT INTO banking_test.login_audit (username_attempted, outcome, created_at)
                VALUES ('grant-sentinel', 'FAILURE', '2026-01-02 03:04:05')
                """);
    }

    private static List<String> tables(String schema) throws Exception {
        List<String> result = new ArrayList<>();
        try (Connection admin = mysql.admin();
             var query = admin.prepareStatement("""
                     SELECT table_name FROM information_schema.tables
                     WHERE table_schema = ? ORDER BY table_name
                     """)) {
            query.setString(1, schema);
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    result.add(rows.getString(1));
                }
            }
        }
        return result;
    }

    private static List<String> snapshot(String schema) throws Exception {
        List<String> result = new ArrayList<>();
        try (Connection admin = mysql.admin(); var statement = admin.createStatement()) {
            for (String table : tables(schema)) {
                // Identifiers come only from the process-owned synthetic schemas.
                String qualified = "`" + schema + "`.`" + table + "`";
                try (var ddl = statement.executeQuery("SHOW CREATE TABLE " + qualified)) {
                    assertTrue(ddl.next());
                    result.add(ddl.getString(2));
                }
                try (var rows = statement.executeQuery("SELECT * FROM " + qualified + " ORDER BY 1")) {
                    int columns = rows.getMetaData().getColumnCount();
                    while (rows.next()) {
                        List<String> row = new ArrayList<>();
                        for (int column = 1; column <= columns; column++) {
                            row.add(rows.getString(column));
                        }
                        result.add(row.toString());
                    }
                }
            }
            // Preserve routine evidence as well as table definitions and rows.
            List<String> procedures = new ArrayList<>();
            try (var query = admin.prepareStatement("""
                    SELECT routine_name FROM information_schema.routines
                    WHERE routine_schema = ? AND routine_type = 'PROCEDURE'
                    ORDER BY routine_name
                    """)) {
                query.setString(1, schema);
                try (var rows = query.executeQuery()) {
                    while (rows.next()) {
                        procedures.add(rows.getString(1));
                    }
                }
            }
            for (String procedure : procedures) {
                try (var ddl = statement.executeQuery(
                        "SHOW CREATE PROCEDURE `" + schema + "`.`" + procedure + "`")) {
                    assertTrue(ddl.next());
                    result.add(ddl.getString("Create Procedure"));
                }
            }
        }
        return result;
    }

    private static void sql(String... commands) throws Exception {
        try (Connection admin = mysql.admin(); var statement = admin.createStatement()) {
            for (String command : commands) {
                statement.execute(command);
            }
        }
    }

    private static long runnerMutationCount() throws Exception {
        try (Connection admin = mysql.admin();
             var statement = admin.createStatement();
             var rows = statement.executeQuery("""
                     SELECT COUNT(*) FROM mysql.general_log
                     WHERE user_host LIKE 'banking_test_runner[%'
                     AND UPPER(argument) REGEXP
                     '^[[:space:]]*(CREATE|DROP|ALTER|TRUNCATE|INSERT|UPDATE|DELETE|REPLACE|GRANT|REVOKE|CALL)[[:space:]]'
                     """)) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }
}
