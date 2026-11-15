package com.example.banking.model;

import java.util.regex.Pattern;

/**
 * Immutable structurally validated bcrypt encoding, not a raw password.
 * Does not verify a password, canonical base64 bits, or the work-factor policy.
 */
public final class PasswordHash {

    private static final Pattern FORMAT =
            Pattern.compile("\\$2[aby]\\$(0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}");
    private final String value;

    public PasswordHash(String value) {
        if (value == null || !FORMAT.matcher(value).matches()) {
            // Never include the supplied value or a parser cause in diagnostics.
            throw new IllegalArgumentException("Password hash must be a supported bcrypt encoding.");
        }
        this.value = value;
    }

    /** Sensitive encoding for explicit hashing/JDBC boundaries only; never log it. */
    public String value() {
        return value;
    }

    /** Exact encoded-value equality, not constant-time password verification. */
    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof PasswordHash hash && value.equals(hash.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /** No prefix, cost, salt, digest, or other value-dependent output. */
    @Override
    public String toString() {
        return "PasswordHash[REDACTED]";
    }
}
