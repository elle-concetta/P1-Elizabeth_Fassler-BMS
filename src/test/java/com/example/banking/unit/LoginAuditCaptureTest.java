package com.example.banking.unit;

import com.example.banking.model.LoginAudit;
import com.example.banking.model.LoginOutcome;
import com.example.banking.security.LoginAuditCapture;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** Pure capture-policy tests: no authentication, logging, JDBC, or database. */
class LoginAuditCaptureTest {

    private static final Instant TIME = Instant.parse("2026-01-02T03:04:05.123456Z");
    private static final String EMOJI = "\uD83D\uDE00";

    @Test
    void capturesAsciiAndSupplementaryInputsJustBeyondTheLimit() {
        for (String username : new String[]{"a".repeat(81), EMOJI.repeat(81),
                "a".repeat(79) + EMOJI + "b", EMOJI.repeat(80) + "a"}) {
            LoginAudit audit = LoginAuditCapture.failedOversizedUsername(username, TIME);
            assertEquals("[OVERSIZED_USERNAME]", audit.usernameAttempted());
            assertEquals("USERNAME_TOO_LONG", audit.failureReason().orElseThrow());
            assertSame(LoginOutcome.FAILURE, audit.outcome());
            assertSame(TIME, audit.createdAt());
            assertTrue(audit.auditId().isEmpty());
            assertTrue(audit.customerId().isEmpty());
            assertTrue(audit.usernameAttempted().codePointCount(
                    0, audit.usernameAttempted().length()) <= 80);
        }
    }

    @Test
    void requiresExplicitOversizeRatherThanReplacingOrdinaryEvidence() {
        for (String username : new String[]{"", " ", "a".repeat(79), "a".repeat(80),
                EMOJI.repeat(79), EMOJI.repeat(80), "a".repeat(79) + EMOJI,
                "not valid\n", "[OVERSIZED_USERNAME]"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> LoginAuditCapture.failedOversizedUsername(username, TIME));
            LoginAudit exact = new LoginAudit(null, null, username, LoginOutcome.FAILURE,
                    "INVALID_CREDENTIALS", TIME);
            assertEquals(username, exact.usernameAttempted());
            assertEquals("INVALID_CREDENTIALS", exact.failureReason().orElseThrow());
        }
    }

    @Test
    void doesNotRetainPrefixesSuffixesCredentialsOrInjectionCharacters() {
        // Synthetic hostile username, not permission to pass passwords to capture.
        String hostile = "password=TOP_SECRET\r\nFORGED_SUCCESS\t\u0000\u001B[31m\u2028\u2029\u202E"
                + "x".repeat(81) + "SECRET_SUFFIX";
        LoginAudit audit = LoginAuditCapture.failedOversizedUsername(hostile, TIME);
        assertEquals("[OVERSIZED_USERNAME]", audit.usernameAttempted());
        assertEquals("USERNAME_TOO_LONG", audit.failureReason().orElseThrow());
        assertEquals("LoginAudit[outcome=FAILURE]", audit.toString());
    }

    @Test
    void deliberatelyDoesNotFingerprintDifferentOversizedAttempts() {
        LoginAudit first = LoginAuditCapture.failedOversizedUsername("a".repeat(81), TIME);
        LoginAudit second = LoginAuditCapture.failedOversizedUsername(EMOJI.repeat(10_000), TIME);
        assertEquals(first.usernameAttempted(), second.usernameAttempted());
        assertEquals(first.failureReason(), second.failureReason());
        assertEquals(first.toString(), second.toString());
    }

    @Test
    void requiresNonNullInputAndExplicitTimestampWithoutLeakingInput() {
        for (Runnable invalid : new Runnable[]{
                () -> LoginAuditCapture.failedOversizedUsername(null, TIME),
                () -> LoginAuditCapture.failedOversizedUsername("TOP_SECRET\n", TIME),
                () -> LoginAuditCapture.failedOversizedUsername("TOP_SECRET\n".repeat(81), null)}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, invalid::run);
            assertFalse(error.toString().contains("TOP_SECRET"));
            assertFalse(error.toString().contains("\n"));
            assertNull(error.getCause());
            assertEquals(0, error.getSuppressed().length);
        }
    }

    @Test
    void directModelConstructionStillRejectsOversizedInput() {
        for (String username : new String[]{"a".repeat(81), EMOJI.repeat(81),
                "a".repeat(79) + EMOJI + "b"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new LoginAudit(null, null, username, LoginOutcome.FAILURE,
                            "INVALID_CREDENTIALS", TIME));
        }
    }
}

