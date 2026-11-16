package com.example.banking.model;

import com.example.banking.exception.ValidationException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** UTC calendar-day filters; start inclusive, end exclusive; zero-based bounded pages. */
public record HistoryQuery(int page, int pageSize, LocalDate start, LocalDate end, TransactionType type) {
    public HistoryQuery {
        if (page < 0 || pageSize < 1 || pageSize > 50)
            throw new ValidationException("Page must be nonnegative and page size must be 1 to 50.");
        try { Math.multiplyExact(page, pageSize); }
        catch (ArithmeticException overflow) { throw new ValidationException("History page offset is too large."); }
        if ((start != null && (start.getYear() < 1000 || start.getYear() > 9999))
                || (end != null && (end.getYear() < 1000 || end.getYear() > 9999)))
            throw new ValidationException("History filter dates must use years 1000 to 9999.");
        if (start != null && end != null && !end.isAfter(start))
            throw new ValidationException("End date must be after start date; the end date is exclusive.");
    }

    public int offset() { return Math.multiplyExact(page, pageSize); }
    public int fetchLimit() { return pageSize + 1; }
    public HistoryQuery atPage(int number) { return new HistoryQuery(number, pageSize, start, end, type); }
    public HistoryQuery next() {
        if (page == Integer.MAX_VALUE) throw new ValidationException("History page offset is too large.");
        return atPage(page + 1);
    }

    public static LocalDate date(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            if (value.length() != 10 || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
                throw new DateTimeParseException("format", "", 0);
            return LocalDate.parse(value);
        } catch (DateTimeParseException invalid) {
            throw new ValidationException("Use a valid date in YYYY-MM-DD format, or leave it blank.");
        }
    }

    public static TransactionType transactionType(String value) {
        if (value == null || value.isEmpty()) return null;
        if (value.length() > 14) throw new ValidationException("History transaction type is invalid.");
        try { return TransactionType.valueOf(value); }
        catch (IllegalArgumentException invalid) {
            throw new ValidationException("Type must be DEPOSIT, WITHDRAWAL, TRANSFER or LOAN_REPAYMENT, or blank.");
        }
    }
}

