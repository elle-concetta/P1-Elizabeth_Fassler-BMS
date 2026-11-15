package com.example.banking.unit;

import com.example.banking.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyTest {

    @Test
    void normalizesAcceptedStringsToTwoDecimalPlaces() {
        assertAll(
                () -> assertEquals("10.00", Money.parse("10").toString()),
                () -> assertEquals("10.10", Money.parse("10.1").toString()),
                () -> assertEquals("10.12", Money.parse("10.12").toString()),
                () -> assertEquals("10.00", Money.parse(" +10 ").toString())
        );
    }

    @Test
    void normalizesBigDecimalWithoutRounding() {
        Money money = new Money(new BigDecimal("10.1"));

        assertEquals(new BigDecimal("10.10"), money.toBigDecimal());
        assertEquals(2, money.toBigDecimal().scale());
    }

    @Test
    void rejectsNullString() {
        assertThrows(IllegalArgumentException.class, () -> Money.parse(null));
    }

    @Test
    void rejectsNullBigDecimal() {
        assertThrows(IllegalArgumentException.class, () -> new Money(null));
    }

    @Test
    void rejectsEmptyAndBlankStrings() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> Money.parse("")),
                () -> assertThrows(IllegalArgumentException.class, () -> Money.parse(" "))
        );
    }

    @Test
    void rejectsMalformedAndNonPlainDecimalStrings() {
        for (String input : new String[]{
                "abc", "1,000.00", "$10.00", "1e2", "NaN",
                "Infinity", ".", ".50", "10.", "1 0", "--10"
        }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> Money.parse(input),
                    "Should reject a malformed decimal format."
            );
        }
    }

    @Test
    void rejectsExcessScaleEvenWhenExtraDigitsAreZero() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class, () -> Money.parse("10.123")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class, () -> Money.parse("10.120")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new Money(new BigDecimal("10.120"))
                )
        );
    }

    @Test
    void allowsZeroButDoesNotCallItPositive() {
        assertEquals(Money.ZERO, Money.parse("0"));
        assertEquals("0.00", Money.ZERO.toString());
        assertFalse(Money.ZERO.isPositive());
    }

    @Test
    void representsNegativeValuesWithoutCallingThemPositive() {
        Money money = Money.parse("-10.00");

        assertEquals("-10.00", money.toString());
        assertFalse(money.isPositive());
    }

    @Test
    void recognizesPositiveValues() {
        assertTrue(Money.parse("0.01").isPositive());
    }

    @Test
    void addsExactlyWithoutChangingEitherOperand() {
        Money first = Money.parse("0.10");
        Money second = Money.parse("0.20");

        Money result = first.add(second);

        assertEquals(Money.parse("0.30"), result);
        assertEquals(Money.parse("0.10"), first);
        assertEquals(Money.parse("0.20"), second);
        assertNotSame(first, result);
        assertNotSame(second, result);
    }

    @Test
    void subtractsWithoutChangingEitherOperand() {
        Money first = Money.parse("10.00");
        Money second = Money.parse("2.25");

        Money result = first.subtract(second);

        assertEquals(Money.parse("7.75"), result);
        assertEquals(Money.parse("10.00"), first);
        assertEquals(Money.parse("2.25"), second);
        assertNotSame(first, result);
    }

    @Test
    void subtractionCanRepresentANegativeDifference() {
        assertEquals(
                Money.parse("-1.00"), Money.parse("1.00").subtract(Money.parse("2.00"))
        );
    }

    @Test
    void comparesValuesNumerically() {
        assertAll(
                () -> assertTrue(Money.parse("2.00").compareTo(Money.parse("10.00")) < 0),
                () -> assertEquals(0, Money.parse("10").compareTo(Money.parse("10.00"))),
                () -> assertTrue(Money.parse("10.01").compareTo(Money.parse("10.00")) > 0)
        );
    }

    @Test
    void equalityAndHashCodeUseTheNormalizedValue() {
        Money first = Money.parse("10");
        Money second = Money.parse("10.0");
        Money third = Money.parse("10.00");

        assertAll(
                () -> assertEquals(first, first),
                () -> assertEquals(first, second),
                () -> assertEquals(second, first),
                () -> assertEquals(second, third),
                () -> assertEquals(first, third),
                () -> assertEquals(first.hashCode(), third.hashCode()),
                () -> assertNotEquals(first, Money.parse("10.01")),
                () -> assertNotEquals(first, null),
                () -> assertNotEquals(first, "10.00")
        );
    }

    @Test
    void formatsWithoutScientificNotation() {
        assertEquals("1000.00", new Money(new BigDecimal("1E+3")).toString());
    }

    @Test
    void acceptsTheStorageRangeBoundaries() {
        assertAll(
                () -> assertEquals(
                        "99999999999999999.99",
                        Money.parse("99999999999999999.99").toString()
                ),
                () -> assertEquals(
                        "-99999999999999999.99",
                        Money.parse("-99999999999999999.99").toString()
                )
        );
    }

    @Test
    void rejectsValuesAndArithmeticOutsideTheStorageRange() {
        Money maximum = Money.parse("99999999999999999.99");
        Money minimum = Money.parse("-99999999999999999.99");
        Money cent = Money.parse("0.01");

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> Money.parse("100000000000000000.00")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new Money(new BigDecimal("-100000000000000000"))
                ),
                () -> assertThrows(IllegalArgumentException.class, () -> maximum.add(cent)),
                () -> assertThrows(IllegalArgumentException.class, () -> minimum.subtract(cent))
        );
    }

    @Test
    void rejectsNullArithmeticAndComparisonOperands() {
        Money money = Money.parse("10");

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> money.add(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> money.subtract(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> money.compareTo(null))
        );
    }
}
