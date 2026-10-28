package com.example.banking.security;

/** Hashing contract. Callers must clear raw password buffers after use. */
public interface PasswordHasher {
    String hash(char[] raw);
    boolean verify(char[] raw, String encoded);
}
