package com.example.banking.unit;

import com.example.banking.exception.ValidationException;
import com.example.banking.model.Customer;
import com.example.banking.model.CustomerStatus;
import com.example.banking.model.Registration;
import com.example.banking.model.policy.PasswordPolicy;
import com.example.banking.security.BcryptPasswordHasher;
import com.example.banking.validation.CustomerValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationSecurityTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void realBcryptUsesSaltsAndVerifiesWithoutChangingCallerBuffer() {
        var hasher = new BcryptPasswordHasher(4);
        char[] raw = "Valid-Password-123!".toCharArray();
        String first = hasher.hash(raw);
        String second = hasher.hash(raw);
        assertNotEquals(first, second);
        assertTrue(first.startsWith("$2a$04$"));
        assertTrue(hasher.verify(raw, first));
        assertFalse(hasher.verify("Wrong-Password-456!".toCharArray(), first));
        assertArrayEquals("Valid-Password-123!".toCharArray(), raw);
        assertFalse(hasher.verify(raw, "not-a-hash"));
        assertFalse(hasher.verify(raw, "$2a$31$" + "A".repeat(53)));
    }

    @Test
    void bcryptDoesNotTruncateOrAcceptMalformedUnicodeOrUnboundedCosts() {
        var hasher = new BcryptPasswordHasher(4);
        assertThrows(IllegalArgumentException.class, () -> hasher.hash("é".repeat(37).toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(new char[]{'\uD800'}));
        assertThrows(IllegalArgumentException.class, () -> new BcryptPasswordHasher(17));
        assertThrows(IllegalArgumentException.class, () -> new BcryptPasswordHasher(3));
        assertFalse(hasher.verify(null, "bad"));
        assertFalse(hasher.verify(new char[73], "bad"));
        String hash = hasher.hash("é".repeat(36).toCharArray());
        assertTrue(hasher.verify("é".repeat(36).toCharArray(), hash));
    }

    @ParameterizedTest
    @ValueSource(strings = {"short1!Aa", "alllowercase123!", "ALLUPPERCASE123!",
            "NoDigitsHere!", "NoSymbolsHere123", "Contains space123!", "Has\nControl123!"})
    void weakPasswordsAreRejected(String raw) {
        assertThrows(ValidationException.class,
                () -> new PasswordPolicy().validate("tester", raw.toCharArray()));
    }

    @Test
    void validationAcceptsOptionalFieldsAndRejectsTodayFutureAndBadIdentities() {
        var validator = new CustomerValidator(clock);
        char[] raw = "Valid-Password-123!".toCharArray();
        assertDoesNotThrow(() -> validator.validate(registration("tester", "test@example.invalid",
                LocalDate.of(2000, 1, 1)), raw));
        for (String username : new String[]{"ab", "has space", "x".repeat(31), "wrong\nname"}) {
            assertThrows(ValidationException.class,
                    () -> validator.validate(registration(username, "test@example.invalid", null), raw));
        }
        for (String email : new String[]{"bad", "x@localhost", " a@example.invalid", "a@@b.invalid"}) {
            assertThrows(ValidationException.class,
                    () -> validator.validate(registration("tester", email, null), raw));
        }
        for (LocalDate day : new LocalDate[]{LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 12),
                LocalDate.of(999, 1, 1)}) {
            assertThrows(ValidationException.class,
                    () -> validator.validate(registration("tester", "test@example.invalid", day), raw));
        }
    }

    @Test
    void automaticProfileOutputContainsNeitherContactDetailsNorCredentials() {
        Customer customer = new Customer(1, "C-test", "Test", null, "Person", null,
                null, "private@example.invalid", null, "private-user", CustomerStatus.ACTIVE);
        assertEquals("Customer[id=1, status=ACTIVE]", customer.toString());
        assertEquals("Registration[REDACTED]", registration("tester", "private@example.invalid", null).toString());
    }

    private Registration registration(String username, String email, LocalDate birth) {
        return new Registration("Test", null, "Person", birth, null, email, "2025550123", username);
    }
}
