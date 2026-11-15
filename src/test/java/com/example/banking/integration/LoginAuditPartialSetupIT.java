package com.example.banking.integration;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Independently approved live fixture lifecycle test; no grant escalation or DAO calls. */
class LoginAuditPartialSetupIT {
    private static final String PRINCIPAL = "'banking_test_runner'@'127.0.0.1'";
    private static final List<String> CREATES = List.of("CREATE customers", "CREATE login_audit");
    private static final List<String> CLEANED = List.of(
            "CREATE customers", "CREATE login_audit", "DROP customers");

    @Test
    void reportsOriginalForeignKeyFailureAndClosesOnlySuccessfullyOwnedFixture() throws Exception {
        try (var mysql = DisposableGrantGuardMysql.forPartialSetup()) {
            provision(mysql);
            var outside = snapshot(mysql, "partial_setup_outside", "sentinel");
            assertEquals(List.of(), tables(mysql));
            assertEquals(List.of(), runnerMutations(mysql));

            try (var database = new LoginAuditTestDatabase(Map.of(
                    "LOGIN_AUDIT_IT_APPROVED", LoginAuditTestDatabase.APPROVAL,
                    "TEST_DB_URL", mysql.url() + "banking_test",
                    "TEST_DB_USERNAME", "banking_test_runner",
                    "TEST_DB_PASSWORD", System.getenv("TEST_DB_PASSWORD")))) {
                // This succeeds through the real safety guard, before either DDL.
                assertSafeRunnerWithoutReferences(database);
                SQLException failure = assertThrows(SQLException.class, database::createFixtures);
                assertEquals("42000", failure.getSQLState());
                assertEquals(1142, failure.getErrorCode());
                assertTrue(failure.getMessage().contains("REFERENCES command denied"));
                assertTrue(failure.getMessage().contains("customers"));
                assertNull(failure.getCause(), "The original driver exception must not be wrapped.");
                assertEquals(0, failure.getSuppressed().length);
                assertEquals(List.of("customers"), tables(mysql));
                assertEquals(CREATES, runnerMutations(mysql),
                        "Both CREATEs must be attempted, with no premature cleanup.");
                assertEquals(outside, snapshot(mysql, "partial_setup_outside", "sentinel"));

                // An independently created table with the failed fixture's name is
                // not harness-owned. Cleanup must not infer ownership from its name.
                sql(mysql, """
                        CREATE TABLE banking_test.login_audit
                        (id INT PRIMARY KEY, evidence VARCHAR(80) NOT NULL)
                        """, """
                        INSERT INTO banking_test.login_audit
                        VALUES (7, 'unowned-audit-evidence-unchanged')
                        """);
                var unowned = snapshot(mysql, "banking_test", "login_audit");

                assertDoesNotThrow(database::close);
                assertEquals(List.of("login_audit"), tables(mysql));
                assertEquals(unowned, snapshot(mysql, "banking_test", "login_audit"));
                assertEquals(outside, snapshot(mysql, "partial_setup_outside", "sentinel"));
                assertEquals(CLEANED, runnerMutations(mysql),
                        "Only the successfully created customers fixture may be dropped.");

                assertDoesNotThrow(database::close);
                assertEquals(CLEANED, runnerMutations(mysql), "Repeated close must attempt no mutations.");
                assertEquals(List.of("login_audit"), tables(mysql));
                assertEquals(unowned, snapshot(mysql, "banking_test", "login_audit"));
                assertEquals(outside, snapshot(mysql, "partial_setup_outside", "sentinel"));
                // Cleanup did not replace or change the original failure.
                assertEquals(1142, failure.getErrorCode());
                assertEquals(0, failure.getSuppressed().length);
            }
            assertEquals(CLEANED, runnerMutations(mysql), "Implicit third close is harmless too.");
            assertEquals(List.of("login_audit"), tables(mysql));
            assertEquals(outside, snapshot(mysql, "partial_setup_outside", "sentinel"));
        }
    }

    private static void provision(DisposableGrantGuardMysql mysql) throws Exception {
        // Fresh server only: no resets, existing database URLs, or elevated runner grants.
        sql(mysql, "CREATE DATABASE banking_test", "CREATE DATABASE partial_setup_outside",
                """
                CREATE TABLE partial_setup_outside.sentinel
                (id INT PRIMARY KEY, evidence VARCHAR(80) NOT NULL)
                """, """
                INSERT INTO partial_setup_outside.sentinel
                VALUES (1, 'outside-evidence-unchanged'), (2, 'second-evidence-unchanged')
                """);
        try (Connection admin = mysql.admin();
             var create = admin.prepareStatement("CREATE USER " + PRINCIPAL + " IDENTIFIED BY ?")) {
            create.setString(1, System.getenv("TEST_DB_PASSWORD"));
            create.execute();
        }
        sql(mysql, "GRANT SELECT, INSERT, CREATE, DROP ON banking_test.* TO " + PRINCIPAL,
                "SET GLOBAL log_output = 'TABLE'", "SET GLOBAL general_log = 'ON'");
    }

    private static void assertSafeRunnerWithoutReferences(LoginAuditTestDatabase database) throws Exception {
        try (Connection runner = database.open(); var statement = runner.createStatement()) {
            // MySQL 8 supports CURRENT_ROLE(); some editor SQL catalogs do not resolve it.
            //noinspection SqlResolve
            try (var rows = statement.executeQuery("SELECT DATABASE(), CURRENT_USER(), CURRENT_ROLE()")) {
                assertTrue(rows.next());
                assertEquals("banking_test", rows.getString(1));
                assertEquals("banking_test_runner@127.0.0.1", rows.getString(2));
                assertEquals("NONE", rows.getString(3));
            }
            List<String> grants = new ArrayList<>();
            try (var rows = statement.executeQuery("SHOW GRANTS")) {
                while (rows.next()) {
                    grants.add(rows.getString(1));
                }
            }
            assertEquals(2, grants.size(), "Only USAGE and one schema-level grant are expected.");
            assertTrue(grants.stream().anyMatch(g -> g.startsWith("GRANT USAGE ON *.* TO ")));
            assertTrue(grants.stream().anyMatch(g ->
                    g.startsWith("GRANT SELECT, INSERT, CREATE, DROP ON `banking_test`.* TO ")));
            assertTrue(grants.stream().noneMatch(g ->
                    g.contains("REFERENCES") || g.contains("WITH GRANT OPTION")));
        }
    }

    private static List<String> tables(DisposableGrantGuardMysql mysql) throws Exception {
        List<String> result = new ArrayList<>();
        try (Connection admin = mysql.admin(); var statement = admin.createStatement();
             var rows = statement.executeQuery("""
                     SELECT table_name FROM information_schema.tables
                     WHERE table_schema = 'banking_test' ORDER BY table_name
                     """)) {
            while (rows.next()) {
                result.add(rows.getString(1));
            }
        }
        return result;
    }

    private static List<String> snapshot(DisposableGrantGuardMysql mysql, String schema, String table)
            throws Exception {
        // Callers supply only fixed synthetic identifiers on the process-owned server.
        String qualified = "`" + schema + "`.`" + table + "`";
        List<String> result = new ArrayList<>();
        try (Connection admin = mysql.admin(); var statement = admin.createStatement()) {
            try (var rows = statement.executeQuery("SHOW CREATE TABLE " + qualified)) {
                assertTrue(rows.next());
                result.add(rows.getString(2));
            }
            try (var rows = statement.executeQuery("SELECT * FROM " + qualified + " ORDER BY id")) {
                while (rows.next()) {
                    result.add(rows.getInt("id") + ":" + rows.getString("evidence"));
                }
            }
        }
        return result;
    }

    private static List<String> runnerMutations(DisposableGrantGuardMysql mysql) throws Exception {
        List<String> result = new ArrayList<>();
        try (Connection admin = mysql.admin(); var statement = admin.createStatement();
             var rows = statement.executeQuery("""
                     SELECT CASE
                         WHEN argument LIKE 'CREATE TABLE customers (%' THEN 'CREATE customers'
                         WHEN argument LIKE 'CREATE TABLE login_audit (%' THEN 'CREATE login_audit'
                         WHEN argument = 'DROP TABLE banking_test.customers' THEN 'DROP customers'
                         ELSE 'UNEXPECTED MUTATION' END
                     FROM mysql.general_log
                     WHERE user_host LIKE 'banking_test_runner[%'
                     AND UPPER(argument) REGEXP
                     '^[[:space:]]*(CREATE|DROP|ALTER|TRUNCATE|INSERT|UPDATE|DELETE|REPLACE|GRANT|REVOKE)[[:space:]]'
                     ORDER BY event_time
                     """)) {
            while (rows.next()) {
                result.add(rows.getString(1));
            }
        }
        return result;
    }

    private static void sql(DisposableGrantGuardMysql mysql, String... commands) throws Exception {
        try (Connection admin = mysql.admin(); var statement = admin.createStatement()) {
            for (String command : commands) {
                statement.execute(command);
            }
        }
    }
}
