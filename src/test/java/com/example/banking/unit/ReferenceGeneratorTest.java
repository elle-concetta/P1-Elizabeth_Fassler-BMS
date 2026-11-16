package com.example.banking.unit;

import com.example.banking.model.AccountNumber;
import com.example.banking.util.ReferenceGenerator;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReferenceGeneratorTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-11-10T00:30:00Z"), ZoneOffset.ofHours(-5));

    @Test
    void generatesKnownAccountAtLowestRandomBounds() {
        SecureRandom random = mock(SecureRandom.class);
        AccountNumber number = new ReferenceGenerator(random, FIXED).generateAccountNumber();
        assertEquals("100000000000000000000008", number.value());
        assertEquals(number, new AccountNumber(number.value()));
        assertTrue(number.value().length() <= 34);
        verify(random).nextInt(9);
        verify(random, times(22)).nextInt(10);
    }

    @Test
    void generatesAccountAtHighestRandomBounds() {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(9)).thenReturn(8);
        when(random.nextInt(10)).thenReturn(9);
        String number = new ReferenceGenerator(random, FIXED).generateAccountNumber().value();
        assertTrue(number.startsWith("9".repeat(23)));
        assertEquals(number, new AccountNumber(number).value());
    }

    @Test
    void referenceUsesUtcDateAndAllFourteenRandomBytes() {
        SecureRandom random = mock(SecureRandom.class);
        doAnswer(invocation -> {
            byte[] bytes = invocation.getArgument(0);
            assertEquals(14, bytes.length);
            Arrays.fill(bytes, (byte) 0xAF);
            return null;
        }).when(random).nextBytes(any(byte[].class));

        String reference = new ReferenceGenerator(random, FIXED).generateTransactionReference();
        assertEquals("TX-20261110-" + "AF".repeat(14), reference);
        assertEquals(40, reference.length());
        verify(random).nextBytes(any(byte[].class));
    }

    @Test
    void preservesLeadingZeroesInRandomHex() {
        SecureRandom random = mock(SecureRandom.class);
        assertEquals("TX-20261110-" + "0".repeat(28),
                new ReferenceGenerator(random, FIXED).generateTransactionReference());
    }

    @Test
    void defaultGeneratorProducesValidSqlSizedCandidates() {
        ReferenceGenerator generator = new ReferenceGenerator();
        for (int i = 0; i < 100; i++) {
            String account = generator.generateAccountNumber().value();
            assertEquals(24, account.length());
            assertEquals(account, new AccountNumber(account).value());
            String reference = generator.generateTransactionReference();
            assertTrue(reference.matches("TX-[0-9]{8}-[0-9A-F]{28}"));
            assertEquals(40, reference.length());
        }
        // No probabilistic uniqueness assertion: only MySQL can guarantee it.
    }

    @Test
    void repeatedEntropyIsNotFalselyTreatedAsGuaranteedUnique() {
        ReferenceGenerator generator = new ReferenceGenerator(mock(SecureRandom.class), FIXED);
        assertEquals(generator.generateAccountNumber(), generator.generateAccountNumber());
        assertEquals(generator.generateTransactionReference(), generator.generateTransactionReference());
    }

    @Test
    void rejectsMissingDependencies() {
        assertThrows(IllegalArgumentException.class, () -> new ReferenceGenerator(null, FIXED));
        assertThrows(IllegalArgumentException.class,
                () -> new ReferenceGenerator(new SecureRandom(), null));
    }

    @Test
    void rejectsDatesThatWouldExceedTheSqlReferenceLength() {
        Clock future = Clock.fixed(Instant.parse("+10000-01-01T00:00:00Z"), ZoneOffset.UTC);
        ReferenceGenerator generator = new ReferenceGenerator(mock(SecureRandom.class), future);
        assertThrows(IllegalStateException.class, generator::generateTransactionReference);
    }
}
