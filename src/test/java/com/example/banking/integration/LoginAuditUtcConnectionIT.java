package com.example.banking.integration;

import com.mysql.cj.jdbc.ServerPreparedStatement;
import com.example.banking.dao.jdbc.JdbcLoginAuditDAO;
import com.example.banking.model.LoginAudit;
import com.example.banking.model.LoginOutcome;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Separate opt-in run: dedicated server must start with
 * --default-time-zone=+05:30. Never changes GLOBAL settings or reads DB_*.
 * JVM defaults are temporarily changed, so no other test may run concurrently.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class LoginAuditUtcConnectionIT {
    private static LoginAuditTestDatabase database;

    @BeforeAll
    static void setup() throws Exception {
        database = new LoginAuditTestDatabase(System.getenv());
        // Prove this is really a non-UTC default, not a test that manually
        // changes writer session state to simulate it.
        try (Connection ordinary = database.factory().getConnection();
             var query = ordinary.createStatement();
             var row = query.executeQuery("SELECT @@global.time_zone, @@session.time_zone")) {
            assertTrue(row.next());
            assertEquals("+05:30", row.getString(1), "Requires isolated non-UTC server default");
            assertEquals("+05:30", row.getString(2), "Ordinary factory must preserve default");
        }
        database.createFixtures();
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (database != null) {
            database.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1970-01-01T00:00:01Z", "2026-01-02T03:04:05Z",
            "2026-03-08T07:00:00Z", "2026-11-01T06:00:00Z",
            "2038-01-19T03:14:07Z"
    })
    void utcWriterPreservesInstantDespiteNonUtcServerDefault(String text) throws Exception {
        Instant time = Instant.parse(text);
        String attempted = "utc-policy-" + time.getEpochSecond();
        // Uses the actual application factory policy, not harness UTC properties.
        new JdbcLoginAuditDAO(database.factory()).append(
                new LoginAudit(null, null, attempted, LoginOutcome.FAILURE, null, time));

        try (Connection reader = database.open();
             var query = reader.prepareStatement("""
                     SELECT UNIX_TIMESTAMP(created_at), created_at, @@session.time_zone
                     FROM login_audit WHERE username_attempted = ?
                     """)) {
            query.setString(1, attempted);
            try (var row = query.executeQuery()) {
                assertTrue(row.next());
                assertEquals(time.getEpochSecond(), row.getLong(1));
                assertEquals(time, row.getTimestamp(2,
                        Calendar.getInstance(TimeZone.getTimeZone("UTC"))).toInstant());
                assertEquals("+00:00", row.getString(3));
                assertFalse(row.next());
            }
        }
        // UTC setup must not change the server default or subsequent business sessions.
        try (Connection ordinary = database.factory().getConnection();
             var query = ordinary.createStatement();
             var row = query.executeQuery("SELECT @@global.time_zone, @@session.time_zone")) {
            assertTrue(row.next());
            assertEquals("+05:30", row.getString(1));
            assertEquals("+05:30", row.getString(2));
        }
    }

    static Stream<Arguments> conflictingTimeZones() {
        // Includes numeric east/west connectionTimeZone values forced into the
        // session by Connector/J, without sessionVariables masking that behavior.
        return Stream.of("America/Los_Angeles", "Asia/Kolkata").flatMap(jvmZone ->
                Stream.of(LoginAuditTestDatabase.DriverTimeZones.values())
                        .filter(options -> options != LoginAuditTestDatabase.DriverTimeZones.DEFAULT)
                        .flatMap(options -> Stream.of(
                                "1970-01-01T00:00:01Z", "2026-01-02T03:04:05Z",
                                "2026-03-08T07:00:00Z", "2026-11-01T06:00:00Z",
                                "2038-01-19T03:14:07Z")
                                .map(time -> Arguments.of(jvmZone, options, time))));
    }

    @ParameterizedTest(name = "{0}, {1}, {2}")
    @MethodSource("conflictingTimeZones")
    void utcWriterOverridesConflictingDriverOptions(
            String jvmZone, LoginAuditTestDatabase.DriverTimeZones options, String text
    ) throws Exception {
        assertConflictingConfiguration(jvmZone, options,
                LoginAuditTestDatabase.PreparedStatements.DEFAULT, text);
    }

    static Stream<Arguments> preparedStatementModes() {
        // Bounded protocol comparison, not another timezone/runtime matrix.
        return Stream.of(LoginAuditTestDatabase.PreparedStatements.CLIENT,
                        LoginAuditTestDatabase.PreparedStatements.SERVER)
                .flatMap(mode -> Stream.of(
                                "1970-01-01T00:00:01Z", "2026-03-08T07:00:00Z",
                                "2038-01-19T03:14:07Z")
                        .map(time -> Arguments.of(mode, time)));
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("preparedStatementModes")
    void utcWriterPreservesTimeWithClientAndServerPreparedStatements(
            LoginAuditTestDatabase.PreparedStatements mode, String text
    ) throws Exception {
        assertConflictingConfiguration("America/Los_Angeles",
                LoginAuditTestDatabase.DriverTimeZones.TOKYO_WEST, mode, text);
    }

    private void assertConflictingConfiguration(
            String jvmZone, LoginAuditTestDatabase.DriverTimeZones options,
            LoginAuditTestDatabase.PreparedStatements mode, String text
    ) throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(jvmZone));
            assertEquals(jvmZone, TimeZone.getDefault().getID());
            // Same approval and target guards as the fixture owner. This instance
            // owns no tables and never creates or reuses fixtures on its own.
            try (var configured = new LoginAuditTestDatabase(System.getenv(), options, mode);
                 Connection existingBusiness = configured.factory().getConnection()) {
                assertSession(existingBusiness, options.sessionTimeZone);
                // Exercise the application policy directly after the driver's
                // connect-time session setup, including forced numeric offsets.
                try (Connection writer = configured.factory().getLoginAuditConnection()) {
                    assertSession(writer, "+00:00");
                    assertPreparedStatementMode(writer, mode);
                }
                Instant time = Instant.parse(text);
                String attempted = "driver-" + jvmZone.hashCode() + "-"
                        + options.ordinal() + "-" + mode.name() + "-" + time.getEpochSecond();
                new JdbcLoginAuditDAO(configured.factory()).append(
                        new LoginAudit(null, null, attempted, LoginOutcome.FAILURE, null, time));

                // Read with both the conflicting driver configuration and the
                // default configuration. All readers use UTC, not an offset matrix.
                try (Connection reader = configured.open()) {
                    assertPersistedTime(reader, attempted, time);
                }
                try (Connection reader = database.open()) {
                    assertPersistedTime(reader, attempted, time);
                }
                // Audit setup must affect neither existing nor fresh business
                // sessions. Driver-requested ordinary session settings survive.
                assertSession(existingBusiness, options.sessionTimeZone);
                try (Connection freshBusiness = configured.factory().getConnection()) {
                    assertSession(freshBusiness, options.sessionTimeZone);
                }
                try (Connection unconfiguredBusiness = database.factory().getConnection()) {
                    assertSession(unconfiguredBusiness, "+05:30");
                }
            }
        } finally {
            TimeZone.setDefault(previous);
        }
        assertEquals(previous, TimeZone.getDefault());
    }

    private static void assertPreparedStatementMode(
            Connection connection, LoginAuditTestDatabase.PreparedStatements mode
    ) throws Exception {
        if (mode == LoginAuditTestDatabase.PreparedStatements.DEFAULT) {
            return;
        }
        // Verify a real parameterized statement, not just the requested property.
        // A silent driver fallback must not count as server-protocol coverage.
        try (var statement = connection.prepareStatement("SELECT ?")) {
            assertEquals(mode == LoginAuditTestDatabase.PreparedStatements.SERVER,
                    statement instanceof ServerPreparedStatement);
            statement.setInt(1, 1);
            try (var row = statement.executeQuery()) {
                assertTrue(row.next());
                assertEquals(1, row.getInt(1));
                assertFalse(row.next());
            }
        }
    }

    private static void assertPersistedTime(Connection reader, String attempted, Instant time)
            throws Exception {
        assertSession(reader, "+00:00");
        try (var query = reader.prepareStatement("""
                SELECT UNIX_TIMESTAMP(created_at), created_at
                FROM login_audit WHERE username_attempted = ?
                """)) {
            query.setString(1, attempted);
            try (var row = query.executeQuery()) {
                assertTrue(row.next());
                assertEquals(time.getEpochSecond(), row.getLong(1));
                assertFalse(row.wasNull(), "Persisted epoch must not be SQL NULL");
                assertEquals(time, row.getTimestamp(2,
                        Calendar.getInstance(TimeZone.getTimeZone("UTC"))).toInstant());
                assertFalse(row.next());
            }
        }
    }

    private static void assertSession(Connection connection, String expected) throws Exception {
        try (var query = connection.createStatement();
             var row = query.executeQuery("SELECT @@global.time_zone, @@session.time_zone")) {
            assertTrue(row.next());
            assertEquals("+05:30", row.getString(1), "Global default must remain unchanged");
            assertEquals(expected, row.getString(2));
            assertFalse(row.next());
        }
    }
}
