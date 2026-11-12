package com.example.banking.security;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.example.banking.model.PasswordHash;
import com.example.banking.model.policy.PasswordPolicy;

/** Library-backed bcrypt; generation and verification are bounded to costs 4–16. */
public final class BcryptPasswordHasher implements PasswordHasher {
    private final int cost;

    public BcryptPasswordHasher() {
        this(12);
    }

    public BcryptPasswordHasher(int cost) {
        if (cost < 4 || cost > 16) {
            throw new IllegalArgumentException("Supported bcrypt runtime cost is 4–16.");
        }
        this.cost = cost;
    }

    @Override
    public String hash(char[] raw) {
        if (!PasswordPolicy.hasSupportedLength(raw)) {
            throw new IllegalArgumentException("Password must contain 1–72 UTF-8 bytes.");
        }
        return BCrypt.withDefaults().hashToString(cost, raw);
    }

    @Override
    public boolean verify(char[] raw, String encoded) {
        if (!PasswordPolicy.hasSupportedLength(raw)) return false;
        try {
            new PasswordHash(encoded);
            if (Integer.parseInt(encoded.substring(4, 6)) > 16) return false;
            return BCrypt.verifyer().verify(raw, encoded).verified;
        } catch (IllegalArgumentException invalidEncoding) {
            return false;
        }
    }
}

