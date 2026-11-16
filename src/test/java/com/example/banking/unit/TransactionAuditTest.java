package com.example.banking.unit;

import com.example.banking.model.AuditOutcome;
import com.example.banking.model.TransactionAudit;
import com.example.banking.model.TransactionReference;
import com.example.banking.model.TransactionType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Pure construction tests; SQL is inspected as text only, never executed. */
class TransactionAuditTest {

    private static final Instant TIME = Instant.parse("2026-01-02T03:04:05.123456Z");
    private static final TransactionReference REFERENCE =
            new TransactionReference("TX-20260102-0123456789ABCDEF0123456789AB");

    @Test
    void constructsSuccessfulAttemptWithExactSuppliedFields() {
        TransactionAudit audit = create(7L, 11, REFERENCE, TransactionType.TRANSFER,
                AuditOutcome.SUCCESS, null, TIME);
        assertEquals(7, audit.auditId().orElseThrow());
        assertEquals(11, audit.customerId());
        assertEquals(REFERENCE, audit.referenceNumber().orElseThrow());
        assertSame(TransactionType.TRANSFER, audit.eventType());
        assertSame(AuditOutcome.SUCCESS, audit.outcome());
        assertTrue(audit.failureCode().isEmpty());
        assertEquals(TIME, audit.createdAt());
    }

    @Test
    void allowsUnpersistedAttemptWithoutInventingAnId() {
        assertTrue(attempt(AuditOutcome.SUCCESS, REFERENCE, null).auditId().isEmpty());
    }

    @Test
    void allowsPositiveIdBoundaries() {
        for (long id : new long[]{1, Long.MAX_VALUE}) {
            TransactionAudit audit = create(id, id, REFERENCE, TransactionType.DEPOSIT,
                    AuditOutcome.SUCCESS, null, TIME);
            assertEquals(id, audit.auditId().orElseThrow());
            assertEquals(id, audit.customerId());
        }
    }

    @Test
    void rejectsNonpositivePersistedAuditIds() {
        for (long id : new long[]{0, -1, Long.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> create(id, 1, REFERENCE,
                    TransactionType.DEPOSIT, AuditOutcome.SUCCESS, null, TIME));
        }
    }

    @Test
    void rejectsNonpositiveCustomerIds() {
        for (long id : new long[]{0, -1, Long.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> create(null, id, REFERENCE,
                    TransactionType.DEPOSIT, AuditOutcome.SUCCESS, null, TIME));
        }
    }

    @Test
    void rejectsMissingEventType() {
        assertThrows(IllegalArgumentException.class, () -> create(null, 1, REFERENCE,
                null, AuditOutcome.SUCCESS, null, TIME));
    }

    @Test
    void rejectsMissingOutcomeRatherThanDefaultingToSuccess() {
        assertThrows(IllegalArgumentException.class, () -> create(null, 1, REFERENCE,
                TransactionType.DEPOSIT, null, null, TIME));
    }

    @Test
    void rejectsMissingTimestampRatherThanReadingTheClock() {
        assertThrows(IllegalArgumentException.class, () -> create(null, 1, REFERENCE,
                TransactionType.DEPOSIT, AuditOutcome.SUCCESS, null, null));
    }

    @Test
    void requiresReferenceForSuccess() {
        assertThrows(IllegalArgumentException.class, () -> attempt(AuditOutcome.SUCCESS, null, null));
    }

    @Test
    void permitsMissingOrPresentReferenceForEveryNonSuccessOutcome() {
        for (AuditOutcome outcome : nonSuccessOutcomes()) {
            assertTrue(attempt(outcome, null, null).referenceNumber().isEmpty());
            assertEquals(REFERENCE, attempt(outcome, REFERENCE, null).referenceNumber().orElseThrow());
        }
    }

    @Test
    void acceptsEveryFinancialEventAndOutcomeWithoutConflatingLedgerStatus() {
        for (TransactionType event : TransactionType.values()) {
            for (AuditOutcome outcome : AuditOutcome.values()) {
                TransactionAudit audit = create(null, 1, REFERENCE, event, outcome, null, TIME);
                assertSame(event, audit.eventType());
                assertSame(outcome, audit.outcome());
            }
        }
    }

    @Test
    void prohibitsAnyFailureCodeOnSuccess() {
        for (String code : new String[]{"INSUFFICIENT_FUNDS", "", " "}) {
            assertThrows(IllegalArgumentException.class,
                    () -> attempt(AuditOutcome.SUCCESS, REFERENCE, code));
        }
    }

    @Test
    void allowsAbsentFailureCodesForEveryNonSuccessOutcome() {
        for (AuditOutcome outcome : nonSuccessOutcomes()) {
            assertTrue(attempt(outcome, null, null).failureCode().isEmpty());
        }
    }

    @Test
    void acceptsExactCodeSyntaxAndLengthBoundariesWithoutNormalization() {
        for (AuditOutcome outcome : nonSuccessOutcomes()) {
            for (String code : new String[]{"A", "NOT_OWNER", "INSUFFICIENT_FUNDS",
                    "ERROR_123", "A".repeat(40)}) {
                assertEquals(code, attempt(outcome, null, code).failureCode().orElseThrow());
            }
        }
    }

    @Test
    void rejectsBlankOverlongNonAsciiOrUnstructuredFailureCodes() {
        for (AuditOutcome outcome : nonSuccessOutcomes()) {
            for (String code : new String[]{"", " ", "A".repeat(41), "lowercase", "Mixed",
                    "_CODE", "1CODE", " CODE", "CODE ", "CODE\n", "CODE\r\n", "CO\tDE",
                    "A-B", "A.B", "A:B", "A/B", "A\u0000B", "ÉRROR", "ＥRROR",
                    "ERR\u041ER", "SQL error: password=secret", "SELECT * FROM customers"}) {
                assertThrows(IllegalArgumentException.class, () -> attempt(outcome, null, code));
            }
        }
    }

    @Test
    void validationErrorsDoNotEchoUntrustedInputOrAttachACause() {
        String secret = "password=TOP_SECRET\nSELECT * FROM customers";
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> attempt(AuditOutcome.FAILED, null, secret));
        assertEquals("Failure code must be 1-40 uppercase ASCII letters, digits, or underscores, starting with a letter.",
                exception.getMessage());
        assertNull(exception.getCause());
        assertEquals(0, exception.getSuppressed().length);
        assertFalse(exception.toString().contains("TOP_SECRET"));
        assertFalse(exception.toString().contains("SELECT"));
    }

    @Test
    void rendersOnlyClosedEventAndOutcomeNames() {
        // Even a syntactically valid code may contain sensitive text; do not render it.
        TransactionAudit audit = create(987654L, 123456L, REFERENCE, TransactionType.TRANSFER,
                AuditOutcome.FAILED, "PRIVATE_CUSTOMER_VALUE", TIME);
        assertEquals("TransactionAudit[eventType=TRANSFER, outcome=FAILED]", audit.toString());
        assertFalse(audit.toString().contains("123456"));
        assertFalse(audit.toString().contains("987654"));
        assertFalse(audit.toString().contains(REFERENCE.value()));
        assertFalse(audit.toString().contains("PRIVATE_CUSTOMER_VALUE"));
        assertFalse(audit.toString().contains(TIME.toString()));
    }

    @Test
    void classAndStateAreFinalPrivateAndExposeNoMutationApi() {
        assertTrue(Modifier.isFinal(TransactionAudit.class.getModifiers()));
        Arrays.stream(TransactionAudit.class.getDeclaredFields()).forEach(field -> {
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
        });
        Set<String> publicMethods = Arrays.stream(TransactionAudit.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .peek(method -> assertEquals(0, method.getParameterCount()))
                .map(method -> method.getName()).collect(Collectors.toSet());
        assertEquals(Set.of("auditId", "customerId", "referenceNumber", "eventType", "outcome",
                "failureCode", "createdAt", "toString"), publicMethods);
    }

    @Test
    void accessorUseCannotAlterTheOriginalRecordOrItsTimestamp() {
        TransactionAudit audit = attempt(AuditOutcome.REJECTED, REFERENCE, "NOT_OWNER");
        assertEquals(TIME.plusSeconds(60), audit.createdAt().plusSeconds(60));
        audit.referenceNumber().map(reference -> new TransactionReference(reference.value()));
        audit.failureCode().map(code -> code + "_OTHER");
        assertEquals(TIME, audit.createdAt());
        assertEquals(REFERENCE, audit.referenceNumber().orElseThrow());
        assertEquals("NOT_OWNER", audit.failureCode().orElseThrow());
        assertTrue(audit.auditId().isEmpty());
        // Future persistence constructs another record rather than assigning an ID in place.
        TransactionAudit persisted = create(1L, audit.customerId(), REFERENCE, audit.eventType(),
                audit.outcome(), audit.failureCode().orElseThrow(), audit.createdAt());
        assertEquals(1, persisted.auditId().orElseThrow());
        assertTrue(audit.auditId().isEmpty());
    }

    @Test
    void fieldsMatchReferenceSchemaNullabilityAndTextWidths() throws IOException {
        String sql = Files.readString(Path.of("sql", "mysql", "schema.sql"))
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)--[^\\r\\n]*", "");
        Matcher table = Pattern.compile(
                        "(?is)CREATE\\s+TABLE\\s+transaction_audit\\s*\\((.*?)\\)\\s*ENGINE\\s*=")
                .matcher(sql);
        assertTrue(table.find());
        String definition = table.group(1);
        assertColumn(definition, "audit_id\\s+BIGINT\\s+PRIMARY\\s+KEY\\s+AUTO_INCREMENT");
        assertColumn(definition, "customer_id\\s+BIGINT\\s+NOT\\s+NULL");
        assertColumn(definition, "reference_number\\s+VARCHAR\\(40\\)\\s+NULL");
        assertColumn(definition, "event_type\\s+VARCHAR\\(30\\)\\s+NOT\\s+NULL");
        assertColumn(definition, "outcome\\s+VARCHAR\\(20\\)\\s+NOT\\s+NULL");
        assertColumn(definition, "failure_code\\s+VARCHAR\\(40\\)\\s+NULL");
        assertColumn(definition, "created_at\\s+TIMESTAMP\\s+NOT\\s+NULL");
        for (TransactionType event : TransactionType.values()) {
            assertTrue(event.name().length() <= 30);
        }
        for (AuditOutcome outcome : AuditOutcome.values()) {
            assertTrue(outcome.name().length() <= 20);
        }
        assertEquals(40, REFERENCE.value().length());
    }

    private static void assertColumn(String definition, String pattern) {
        assertTrue(Pattern.compile("(?i)\\b" + pattern).matcher(definition).find(), pattern);
    }

    private static AuditOutcome[] nonSuccessOutcomes() {
        return new AuditOutcome[]{AuditOutcome.REJECTED, AuditOutcome.FAILED, AuditOutcome.ROLLED_BACK};
    }

    private static TransactionAudit attempt(AuditOutcome outcome,
                                            TransactionReference reference, String code) {
        return create(null, 1, reference, TransactionType.TRANSFER, outcome, code, TIME);
    }

    private static TransactionAudit create(Long id, long customerId, TransactionReference reference,
                                           TransactionType event, AuditOutcome outcome,
                                           String code, Instant time) {
        return new TransactionAudit(id, customerId, reference, event, outcome, code, time);
    }
}
