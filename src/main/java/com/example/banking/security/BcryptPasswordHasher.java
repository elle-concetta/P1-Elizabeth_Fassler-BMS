package com.example.banking.security;

/** Explicitly nonfunctional until a library-backed implementation is added. */
public final class BcryptPasswordHasher implements PasswordHasher {
    @Override
    public String hash(char[] raw) {
        throw new UnsupportedOperationException("TODO: Implement bcrypt hashing.");
    }

    @Override
    public boolean verify(char[] raw, String encoded) {
        throw new UnsupportedOperationException("TODO: Implement bcrypt verification.");
    }
}
