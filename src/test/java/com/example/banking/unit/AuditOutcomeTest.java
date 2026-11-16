package com.example.banking.unit;

import com.example.banking.model.AuditOutcome;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Reads reference DDL as text only; no SQL execution or database connection. */
class AuditOutcomeTest {

    @Test
    void supportsExactlyTheFourFinancialOutcomes() {
        assertEquals(Set.of("SUCCESS", "REJECTED", "FAILED", "ROLLED_BACK"), names());
    }

    @Test
    void everyPersistedNameRoundTripsExactly() {
        for (AuditOutcome outcome : AuditOutcome.values()) {
            assertSame(outcome, AuditOutcome.valueOf(outcome.name()));
        }
    }

    @Test
    void rejectsUnknownLoginAndLedgerOnlyNames() {
        assertInvalid("UNKNOWN", "FAILURE", "LOCKED", "TIMEOUT", "LOGOUT",
                "PENDING", "REVERSED");
    }

    @Test
    void rejectsNullRatherThanDefaultingToSuccess() {
        assertThrows(NullPointerException.class, () -> AuditOutcome.valueOf(null));
    }

    @Test
    void rejectsEmptyBlankAndSurroundingWhitespace() {
        assertInvalid("", " ", "\t", "\n");
        for (AuditOutcome outcome : AuditOutcome.values()) {
            assertInvalid(" " + outcome.name(), outcome.name() + " ",
                    "\t" + outcome.name(), outcome.name() + "\n");
        }
    }

    @Test
    void rejectsCaseChangesAndUnicodeLookalikes() {
        assertInvalid("success", "Success", "rejected", "failed", "rolled_back",
                "ROLLED-BACK", "ＳＵＣＣＥＳＳ", "SUCC\u0415SS");
    }

    @Test
    void namesFitTheRequiredNonNullSqlColumn() throws IOException {
        Matcher column = Pattern.compile(
                        "(?i)\\boutcome\\s+VARCHAR\\s*\\(\\s*(\\d+)\\s*\\)\\s+NOT\\s+NULL")
                .matcher(transactionAuditDefinition());
        assertTrue(column.find(), "Missing transaction_audit.outcome VARCHAR NOT NULL");
        int width = Integer.parseInt(column.group(1));
        for (String name : names()) {
            assertTrue(name.length() <= width, "Outcome exceeds reference column width");
        }
    }

    @Test
    void persistedNamesMatchTheFinancialAuditCheckExactly() throws IOException {
        Matcher constraint = Pattern.compile(
                        "(?i)\\bCONSTRAINT\\s+chk_transaction_audit_outcome\\s+CHECK\\s*"
                                + "\\(\\s*outcome\\s+IN\\s*\\(([^)]*)\\)\\s*\\)")
                .matcher(transactionAuditDefinition());
        assertTrue(constraint.find(), "Missing financial audit outcome CHECK");
        Set<String> sqlNames = Pattern.compile("'([^']*)'")
                .matcher(constraint.group(1)).results()
                .map(match -> match.group(1)).collect(Collectors.toSet());
        assertEquals(names(), sqlNames);
    }

    private static Set<String> names() {
        return Arrays.stream(AuditOutcome.values())
                .map(Enum::name).collect(Collectors.toSet());
    }

    private static void assertInvalid(String... inputs) {
        for (String input : inputs) {
            assertThrows(IllegalArgumentException.class, () -> AuditOutcome.valueOf(input));
        }
    }

    private static String transactionAuditDefinition() throws IOException {
        // Run from the Maven project root. Never execute this schema.
        String sql = Files.readString(Path.of("sql", "mysql", "schema.sql"))
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)--[^\\r\\n]*", "");
        Matcher table = Pattern.compile(
                        "(?is)CREATE\\s+TABLE\\s+transaction_audit\\s*\\((.*?)\\)\\s*ENGINE\\s*=")
                .matcher(sql);
        assertTrue(table.find(), "Missing transaction_audit reference table");
        return table.group(1);
    }
}
