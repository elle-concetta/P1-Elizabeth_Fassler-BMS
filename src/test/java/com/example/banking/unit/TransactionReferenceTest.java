package com.example.banking.unit;

import com.example.banking.model.TransactionReference;
import com.example.banking.util.ReferenceGenerator;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TransactionReferenceTest {

    private static final String VALID = "TX-20261110-0123456789ABCDEF0123456789AB";

    @Test
    void preservesExactValueAndRendersCorrelationReference() {
        TransactionReference reference = new TransactionReference(VALID);
        assertEquals(VALID, reference.value());
        assertEquals(VALID, reference.toString());
        assertEquals(40, TransactionReference.LENGTH);
        assertEquals(TransactionReference.LENGTH, reference.value().length());
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new TransactionReference(null));
    }

    @Test
    void rejectsEmptyBlankAndSurroundingWhitespaceWithoutTrimming() {
        reject("", " ", "\t\n", " " + VALID, VALID + " ", VALID + "\n", "\t" + VALID);
    }

    @Test
    void rejectsWrongPrefixesSeparatorsAndCaseWithoutNormalization() {
        reject(VALID.replace("TX-", "TR-"), VALID.replace("TX-", "tx-"),
                VALID.replace('-', '_'), VALID.toLowerCase(),
                VALID.substring(0, 11) + ":" + VALID.substring(12));
    }

    @Test
    void rejectsShortAndLongValuesAtSqlLengthBoundary() {
        reject(VALID.substring(0, 39), VALID + "0",
                "TX-20261110-" + "A".repeat(27), "TX-20261110-" + "A".repeat(29),
                VALID + "A".repeat(100));
    }

    @Test
    void rejectsNonHexAndNonAsciiCharacters() {
        reject("TX-20261110-" + "G".repeat(28), "TX-20261110-" + "a".repeat(28),
                "TX-20261110-" + "Ａ".repeat(28), "TX-２０２６１１１０-" + "0".repeat(28),
                "TX-20261110-" + "0".repeat(27) + "\u0000",
                "TX-20261110-" + "0".repeat(27) + " ");
    }

    @Test
    void rejectsImpossibleOrMalformedCalendarDates() {
        for (String date : new String[]{"20260229", "19000229", "20260431",
                "20261301", "20260001", "20260100", "20260132", "2026AB10"}) {
            reject("TX-" + date + "-" + "0".repeat(28));
        }
    }

    @Test
    void acceptsLeapDaysAndFullFourDigitYearRange() {
        // ISO proleptic year zero is supported by the existing generator.
        for (String date : new String[]{"00000101", "00000229", "20000229",
                "20240229", "99991231"}) {
            String value = "TX-" + date + "-" + "0".repeat(28);
            assertEquals(value, new TransactionReference(value).value());
        }
    }

    @Test
    void acceptsBothHexBoundariesWithoutClaimingEntropyOrUniqueness() {
        for (String hex : new String[]{"0".repeat(28), "F".repeat(28)}) {
            String value = "TX-20261110-" + hex;
            assertEquals(value, new TransactionReference(value).value());
        }
    }

    @Test
    void providesValueEqualityAndHashing() {
        TransactionReference first = new TransactionReference(VALID);
        TransactionReference second = new TransactionReference(VALID);
        TransactionReference third = new TransactionReference(VALID);
        assertEquals(first, first);
        assertEquals(first, second);
        assertEquals(second, first);
        assertEquals(second, third);
        assertEquals(first, third);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(1, new HashSet<>(Arrays.asList(first, second, third)).size());
        assertNotEquals(first, new TransactionReference("TX-20261111-" + VALID.substring(12)));
        assertNotEquals(first, new TransactionReference(VALID.substring(0, 39) + "E"));
        assertNotEquals(first, null);
        assertNotEquals(first, VALID);
        assertFalse(first.equals(new Object()));
    }

    @Test
    void acceptsDeterministicGeneratorOutputUsingUtcRatherThanClockZone() {
        SecureRandom random = mock(SecureRandom.class);
        doAnswer(invocation -> {
            byte[] bytes = invocation.getArgument(0);
            assertEquals(14, bytes.length);
            Arrays.fill(bytes, (byte) 0xAF);
            return null;
        }).when(random).nextBytes(any(byte[].class));
        Clock clock = Clock.fixed(Instant.parse("2026-11-10T00:30:00Z"), ZoneOffset.ofHours(-5));
        ReferenceGenerator generator = new ReferenceGenerator(random, clock);
        TransactionReference reference = new TransactionReference(generator.generateTransactionReference());
        assertEquals("TX-20261110-" + "AF".repeat(14), reference.value());
        verify(random).nextBytes(any(byte[].class));
    }

    @Test
    void acceptsGeneratorYearBoundariesAndRejectsOutOfRangeYears() {
        for (String instant : Set.of("0000-01-01T00:00:00Z", "9999-12-31T23:59:59Z")) {
            ReferenceGenerator generator = generatorAt(instant);
            String value = generator.generateTransactionReference();
            assertEquals(value, new TransactionReference(value).value());
            assertEquals(40, value.length());
        }
        for (String instant : Set.of("-0001-12-31T23:59:59Z", "+10000-01-01T00:00:00Z")) {
            assertThrows(IllegalStateException.class, generatorAt(instant)::generateTransactionReference);
        }
    }

    private static ReferenceGenerator generatorAt(String instant) {
        return new ReferenceGenerator(mock(SecureRandom.class),
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private static void reject(String... values) {
        for (String value : values) {
            assertThrows(IllegalArgumentException.class, () -> new TransactionReference(value));
        }
    }
}
