package com.example.banking.unit;

import com.example.banking.exception.ValidationException;
import com.example.banking.model.Registration;
import com.example.banking.validation.CustomerValidator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumberValidationTest {
    private final CustomerValidator validator = new CustomerValidator(
            Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC));

    @ParameterizedTest
    @ValueSource(strings = {"2025550123", "0123456789", "0000000000", "9999999999"})
    void acceptsExactlyTenAsciiDigitsWithoutChangingLeadingZeroes(String phone) {
        Registration input = registration(phone);
        assertDoesNotThrow(() -> CustomerValidator.validatePhone(phone));
        assertDoesNotThrow(() -> validator.validate(input, "Valid-Password-123!".toCharArray()));
        assertEquals(phone, input.phone());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "123456789", "12345678901", "+12025550123",
            "202-555-0123", "(202)5550123", "202 555 0123", "202555012A",
            "2025550123 ", " 2025550123", "2025550123\n", "٢٠٢٥٥٥٠١٢٣",
            "２０２５５５０１２３"})
    void rejectsMissingNonTenDigitOrFormattedNumbersAtBothBoundaries(String phone) {
        assertThrows(ValidationException.class, () -> CustomerValidator.validatePhone(phone));
        assertThrows(ValidationException.class,
                () -> validator.validate(registration(phone), "Valid-Password-123!".toCharArray()));
    }

    private Registration registration(String phone) {
        return new Registration("Test", null, "Person", null, null,
                "test@example.invalid", phone, "tester");
    }
}
