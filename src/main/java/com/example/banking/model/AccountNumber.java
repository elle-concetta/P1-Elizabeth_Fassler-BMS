package com.example.banking.model;

/**
 * Immutable synthetic account number: 23 ASCII digits plus a Luhn check digit.
 * This is not an IBAN, routing number, or proof that an account exists.
 */
public final class AccountNumber {

    public static final int LENGTH = 24;
    private final String value;

    public AccountNumber(String value) {
        if (value == null || !value.matches("[1-9][0-9]{23}")) {
            throw new IllegalArgumentException(
                    "Account number must contain 24 ASCII digits with no leading zero.");
        }
        if (luhnSum(value) % 10 != 0) {
            throw new IllegalArgumentException("Account number checksum is invalid.");
        }
        this.value = value;
    }

    /**
     * Adds the check digit to an already generated 23-digit payload.
     * Validation and generation share the same checksum rule.
     */
    public static AccountNumber fromPayload(String payload) {
        if (payload == null || !payload.matches("[1-9][0-9]{22}")) {
            throw new IllegalArgumentException(
                    "Account number payload must contain 23 ASCII digits with no leading zero.");
        }
        int checkDigit = (10 - luhnSum(payload + "0") % 10) % 10;
        return new AccountNumber(payload + checkDigit);
    }

    /** Exact database/input value; explicitly mask this value before logging. */
    public String value() {
        return value;
    }

    /** Customer-facing representation; never use value() to render an account number. */
    public String masked() {
        return "****" + value.substring(LENGTH - 4);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof AccountNumber number && value.equals(number.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /** Avoid leaking the full number through automatic object rendering. */
    @Override
    public String toString() {
        return "AccountNumber[" + masked() + "]";
    }

    private static int luhnSum(String digits) {
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum;
    }
}
