package com.example.banking.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Immutable decimal value compatible with DECIMAL(19,2).
 * Account and transaction rules separately validate balances
 * and operation amounts.
 */
public final class Money implements Comparable<Money> {

    private static final BigDecimal MAX_STORAGE_VALUE =
            new BigDecimal("99999999999999999.99");

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private final BigDecimal amount;

    public Money(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException(
                    "Money amount must not be null."
            );
        }

        if (amount.scale() > 2) {
            throw new IllegalArgumentException(
                    "Money amount must have no more than two decimal places."
            );
        }

        if (amount.abs().compareTo(MAX_STORAGE_VALUE) > 0) {
            throw new IllegalArgumentException(
                    "Money amount exceeds the DECIMAL(19,2) storage range."
            );
        }

        this.amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static Money parse(String input) {
        if (input == null
                || !input.trim().matches("[+-]?[0-9]+(?:\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException(
                    "Enter a decimal amount with no more than two decimal places."
            );
        }

        return new Money(new BigDecimal(input.trim()));
    }

    public BigDecimal toBigDecimal() {
        return amount;
    }

    public Money add(Money other) {
        requireOther(other);
        return new Money(amount.add(other.amount));
    }

    public Money subtract(Money other) {
        requireOther(other);
        return new Money(amount.subtract(other.amount));
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    @Override
    public int compareTo(Money other) {
        requireOther(other);
        return amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof Money money)) {
            return false;
        }

        return amount.equals(money.amount);
    }

    @Override
    public int hashCode() {
        return amount.hashCode();
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }

    private static void requireOther(Money other) {
        if (other == null) {
            throw new IllegalArgumentException(
                    "Other money value must not be null."
            );
        }
    }
}