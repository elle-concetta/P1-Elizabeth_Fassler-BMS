package com.example.banking.unit;

import com.example.banking.model.LoginOutcome;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Reads reference DDL as text only; no SQL execution or database connection. */
class LoginOutcomeTest {

    @Test
    void supportsExactlyTheFiveLoginOutcomes() {
        assertEquals(Set.of("SUCCESS", "FAILURE", "LOCKED", "TIMEOUT", "LOGOUT"), names());
    }

    @Test
    void everyPersistedNameRoundTripsExactly() {
        for (LoginOutcome outcome : LoginOutcome.values()) {
            assertSame(outcome, LoginOutcome.valueOf(outcome.name()));
        }
    }

    @Test
    void rejectsUnknownFinancialAndLedgerOnlyNames() {
        assertInvalid("UNKNOWN", "REJECTED", "FAILED", "ROLLED_BACK",
                "PENDING", "REVERSED", "PASSWORD_CHANGED");
    }

    @Test
    void rejectsNullRatherThanDefaultingToSuccess() {
        assertThrows(NullPointerException.class, () -> LoginOutcome.valueOf(null));
    }

    @Test
    void rejectsEmptyBlankAndSurroundingWhitespace() {
        assertInvalid("", " ", "\t", "\n");
        for (LoginOutcome outcome : LoginOutcome.values()) {
            assertInvalid(" " + outcome.name(), outcome.name() + " ",
                    "\t" + outcome.name(), outcome.name() + "\n");
        }
    }

    @Test
    void rejectsCaseChangesAndUnicodeLookalikes() {
        for (LoginOutcome outcome : LoginOutcome.values()) {
            assertInvalid(outcome.name().toLowerCase(Locale.ROOT));
        }
        assertInvalid("Success", "Failure", "Locked", "Timeout", "Logout",
                "TIME-OUT", "LOG_OUT", "ＳＵＣＣＥＳＳ", "SUCC\u0415SS");
    }

    @Test
    void namesFitTheRequiredNonNullSqlColumn() throws IOException {
        Matcher column = Pattern.compile(
                        "(?i)\\boutcome\\s+VARCHAR\\s*\\(\\s*(\\d+)\\s*\\)\\s+NOT\\s+NULL")
                .matcher(loginAuditDefinition());
        assertTrue(column.find(), "Missing login_audit.outcome VARCHAR NOT NULL");
        int width = Integer.parseInt(column.group(1));
        for (String name : names()) {
            assertTrue(name.length() <= width, "Outcome exceeds reference column width");
        }
    }

    @Test
    void persistedNamesMatchTheLoginAuditCheckExactly() throws IOException {
        Matcher constraint = Pattern.compile(
                        "(?i)\\bCONSTRAINT\\s+chk_audit_outcome\\s+CHECK\\s*"
                                + "\\(\\s*outcome\\s+IN\\s*\\(([^)]*)\\)\\s*\\)")
                .matcher(loginAuditDefinition());
        assertTrue(constraint.find(), "Missing login audit outcome CHECK");
        Set<String> sqlNames = Pattern.compile("'([^']*)'")
                .matcher(constraint.group(1)).results()
                .map(match -> match.group(1)).collect(Collectors.toSet());
        assertEquals(names(), sqlNames);
    }

    private static Set<String> names() {
        return Arrays.stream(LoginOutcome.values())
                .map(Enum::name).collect(Collectors.toSet());
    }

    private static void assertInvalid(String... inputs) {
        for (String input : inputs) {
            assertThrows(IllegalArgumentException.class, () -> LoginOutcome.valueOf(input));
        }
    }

    private static String loginAuditDefinition() throws IOException {
        // Run from the Maven project root. Never execute this schema.
        String sql = Files.readString(Path.of("sql", "mysql", "schema.sql"))
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)--[^\\r\\n]*", "");
        Matcher table = Pattern.compile(
                        "(?is)CREATE\\s+TABLE\\s+login_audit\\s*\\((.*?)\\)\\s*ENGINE\\s*=")
                .matcher(sql);
        assertTrue(table.find(), "Missing login_audit reference table");
        return table.group(1);
    }
}
