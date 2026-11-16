package com.example.banking.unit;

import com.example.banking.exception.ValidationException;
import com.example.banking.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.time.*;
import java.util.*;
import static com.example.banking.unit.DepositFixtures.*;
import static com.example.banking.unit.HistoryFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class HistoryModelTest {
    @ParameterizedTest @CsvSource({"-1,20", "0,0", "0,-1", "0,51", "2147483647,50", "107374183,20"})
    void rejectsInvalidPagesSizesAndOffsetOverflow(int page, int size) {
        assertThrows(ValidationException.class, () -> query(page, size));
    }

    @Test void validPageBoundsAndNavigationRetainFilters() {
        var filter = new HistoryQuery(0, 50, LocalDate.of(2026, 11, 15),
                LocalDate.of(2026, 11, 16), TransactionType.TRANSFER);
        assertEquals(0, filter.offset());
        assertEquals(51, filter.fetchLimit());
        assertEquals(50, filter.next().offset());
        assertEquals(filter, filter.next().atPage(0));
        assertEquals(Integer.MAX_VALUE, query(Integer.MAX_VALUE, 1).offset());
        assertThrows(ValidationException.class, () -> query(Integer.MAX_VALUE, 1).next());
    }

    @ParameterizedTest @ValueSource(strings = {"2026-02-29", "2026-13-01", "2026-11-31", "2026-1-01", " 2026-11-15", "tomorrow"})
    void invalidDateTextIsRejected(String value) {
        assertThrows(ValidationException.class, () -> HistoryQuery.date(value));
    }

    @Test void optionalDatesAreStrictCalendarValuesNotFreshnessChecks() {
        assertNull(HistoryQuery.date(""));
        assertNull(HistoryQuery.date(null));
        assertEquals(LocalDate.of(2024, 2, 29), HistoryQuery.date("2024-02-29"));
        assertNotNull(new HistoryQuery(0, 20, HistoryQuery.date("1000-01-01"),
                HistoryQuery.date("9999-12-31"), null));
        assertThrows(ValidationException.class, () -> new HistoryQuery(0, 20,
                LocalDate.of(999, 1, 1), null, null));
    }

    @Test void equalOrReversedDateRangeCannotReachSql() {
        var start = LocalDate.of(2026, 11, 15);
        assertThrows(ValidationException.class, () -> new HistoryQuery(0, 20, start, start, null));
        assertThrows(ValidationException.class, () -> new HistoryQuery(0, 20, start, start.minusDays(1), null));
    }

    @ParameterizedTest @EnumSource(TransactionType.class)
    void typeFilterIsWhitelisted(TransactionType type) {
        assertEquals(type, HistoryQuery.transactionType(type.name()));
    }

    @ParameterizedTest @ValueSource(strings = {"transfer", "ALL", "' OR 1=1", "UNKNOWN"})
    void arbitraryTypeTextIsNotAccepted(String value) {
        assertThrows(ValidationException.class, () -> HistoryQuery.transactionType(value));
    }

    @Test void blankTypeMeansAllAndHugeTypeCannotBuildAnEchoedException() {
        assertNull(HistoryQuery.transactionType(""));
        var error = assertThrows(ValidationException.class, () -> HistoryQuery.transactionType("x".repeat(10000)));
        assertTrue(error.getMessage().length() < 100);
    }

    @Test void pageCopiesRowsAndNeverIncludesTheLookahead() {
        var rows = new ArrayList<>(List.of(row(2, TIME), row(1, TIME)));
        var page = new HistoryPage(7, NUMBER, query(0, 2), rows, true);
        rows.clear();
        assertEquals(2, page.entries().size());
        assertThrows(UnsupportedOperationException.class, () -> page.entries().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new HistoryPage(7, NUMBER, query(0, 1), page.entries(), false));
        assertThrows(IllegalArgumentException.class,
                () -> new HistoryPage(7, NUMBER, query(0, 3), page.entries(), true));
    }

    @Test void storedEntriesMustHaveValidShapeAndSupportedExactTime() {
        assertThrows(IllegalArgumentException.class, () -> new HistoryEntry(0, REF, TransactionType.TRANSFER,
                Money.parse("1"), HistoryEntry.Direction.CREDIT, TransactionStatus.SUCCESS, TIME));
        assertThrows(IllegalArgumentException.class, () -> new HistoryEntry(1, REF, TransactionType.DEPOSIT,
                Money.parse("1"), HistoryEntry.Direction.DEBIT, TransactionStatus.SUCCESS, TIME));
        assertThrows(IllegalArgumentException.class, () -> new HistoryEntry(1, REF, TransactionType.WITHDRAWAL,
                Money.parse("1"), HistoryEntry.Direction.CREDIT, TransactionStatus.SUCCESS, TIME));
        assertThrows(IllegalArgumentException.class, () -> new HistoryEntry(1, REF, TransactionType.TRANSFER,
                Money.ZERO, HistoryEntry.Direction.CREDIT, TransactionStatus.SUCCESS, TIME));
        assertThrows(IllegalArgumentException.class, () -> row(1, TIME.plusNanos(1)));
    }

    @Test void historyProjectionHasNoCounterpartyOrBalanceFields() {
        var names = Arrays.stream(HistoryEntry.class.getRecordComponents()).map(c -> c.getName()).toList();
        assertEquals(List.of("transactionId", "reference", "type", "amount", "direction", "status", "createdAt"), names);
        assertFalse(new HistoryPage(7, NUMBER, query(0, 20), List.of(row(1, TIME)), false)
                .toString().contains(NUMBER.value()));
    }
}

