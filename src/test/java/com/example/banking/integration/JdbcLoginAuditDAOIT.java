package com.example.banking.integration;

import com.example.banking.dao.jdbc.JdbcLoginAuditDAO;
import com.example.banking.exception.PersistenceException;
import com.example.banking.model.LoginAudit;
import com.example.banking.model.LoginOutcome;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL only. Missing approval/configuration fails, rather than skipping. */
class JdbcLoginAuditDAOIT {
    // TIMESTAMP(0) in the checked-in schema: only whole-second precision claimed.
    private static final Instant TIME = Instant.parse("2026-01-02T03:04:05Z");
    private static LoginAuditTestDatabase database;
    private static JdbcLoginAuditDAO dao;

    @BeforeAll
    static void setup() throws Exception {
        database = new LoginAuditTestDatabase(System.getenv());
        database.createFixtures();
        dao = new JdbcLoginAuditDAO(database.factory());
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void refusesNonemptySchemaWithoutClaimingOrDeletingExistingTables() throws Exception {
        dao.append(failure("guard-evidence", null, null));
        try (var secondHarness = new LoginAuditTestDatabase(System.getenv())) {
            assertThrows(IllegalStateException.class, secondHarness::createFixtures);
        }
        assertRow("guard-evidence", null, null);
    }

    @Test
    void committedFailureSurvivesIndependentPrimaryRollback() throws Exception {
        try (Connection primary = database.open()) {
            primary.setAutoCommit(false);
            try {
                insertCustomer(primary, "rolled-back");
                assertEquals(1, customerCount(primary, "rolled-back"));
                long primaryId = connectionId(primary);
                try (Connection independent = database.open()) {
                    assertNotEquals(primaryId, connectionId(independent));
                    try (var statement = independent.createStatement();
                         var result = statement.executeQuery("SELECT @@session.time_zone")) {
                        assertTrue(result.next());
                        assertEquals("+00:00", result.getString(1));
                    }
                }
                dao.append(failure("rollback-evidence", null, "INVALID_CREDENTIALS"));
                // Separate observer sees the committed evidence while primary is open.
                assertRow("rollback-evidence", null, "INVALID_CREDENTIALS");
            } finally {
                primary.rollback();
            }
        }
        try (Connection observer = database.open()) {
            assertEquals(0, customerCount(observer, "rolled-back"),
                    "The primary fixture really must have rolled back.");
        }
        // A fresh connection after rollback, not a cached ResultSet.
        assertRow("rollback-evidence", null, "INVALID_CREDENTIALS");
    }

    @Test
    void unknownCustomerAndAbsentReasonPersistAsSqlNull() throws Exception {
        dao.append(failure(" unknown ' OR 1=1 -- ", null, null));
        assertRow(" unknown ' OR 1=1 -- ", null, null);
    }

    @Test
    void committedKnownCustomerReferenceIsValidAndOrphanIsRejected() throws Exception {
        long customer;
        try (Connection connection = database.open()) {
            customer = insertCustomer(connection, "known");
        }
        dao.append(failure("known-attempt", customer, "INVALID_CREDENTIALS"));
        assertRow("known-attempt", customer, "INVALID_CREDENTIALS");
        assertThrows(PersistenceException.class,
                () -> dao.append(failure("orphan-attempt", Long.MAX_VALUE, null)));
        try (Connection observer = database.open();
             var query = observer.prepareStatement(
                     "SELECT COUNT(*) FROM login_audit WHERE username_attempted = ?")) {
            query.setString(1, "orphan-attempt");
            try (var result = query.executeQuery()) {
                assertTrue(result.next());
                assertEquals(0, result.getLong(1));
            }
        }
        assertRow("known-attempt", customer, "INVALID_CREDENTIALS");
    }

    @ParameterizedTest
    @CsvSource({"+00:00, GMT", "-05:00, GMT-05:00", "+05:30, GMT+05:30"})
    void utcWriterRoundTripsAcrossReaderSessionZones(String offset, String zone) throws Exception {
        String attempted = "zone-" + offset;
        dao.append(failure(attempted, null, null));
        try (Connection reader = database.open()) {
            try (var statement = reader.prepareStatement("SET SESSION time_zone = ?")) {
                statement.setString(1, offset);
                statement.execute();
            }
            try (var query = reader.prepareStatement("""
                    SELECT created_at, UNIX_TIMESTAMP(created_at), @@session.time_zone
                    FROM login_audit WHERE username_attempted = ?
                    """)) {
                query.setString(1, attempted);
                try (var result = query.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(offset, result.getString(3));
                    assertEquals(TIME.getEpochSecond(), result.getLong(2));
                    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(zone));
                    assertEquals(TIME, result.getTimestamp(1, calendar).toInstant());
                    assertFalse(result.next());
                }
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
            "1970-01-01T00:00:01Z, +00:00, GMT",
            "1970-01-01T00:00:01Z, -05:00, GMT-05:00",
            "1970-01-01T00:00:01Z, +05:30, GMT+05:30",
            "2038-01-19T03:14:07Z, +00:00, GMT",
            "2038-01-19T03:14:07Z, -05:00, GMT-05:00",
            "2038-01-19T03:14:07Z, +05:30, GMT+05:30"
    })
    void exactTimestampBoundsRoundTripAcrossReaderSessionZones(
            String text, String offset, String zone) throws Exception {
        Instant time = Instant.parse(text);
        String attempted = "bound-" + time.getEpochSecond() + "-" + offset;
        dao.append(new LoginAudit(null, null, attempted, LoginOutcome.FAILURE, null, time));
        try (Connection reader = database.open()) {
            try (var statement = reader.prepareStatement("SET SESSION time_zone = ?")) {
                statement.setString(1, offset);
                statement.execute();
            }
            try (var query = reader.prepareStatement("""
                    SELECT created_at, UNIX_TIMESTAMP(created_at), @@session.time_zone
                    FROM login_audit WHERE username_attempted = ?
                    """)) {
                query.setString(1, attempted);
                try (var result = query.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(offset, result.getString(3));
                    assertEquals(time, result.getTimestamp(1,
                            Calendar.getInstance(TimeZone.getTimeZone(zone))).toInstant());
                    assertEquals(time.getEpochSecond(), result.getLong(2));
                    assertFalse(result.next());
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-01-02T03:04:05.000000001Z", "2026-01-02T03:04:05.123456Z",
            "2026-01-02T03:04:05.999999999Z",
            "1970-01-01T00:00:00.999999999Z", "2038-01-19T03:14:07.000000001Z",
            "1970-01-01T00:00:00Z", "2038-01-19T03:14:08Z"
    })
    void unsupportedTimeLeavesEarlierEvidenceAndRowCountUnchanged(String text) throws Exception {
        String attempted = "rejected-" + text;
        String prior = "prior-" + text;
        dao.append(failure(prior, null, "INVALID_CREDENTIALS"));
        long before = auditCount();
        Instant time = Instant.parse(text);
        LoginAudit audit = new LoginAudit(null, null, attempted, LoginOutcome.FAILURE, null, time);
        assertThrows(IllegalArgumentException.class, () -> dao.append(audit));
        assertEquals(time, audit.createdAt());
        assertEquals(before, auditCount());
        assertRow(prior, null, "INVALID_CREDENTIALS");
        try (Connection reader = database.open();
             var query = reader.prepareStatement(
                     "SELECT COUNT(*) FROM login_audit WHERE username_attempted = ?")) {
            query.setString(1, attempted);
            try (var result = query.executeQuery()) {
                assertTrue(result.next());
                assertEquals(0, result.getLong(1));
            }
        }
    }

    private static long auditCount() throws Exception {
        try (Connection reader = database.open();
             var query = reader.createStatement();
             var result = query.executeQuery("SELECT COUNT(*) FROM login_audit")) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private static LoginAudit failure(String username, Long customer, String reason) {
        return new LoginAudit(null, customer, username, LoginOutcome.FAILURE, reason, TIME);
    }

    private static long insertCustomer(Connection connection, String label) throws Exception {
        try (var insert = connection.prepareStatement("""
                INSERT INTO customers
                    (customer_code, first_name, last_name, email, username, password_hash)
                VALUES (?, 'Fixture', 'Only', ?, ?, 'NOT_AN_AUTHENTICATION_HASH')
                """, Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, label);
            insert.setString(2, label + "@example.invalid");
            insert.setString(3, label);
            assertEquals(1, insert.executeUpdate());
            try (var keys = insert.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    private static long customerCount(Connection connection, String label) throws Exception {
        try (var query = connection.prepareStatement(
                "SELECT COUNT(*) FROM customers WHERE customer_code = ?")) {
            query.setString(1, label);
            try (var result = query.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private static long connectionId(Connection connection) throws Exception {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT CONNECTION_ID()")) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private static void assertRow(String attempted, Long customer, String reason) throws Exception {
        try (Connection reader = database.open();
             var query = reader.prepareStatement("""
                     SELECT audit_id, customer_id, username_attempted, outcome,
                            failure_reason, created_at, ip_address, device_info
                     FROM login_audit WHERE username_attempted = ?
                     """)) {
            query.setString(1, attempted);
            try (var result = query.executeQuery()) {
                assertTrue(result.next());
                assertTrue(result.getLong("audit_id") > 0);
                assertEquals(customer, result.getObject("customer_id", Long.class));
                assertEquals(attempted, result.getString("username_attempted"));
                assertEquals("FAILURE", result.getString("outcome"));
                assertEquals(reason, result.getString("failure_reason"));
                assertEquals(TIME, result.getTimestamp("created_at",
                        Calendar.getInstance(TimeZone.getTimeZone("UTC"))).toInstant());
                assertNull(result.getObject("ip_address"));
                assertNull(result.getObject("device_info"));
                assertFalse(result.next(), "Expected exactly one committed audit row.");
            }
        }
    }
}
