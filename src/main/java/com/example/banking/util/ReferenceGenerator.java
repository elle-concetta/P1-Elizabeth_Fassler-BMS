package com.example.banking.util;

import com.example.banking.model.AccountNumber;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * Generates candidate identifiers without accessing a database.
 * UNIQUE constraints remain the final collision guard.
 */
public final class ReferenceGenerator {

    private final SecureRandom random;
    private final Clock clock;

    public ReferenceGenerator() {
        this(new SecureRandom(), Clock.systemUTC());
    }

    /** Injectable secure randomness and time for deterministic unit tests. */
    public ReferenceGenerator(SecureRandom random, Clock clock) {
        if (random == null || clock == null) {
            throw new IllegalArgumentException("Random source and clock must not be null.");
        }
        this.random = random;
        this.clock = clock;
    }

    /** 24 characters, within accounts.account_number VARCHAR(34). */
    public AccountNumber generateAccountNumber() {
        StringBuilder payload = new StringBuilder(AccountNumber.LENGTH - 1);
        payload.append(random.nextInt(9) + 1);
        for (int i = 1; i < AccountNumber.LENGTH - 1; i++) {
            payload.append(random.nextInt(10));
        }
        return AccountNumber.fromPayload(payload.toString());
    }

    /**
     * 40 characters: TX-YYYYMMDD- followed by 112 random bits in uppercase hex.
     * Generate once per logical operation and retain across all retries and
     * audit attempts. This is a correlation identifier, not authorization.
     */
    public String generateTransactionReference() {
        LocalDate date = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (date.getYear() < 0 || date.getYear() > 9999) {
            throw new IllegalStateException("Reference date must have a four-digit year.");
        }
        byte[] entropy = new byte[14];
        random.nextBytes(entropy);
        return "TX-" + date.format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + HexFormat.of().withUpperCase().formatHex(entropy);
    }
}
