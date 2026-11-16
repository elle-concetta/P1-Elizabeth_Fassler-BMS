package com.example.banking.unit;

import com.example.banking.model.LoginAudit;
import com.example.banking.model.LoginOutcome;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the LoginAudit model without connecting to MySQL.
 * The schema check reads schema.sql as text; it does not execute SQL.
 */
class LoginAuditTest {

    private static final Instant TIME =
            Instant.parse("2026-01-02T03:04:05.123456Z");

    @Test
    void retainsExactSuppliedFields() {
        LoginAudit audit = new LoginAudit(7L, 11L, " Alice ", LoginOutcome.FAILURE,
                "INVALID_CREDENTIALS", TIME);
        assertAll(
                () -> assertEquals(7L, audit.auditId().orElseThrow()),
                () -> assertEquals(11L, audit.customerId().orElseThrow()),
                () -> assertEquals(" Alice ", audit.usernameAttempted()),
                () -> assertSame(LoginOutcome.FAILURE, audit.outcome()),
                () -> assertEquals(
                        "INVALID_CREDENTIALS",
                        audit.failureReason().orElseThrow()
                ),
                () -> assertSame(TIME, audit.createdAt())
        );
    }

    @Test
    void allowsUnknownCustomerAndUnpersistedIdentityWithoutInventingIds() {
        LoginAudit audit = attempt("unknown", LoginOutcome.FAILURE, null);
        assertAll(
                () -> assertTrue(audit.auditId().isEmpty()),
                () -> assertTrue(audit.customerId().isEmpty()),
                () -> assertTrue(audit.failureReason().isEmpty())
        );
    }

    @Test
    void acceptsPositiveIdentityBoundaries() {
        for (long id : new long[]{1, Long.MAX_VALUE}) {
            LoginAudit audit = new LoginAudit(id, id, "alice", LoginOutcome.SUCCESS, null, TIME);
            assertEquals(id, audit.auditId().orElseThrow());
            assertEquals(id, audit.customerId().orElseThrow());
        }
    }

    @Test
    void rejectsNonpositiveAuditIds() {
        for (long id : new long[]{0, -1, Long.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new LoginAudit(id, null, "alice", LoginOutcome.FAILURE, null, TIME));
        }
    }

    @Test
    void rejectsNonpositiveCustomerIds() {
        for (long id : new long[]{0, -1, Long.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new LoginAudit(null, id, "alice", LoginOutcome.FAILURE, null, TIME));
        }
    }

    @Test
    void requiresUsernameWithoutEchoingInput() {
        assertThrows(IllegalArgumentException.class, () -> attempt(null, LoginOutcome.FAILURE, null));
    }

    @Test
    void preservesEmptyWhitespaceAndInvalidRegistrationAttempts() {
        for (String username : new String[]{"", " ", " Alice ", "not a valid username", "a\nb"}) {
            assertEquals(username, attempt(username, LoginOutcome.FAILURE, null).usernameAttempted());
        }
    }

    @Test
    void acceptsUsernameWidthBoundaryIncludingSupplementaryCharacters() {
        for (String username : new String[]{"a", "a".repeat(80), "\uD83D\uDE00".repeat(80)}) {
            assertEquals(username, attempt(username, LoginOutcome.FAILURE, null).usernameAttempted());
        }
    }

    @Test
    void rejectsOverlongUsernamesWithoutTruncation() {
        for (String username : new String[]{"a".repeat(81), "\uD83D\uDE00".repeat(81)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> attempt(username, LoginOutcome.FAILURE, null));
        }
    }

    @Test
    void requiresOutcomeWithoutDefaultingToSuccess() {
        assertThrows(IllegalArgumentException.class, () -> attempt("alice", null, null));
    }

    @Test
    void requiresExplicitTimestamp() {
        assertThrows(IllegalArgumentException.class,
                () -> new LoginAudit(null, null, "alice", LoginOutcome.FAILURE, null, null));
    }

    @Test
    void acceptsAllLoginOutcomesWithNoReason() {
        for (LoginOutcome outcome : LoginOutcome.values()) {
            LoginAudit audit = attempt("alice", outcome, null);
            assertSame(outcome, audit.outcome());
            assertTrue(audit.failureReason().isEmpty());
        }
    }

    @Test
    void acceptsExactControlledReasonSyntaxForFailureAndLockout() {
        for (LoginOutcome outcome : new LoginOutcome[]{LoginOutcome.FAILURE, LoginOutcome.LOCKED}) {
            for (String reason : new String[]{"A", "INVALID_CREDENTIALS", "LOCKED_123", "A".repeat(40)}) {
                assertEquals(reason, attempt("alice", outcome, reason).failureReason().orElseThrow());
            }
        }
    }

    @Test
    void rejectsReasonsOnSuccessTimeoutAndLogout() {
        for (LoginOutcome outcome : new LoginOutcome[]{
                LoginOutcome.SUCCESS, LoginOutcome.TIMEOUT, LoginOutcome.LOGOUT}) {
            for (String reason : new String[]{"INVALID_CREDENTIALS", "", " "}) {
                assertThrows(IllegalArgumentException.class, () -> attempt("alice", outcome, reason));
            }
        }
    }

    @Test
    void rejectsUnstructuredOrOverlongReasonsRatherThanNormalizing() {
        for (LoginOutcome outcome : new LoginOutcome[]{LoginOutcome.FAILURE, LoginOutcome.LOCKED}) {
            for (String reason : new String[]{"", " ", "A".repeat(41), "lowercase", "_CODE",
                    "1CODE", " CODE", "CODE ", "CODE\n", "A-B", "ÉRROR", "A\u0000B",
                    "password=secret", "SQL error: SELECT * FROM customers"}) {
                assertThrows(IllegalArgumentException.class, () -> attempt("alice", outcome, reason));
            }
        }
    }

    @Test
    void errorsDoNotExposeRejectedValuesOrAttachCauses() {
        // Synthetic test data, not an actual credential.
        String secret = "password=TOP_SECRET\nSELECT * FROM customers";
        for (Runnable invalid : new Runnable[]{
                () -> attempt(secret.repeat(4), LoginOutcome.FAILURE, null),
                () -> attempt("alice", LoginOutcome.FAILURE, secret)}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, invalid::run);
            assertAll(
                    () -> assertFalse(error.toString().contains("TOP_SECRET")),
                    () -> assertFalse(error.toString().contains("SELECT")),
                    () -> assertNull(error.getCause()),
                    () -> assertEquals(0, error.getSuppressed().length)
            );
        }
    }

    @Test
    void rendersOnlyTheClosedOutcomeName() {
        LoginAudit audit = new LoginAudit(987654L, 123456L, "private_username\n",
                LoginOutcome.FAILURE, "PRIVATE_REASON", TIME);
        assertEquals("LoginAudit[outcome=FAILURE]", audit.toString());
    }

    @Test
    void stateIsPrivateFinalAndExposesNoMutationApi() {
        assertTrue(Modifier.isFinal(LoginAudit.class.getModifiers()));
        Arrays.stream(LoginAudit.class.getDeclaredFields()).forEach(field -> {
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
        });
        Set<String> methods = Arrays.stream(LoginAudit.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .peek(method -> assertEquals(0, method.getParameterCount()))
                .map(method -> method.getName()).collect(Collectors.toSet());
        assertEquals(Set.of("auditId", "customerId", "usernameAttempted", "outcome",
                "failureReason", "createdAt", "toString"), methods);
    }

    @Test
    void accessorUseCannotChangeTheRecord() {
        LoginAudit audit = attempt("alice", LoginOutcome.FAILURE, "INVALID_CREDENTIALS");
        audit.createdAt().plusSeconds(60);
        audit.usernameAttempted().toUpperCase();
        audit.failureReason().map(String::toLowerCase);
        assertAll(
                () -> assertEquals(TIME, audit.createdAt()),
                () -> assertEquals("alice", audit.usernameAttempted()),
                () -> assertEquals(
                        "INVALID_CREDENTIALS",
                        audit.failureReason().orElseThrow()
                )
        );
    }

    @Test
    void matchesReferenceSchemaWidthsAndNullabilityWithoutExecutingSql() throws Exception {
        String schema = Files.readString(Path.of("sql/mysql/schema.sql"));
        var table = Pattern.compile("(?is)CREATE\\s+TABLE\\s+login_audit\\s*\\((.*?)\\)\\s*ENGINE")
                .matcher(schema);
        assertTrue(table.find(), "schema.sql should define the login_audit table.");
        String definition = table.group(1);
        for (String column : new String[]{
                "audit_id\\s+BIGINT\\s+PRIMARY\\s+KEY\\s+AUTO_INCREMENT",
                "customer_id\\s+BIGINT\\s+NULL",
                "username_attempted\\s+VARCHAR\\(80\\)\\s+NOT\\s+NULL",
                "outcome\\s+VARCHAR\\(20\\)\\s+NOT\\s+NULL",
                "failure_reason\\s+VARCHAR\\(255\\)\\s+NULL",
                "created_at\\s+TIMESTAMP\\s+NOT\\s+NULL"}) {
            assertTrue(
                    Pattern.compile("(?i)\\b" + column).matcher(definition).find(),
                    "Missing or unexpected column definition: " + column
            );
        }
        for (LoginOutcome outcome : LoginOutcome.values()) {
            assertTrue(outcome.name().length() <= 20);
        }
    }

    private static LoginAudit attempt(String username, LoginOutcome outcome, String reason) {
        return new LoginAudit(null, null, username, outcome, reason, TIME);
    }
}
