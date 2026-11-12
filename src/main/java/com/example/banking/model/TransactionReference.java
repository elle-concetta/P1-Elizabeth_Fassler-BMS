package com.example.banking.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Immutable correlation identifier: TX-YYYYMMDD- plus 28 uppercase hex digits.
 * Validates shape and calendar date, not existence, ownership, or uniqueness.
 */
public final class TransactionReference {

    public static final int LENGTH = 40;
    private static final Pattern FORMAT = Pattern.compile("TX-[0-9]{8}-[0-9A-F]{28}");
    private final String value;

    public TransactionReference(String value) {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Transaction reference must be TX-YYYYMMDD- followed by 28 uppercase hex digits.");
        }
        try {
            LocalDate.parse(value.substring(3, 11), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException exception) {
            // Do not attach the parser exception, which contains untrusted input.
            throw new IllegalArgumentException("Transaction reference must contain a valid calendar date.");
        }
        this.value = value;
    }

    /** Exact value for future JDBC binding; no trimming or case conversion. */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof TransactionReference reference && value.equals(reference.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /** References contain no account/customer details and may be used for correlation. */
    @Override
    public String toString() {
        return value;
    }
}

