package com.example.banking.validation;

import com.example.banking.exception.ValidationException;
import com.example.banking.model.Money;
import java.time.Instant;

/** Validate before money SQL; never use floating point, rounding or driver coercion. */
public final class TransactionValidator {
    private static final Instant MIN = Instant.parse("1970-01-01T00:00:01Z");
    private static final Instant MAX = Instant.parse("2038-01-19T03:14:07Z");
    private TransactionValidator() {}

    public static Money amount(String supplied, Money maximum) {
        if (maximum == null || !maximum.isPositive())
            throw new IllegalArgumentException("A positive configured maximum is required.");
        if (supplied == null || supplied.length() > 40
                || !supplied.matches("[0-9]+(?:\\.[0-9]{1,2})?"))
            throw new ValidationException("Enter a positive plain-decimal amount with at most two decimal places.");
        Money parsed;
        try {
            parsed = Money.parse(supplied);
        } catch (IllegalArgumentException invalid) {
            throw new ValidationException("Amount exceeds supported monetary storage.");
        }
        if (!parsed.isPositive() || parsed.compareTo(maximum) > 0)
            throw new ValidationException("Amount must be positive and no greater than the configured transaction limit.");
        return parsed;
    }

    public static boolean supportedTime(Instant time) {
        return time != null && time.getNano() == 0 && !time.isBefore(MIN) && !time.isAfter(MAX);
    }
}
