package com.example.banking.unit;

import com.example.banking.model.PasswordHash;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHashTest {

    // Fixed public fixture, never a real customer's hash. No random hashing in these tests.
    private static final String VALID =
            "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";
    private static final String MESSAGE = "Password hash must be a supported bcrypt encoding.";

    @Test
    void preservesExactEncoding() {
        assertEquals(60, VALID.length());
        assertEquals(VALID, new PasswordHash(VALID).value());
    }

    @Test
    void acceptsSupportedBcryptRevisions() {
        for (String revision : List.of("2a", "2b", "2y")) {
            String encoded = VALID.replace("2a", revision);
            assertEquals(encoded, new PasswordHash(encoded).value());
        }
    }

    @Test
    void acceptsEveryTwoDigitCostFromFourThroughThirtyOne() {
        for (int cost = 4; cost <= 31; cost++) {
            String digits = cost < 10 ? "0" + cost : Integer.toString(cost);
            String encoded = VALID.replace("$12$", "$" + digits + "$");
            assertEquals(encoded, new PasswordHash(encoded).value());
        }
    }

    @Test
    void rejectsNull() {
        reject((String) null);
    }

    @Test
    void rejectsEmptyBlankAndSurroundingWhitespaceWithoutTrimming() {
        reject("", " ", "\t\n", " " + VALID, VALID + " ", VALID + "\n", "\t" + VALID);
    }

    @Test
    void rejectsPlaintextAndOtherAlgorithmsOrRevisions() {
        reject("not-an-encoded-hash", "$argon2id$v=19$m=65536,t=3,p=1$salt$digest",
                VALID.replace("$2a$", "$2x$"), VALID.replace("$2a$", "$2$"),
                VALID.replace("$2a$", "$2A$"), VALID.replace("$2a$", "$3b$"));
    }

    @Test
    void rejectsOutOfRangeAndMalformedCosts() {
        for (String cost : List.of("00", "03", "32", "99", "4", "004", "-1", "aa", "１２")) {
            reject(VALID.replace("$12$", "$" + cost + "$"));
        }
    }

    @Test
    void rejectsWrongLengthsAndSeparators() {
        reject(VALID.substring(0, 59), VALID + "A", VALID + "A".repeat(100),
                VALID.substring(1), VALID.replace('$', ':'), VALID.replace("$12$", "$12:"));
    }

    @Test
    void rejectsCharactersOutsideBcryptAlphabet() {
        for (String invalid : List.of("+", "=", "_", "-", " ", "\n", "\u0000", "Ａ")) {
            reject(VALID.substring(0, 59) + invalid,
                    VALID.substring(0, 7) + invalid + VALID.substring(8));
        }
    }

    @Test
    void providesExactValueEqualityAndHashing() {
        PasswordHash first = new PasswordHash(VALID);
        PasswordHash second = new PasswordHash(VALID);
        PasswordHash third = new PasswordHash(VALID);
        assertEquals(first, first);
        assertEquals(first, second);
        assertEquals(second, first);
        assertEquals(second, third);
        assertEquals(first, third);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(1, new HashSet<>(List.of(first, second, third)).size());
        assertNotEquals(first, new PasswordHash(VALID.replace("$12$", "$13$")));
        assertNotEquals(first, new PasswordHash(VALID.replace("$2a$", "$2b$")));
        assertNotEquals(first, new PasswordHash(VALID.substring(0, 59) + "a"));
        assertNotEquals(first, null);
        assertNotEquals(first, VALID);
        assertFalse(first.equals(new Object()));
    }

    @Test
    void redactsDirectConcatenatedAndCollectionRendering() {
        for (String encoded : List.of(VALID, VALID.replace("$12$", "$04$"),
                VALID.replace("$2a$", "$2y$"))) {
            PasswordHash hash = new PasswordHash(encoded);
            assertEquals("PasswordHash[REDACTED]", hash.toString());
            assertEquals("hash=PasswordHash[REDACTED]", "hash=" + hash);
            assertEquals("PasswordHash[REDACTED]", String.valueOf(hash));
            assertEquals("[PasswordHash[REDACTED]]", List.of(hash).toString());
        }
    }

    @Test
    void validationDiagnosticsNeverEchoSuppliedMaterial() {
        for (String input : List.of("private-input-marker", VALID + "\nprivate-input-marker")) {
            IllegalArgumentException error =
                    assertThrows(IllegalArgumentException.class, () -> new PasswordHash(input));
            assertEquals(MESSAGE, error.getMessage());
            assertFalse(error.toString().contains("private-input-marker"));
            assertFalse(error.toString().contains(VALID));
            assertNull(error.getCause());
        }
    }

    private static void reject(String... values) {
        for (String value : values) {
            IllegalArgumentException error =
                    assertThrows(IllegalArgumentException.class, () -> new PasswordHash(value));
            assertEquals(MESSAGE, error.getMessage());
            assertNull(error.getCause());
        }
    }
}

