package com.example.banking.unit;

import com.example.banking.model.AccountNumber;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountNumberTest {

    private static final String VALID = "123456789012345678901234";

    @Test
    void acceptsKnownLuhnVectorAndPreservesExactValue() {
        assertEquals(VALID, new AccountNumber(VALID).value());
        assertEquals(24, AccountNumber.LENGTH);
    }

    @Test
    void appendsKnownCheckDigitsIncludingZeroAndNine() {
        assertEquals(VALID, AccountNumber.fromPayload("12345678901234567890123").value());
        assertEquals("100000000000000000000008",
                AccountNumber.fromPayload("10000000000000000000000").value());
        assertEquals("900000000000000000000001",
                AccountNumber.fromPayload("90000000000000000000000").value());
        assertEquals("100000000000000000000099",
                AccountNumber.fromPayload("10000000000000000000009").value());
        assertEquals("100000000000000000000180",
                AccountNumber.fromPayload("10000000000000000000018").value());
    }

    @Test
    void rejectsNullBlankAndWrongLengths() {
        for (String value : new String[]{null, "", " ", "1", VALID.substring(1), VALID + "0"}) {
            assertThrows(IllegalArgumentException.class, () -> new AccountNumber(value));
        }
    }

    @Test
    void rejectsNonCanonicalInputWithoutTrimming() {
        for (String value : new String[]{
                "0" + VALID.substring(1), " " + VALID, VALID + " ", VALID + "\n",
                "+" + VALID.substring(1), "A" + VALID.substring(1),
                "١" + VALID.substring(1), "１" + VALID.substring(1),
                VALID.substring(0, 10) + "-" + VALID.substring(11)
        }) {
            assertThrows(IllegalArgumentException.class, () -> new AccountNumber(value));
        }
    }

    @Test
    void rejectsEverySingleDigitMutationOfKnownValidNumber() {
        for (int position = 0; position < VALID.length(); position++) {
            for (char digit = '0'; digit <= '9'; digit++) {
                if (digit == VALID.charAt(position)) {
                    continue;
                }
                String changed = VALID.substring(0, position) + digit
                        + VALID.substring(position + 1);
                assertThrows(IllegalArgumentException.class, () -> new AccountNumber(changed));
            }
        }
    }

    @Test
    void rejectsInvalidPayloads() {
        for (String payload : new String[]{
                null, "", VALID, "123", "00000000000000000000000",
                "A2345678901234567890123", "1234567890123456789012 "
        }) {
            assertThrows(IllegalArgumentException.class, () -> AccountNumber.fromPayload(payload));
        }
    }

    @Test
    void comparesAndHashesByValue() {
        AccountNumber first = new AccountNumber(VALID);
        AccountNumber same = new AccountNumber(VALID);
        assertAll(
                () -> assertEquals(first, first),
                () -> assertEquals(first, same),
                () -> assertEquals(first.hashCode(), same.hashCode()),
                () -> assertNotEquals(first, AccountNumber.fromPayload("10000000000000000000000")),
                () -> assertNotEquals(first, null),
                () -> assertNotEquals(first, VALID)
        );
    }

    @Test
    void masksAutomaticRenderingWithoutChangingStoredValue() {
        AccountNumber number = new AccountNumber(VALID);
        assertEquals("AccountNumber[****1234]", number.toString());
        assertFalse(number.toString().contains(VALID));
        assertEquals(VALID, number.value());
    }
}
