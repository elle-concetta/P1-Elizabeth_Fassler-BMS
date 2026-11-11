package com.example.banking.util;

import org.slf4j.Logger;

/** Shared logger access and account-number masking. */
public final class LoggerFactory {

    private LoggerFactory() {
    }

    public static Logger getLogger(Class<?> source) {
        if (source == null) {
            throw new IllegalArgumentException(
                    "Logger source class must not be null."
            );
        }

        return org.slf4j.LoggerFactory.getLogger(source);
    }

    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null
                || accountNumber.length() <= 4
                || !accountNumber.matches("[A-Za-z0-9]+")) {
            return "****";
        }

        return "****"
                + accountNumber.substring(accountNumber.length() - 4);
    }
}

