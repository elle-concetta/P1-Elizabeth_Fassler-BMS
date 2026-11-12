package com.example.banking.validation;

import com.example.banking.exception.ValidationException;
import com.example.banking.model.Registration;
import com.example.banking.model.policy.PasswordPolicy;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/** Rejects invalid input before hashing or database work; does not normalize it. */
public final class CustomerValidator {
    private final Clock clock;
    private final PasswordPolicy passwords = new PasswordPolicy();

    public CustomerValidator(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public void validate(Registration input, char[] raw) {
        if (input == null) throw new ValidationException("Registration details are required.");
        name(input.firstName(), "First name");
        name(input.lastName(), "Last name");
        if (input.middleInitial() != null && !input.middleInitial().isEmpty()
                && !input.middleInitial().matches("\\p{L}")) {
            throw new ValidationException("Middle initial must be one letter.");
        }
        if (input.username() == null || !input.username().matches("[A-Za-z0-9._-]{3,30}")) {
            throw new ValidationException("Username must be 3–30 letters, digits, dots, underscores or hyphens.");
        }
        if (input.email() == null || input.email().length() > 254
                || !input.email().matches("[^\\s@\\p{Cntrl}]+@[^\\s@\\p{Cntrl}]+\\.[^\\s@\\p{Cntrl}]+")) {
            throw new ValidationException("Enter a valid email address of at most 254 characters.");
        }
        optionalText(input.address(), 255, "Address");
        if (input.phone() != null && !input.phone().isEmpty()
                && !input.phone().matches("[+0-9() .-]{7,30}")) {
            throw new ValidationException("Phone must contain 7–30 phone-number characters.");
        }
        if (input.dateOfBirth() != null && (input.dateOfBirth().getYear() < 1000
                || !input.dateOfBirth().isBefore(LocalDate.now(clock)))) {
            throw new ValidationException("Birth date must be a past date with a year of at least 1000.");
        }
        passwords.validate(input.username(), raw);
    }

    private static void name(String value, String label) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > 60
                || !value.equals(value.strip())
                || !value.matches("[\\p{L}\\p{M}][\\p{L}\\p{M} '\\-]*")) {
            throw new ValidationException(label + " must contain 1–60 name characters without surrounding spaces.");
        }
    }

    private static void optionalText(String value, int maximum, String label) {
        if (value != null && (value.codePointCount(0, value.length()) > maximum
                || value.codePoints().anyMatch(Character::isISOControl))) {
            throw new ValidationException(label + " is too long or contains control characters.");
        }
    }
}

