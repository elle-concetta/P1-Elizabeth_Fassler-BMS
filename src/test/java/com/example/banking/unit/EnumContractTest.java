package com.example.banking.unit;

import com.example.banking.model.AccountStatus;
import com.example.banking.model.AccountType;
import com.example.banking.model.CustomerStatus;
import com.example.banking.model.TransactionStatus;
import com.example.banking.model.TransactionType;
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

/** Locks the persisted names to the project guidelines and checked-in MySQL DDL. */
class EnumContractTest {

    @Test
    void accountTypesKeepAllFourPersistedNames() {
        assertEquals(Set.of("SAVINGS", "CURRENT", "DEPOSIT", "LOAN"), names(AccountType.class));
    }

    @Test
    void accountStatusesIncludeMatured() {
        assertEquals(Set.of("ACTIVE", "FROZEN", "MATURED", "CLOSED"), names(AccountStatus.class));
    }

    @Test
    void customerStatusesIncludeLocked() {
        assertEquals(Set.of("ACTIVE", "LOCKED", "CLOSED"), names(CustomerStatus.class));
    }

    @Test
    void transactionTypesKeepLoanRepaymentAndNoMaturityType() {
        assertEquals(Set.of("DEPOSIT", "WITHDRAWAL", "TRANSFER", "LOAN_REPAYMENT"),
                names(TransactionType.class));
    }

    @Test
    void transactionStatusesRetainEveryReferenceDdlState() {
        assertEquals(Set.of("PENDING", "SUCCESS", "FAILED", "REVERSED"),
                names(TransactionStatus.class));
    }

    @Test
    void createdIsNotAPersistedAccountStatus() {
        assertFalse(names(AccountStatus.class).contains("CREATED"));
        assertThrows(IllegalArgumentException.class, () -> AccountStatus.valueOf("CREATED"));
    }

    @Test
    void allPersistedNamesRoundTripWithoutOrdinalsOrDisplayLabels() {
        assertNameRoundTrips(AccountType.class);
        assertNameRoundTrips(AccountStatus.class);
        assertNameRoundTrips(CustomerStatus.class);
        assertNameRoundTrips(TransactionType.class);
        assertNameRoundTrips(TransactionStatus.class);
    }

    @Test
    void enumLookupDoesNotSilentlyNormalizeUnknownStoredNames() {
        assertInvalidNames(AccountType.class, "savings", " SAVINGS", "SAVINGS ", "UNKNOWN");
        assertInvalidNames(AccountStatus.class, "active", " ACTIVE", "ACTIVE ", "CREATED");
        assertInvalidNames(CustomerStatus.class, "active", " ACTIVE", "ACTIVE ", "FROZEN");
        assertInvalidNames(TransactionType.class, "transfer", " TRANSFER", "TRANSFER ", "MATURITY");
        assertInvalidNames(TransactionStatus.class, "success", " SUCCESS", "SUCCESS ", "COMPLETED");
    }

    @Test
    void mysqlCheckConstraintsMatchEveryJavaEnum() throws IOException {
        String sql = schemaWithoutComments();
        assertAll(
                () -> assertEquals(names(AccountType.class),
                        checkValues(sql, "chk_account_type", "account_type")),
                () -> assertEquals(names(AccountStatus.class),
                        checkValues(sql, "chk_account_status", "status")),
                () -> assertEquals(names(CustomerStatus.class),
                        checkValues(sql, "chk_customer_status", "status")),
                () -> assertEquals(names(TransactionType.class),
                        checkValues(sql, "chk_txn_type", "transaction_type")),
                () -> assertEquals(names(TransactionStatus.class),
                        checkValues(sql, "chk_txn_status", "status"))
        );
    }

    @Test
    void mysqlCustomerEnumAlsoMatchesJavaAndItsCheckConstraint() throws IOException {
        String sql = schemaWithoutComments();
        Matcher table = Pattern.compile(
                "(?is)CREATE\\s+TABLE\\s+customers\\s*\\((.*?)\\)\\s*ENGINE\\s*=").matcher(sql);
        assertTrue(table.find(), "Missing customers table in reference schema");
        Matcher status = Pattern.compile(
                "(?i)\\bstatus\\s+ENUM\\s*\\(([^)]*)\\)").matcher(table.group(1));
        assertTrue(status.find(), "Missing customers.status ENUM in reference schema");
        Set<String> values = quotedValues(status.group(1));
        assertEquals(names(CustomerStatus.class), values);
        assertEquals(checkValues(sql, "chk_customer_status", "status"), values);
    }

    private static <E extends Enum<E>> Set<String> names(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
    }

    private static <E extends Enum<E>> void assertNameRoundTrips(Class<E> type) {
        for (E value : type.getEnumConstants()) {
            assertSame(value, Enum.valueOf(type, value.name()));
        }
    }

    private static <E extends Enum<E>> void assertInvalidNames(Class<E> type, String... invalid) {
        for (String value : invalid) {
            assertThrows(IllegalArgumentException.class, () -> Enum.valueOf(type, value),
                    type.getSimpleName() + " must reject " + value);
        }
    }

    private static String schemaWithoutComments() throws IOException {
        // Read only: this test never opens a database connection or executes SQL.
        // The working directory must be the Maven project root.
        return Files.readString(Path.of("sql", "mysql", "schema.sql"))
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)--[^\\r\\n]*", "");
    }

    private static Set<String> checkValues(String sql, String constraint, String column) {
        Matcher matcher = Pattern.compile("(?i)\\bCONSTRAINT\\s+" + Pattern.quote(constraint)
                + "\\s+CHECK\\s*\\(\\s*" + Pattern.quote(column)
                + "\\s+IN\\s*\\(([^)]*)\\)\\s*\\)").matcher(sql);
        assertTrue(matcher.find(), "Missing or changed CHECK " + constraint);
        return quotedValues(matcher.group(1));
    }

    private static Set<String> quotedValues(String list) {
        return Pattern.compile("'([^']*)'").matcher(list).results()
                .map(match -> match.group(1)).collect(Collectors.toSet());
    }
}

