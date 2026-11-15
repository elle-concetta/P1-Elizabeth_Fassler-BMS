package com.example.banking.unit;

import com.example.banking.model.PasswordHash;
import com.example.banking.model.policy.PasswordPolicy;
import com.example.banking.security.BcryptPasswordHasher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationSecurityTest {
    @Test
    void bcryptUsesFreshSaltsAndVerifiesOnlyTheCorrectPassword() {
        var hasher = new BcryptPasswordHasher(4);
        char[] raw = "Synthetic-Password-1!".toCharArray();
        String first = hasher.hash(raw);
        String second = hasher.hash(raw);
        assertNotEquals(first, second);
        assertTrue(hasher.verify(raw, first));
        assertFalse(hasher.verify("Wrong-Password-2!".toCharArray(), first));
        assertFalse(first.contains("Synthetic"));
        assertEquals("PasswordHash[REDACTED]", new PasswordHash(first).toString());
    }

    @Test
    void rejectsOversizedUnicodeAndMalformedHashesWithoutTruncating() {
        var hasher = new BcryptPasswordHasher(4);
        char[] oversized = "é".repeat(37).toCharArray();
        assertFalse(PasswordPolicy.hasSupportedLength(oversized));
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(oversized));
        assertFalse(hasher.verify("Valid-Password-1!".toCharArray(), "not-a-hash"));
        assertThrows(IllegalArgumentException.class, () -> new BcryptPasswordHasher(17));
    }
}
