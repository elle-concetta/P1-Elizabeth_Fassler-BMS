package com.example.banking.util;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Properties;

public final class AppConfig {

    private final Properties properties;

    private AppConfig(Properties properties) {
        this.properties = properties;
    }

    public static AppConfig load() {
        Properties properties = new Properties();

        try (InputStream input =
                     AppConfig.class.getResourceAsStream("/app.properties")) {

            if (input == null) {
                throw new IllegalStateException(
                        "Required configuration file app.properties was not found."
                );
            }

            properties.load(input);

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to load app.properties.",
                    exception
            );
        }

        return from(properties);
    }

    public static AppConfig from(Properties source) {
        if (source == null) {
            throw new IllegalArgumentException(
                    "Configuration properties must not be null."
            );
        }

        Properties copy = new Properties();

        for (String key : source.stringPropertyNames()) {
            copy.setProperty(key, source.getProperty(key));
        }

        return new AppConfig(copy);
    }

    public String getRequired(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "Configuration key must not be blank."
            );
        }

        String value = properties.getProperty(key);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required configuration setting: " + key
            );
        }

        return value.trim();
    }

    public int getPositiveInt(String key) {
        String value = getRequired(key);

        final int number;

        try {
            number = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "Configuration setting must be a whole number: " + key
            );
        }

        if (number <= 0) {
            throw new IllegalStateException(
                    "Configuration setting must be greater than zero: " + key
            );
        }

        return number;
    }
    /**
     * Reads a positive monetary setting without rounding.
     * Accepted values are returned with exactly two decimal places.
     */
    public BigDecimal getPositiveDecimal(String key) {
        String value = getRequired(key);

        if (!value.matches("[+-]?[0-9]+(?:\\.[0-9]{1,2})?")) {
            throw new IllegalStateException(
                    "Configuration setting must be a decimal number "
                            + "with at most two decimal places: " + key
            );
        }

        BigDecimal amount = new BigDecimal(value);

        if (amount.signum() <= 0) {
            throw new IllegalStateException(
                    "Configuration setting must be greater than zero: " + key
            );
        }

        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
    /**
     * Checks that the password-hashing settings are supported.
     * This method validates configuration only; it does not hash a password.
     */
    public void validatePasswordHashSettings() {
        String algorithm = getRequired("password.hash.algorithm");

        if (!"bcrypt".equals(algorithm)) {
            throw new IllegalStateException(
                    "Unsupported password.hash.algorithm. Use bcrypt."
            );
        }

        int cost = getPositiveInt("password.hash.cost");

        if (cost < 4 || cost > 31) {
            throw new IllegalStateException(
                    "password.hash.cost must be between 4 and 31."
            );
        }
    }
    /**
     * Reads a required environment variable from the running process.
     */
    public static String getRequiredEnvironment(String key) {
        return getRequiredEnvironment(key, System.getenv());
    }

    /**
     * Reads from a supplied environment map.
     * Unit tests use this overload without changing the real environment.
     */
    public static String getRequiredEnvironment(
            String key,
            Map<String, String> environment
    ) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "Environment variable name must not be blank."
            );
        }

        if (environment == null) {
            throw new IllegalArgumentException(
                    "Environment map must not be null."
            );
        }

        String value = environment.get(key);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required environment variable: " + key
            );
        }

        return value;
    }

}