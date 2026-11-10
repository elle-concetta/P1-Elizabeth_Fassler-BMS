package com.example.banking.unit;

import com.example.banking.util.AppConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigTest {

    // Your existing tests stay here.

    @Test
    void loadsSettingsFromPropertiesFile() {
        AppConfig config = AppConfig.load();

        assertAll(
                () -> assertEquals(
                        "300",
                        config.getRequired("session.idle.timeout.seconds")
                ),
                () -> assertEquals(
                        "1000000.00",
                        config.getRequired("tx.max.amount")
                ),
                () -> assertEquals(
                        "bcrypt",
                        config.getRequired("password.hash.algorithm")
                ),
                () -> assertEquals(
                        "12",
                        config.getRequired("password.hash.cost")
                )
        );
    }

    @Test
    void rejectsMissingRequiredSetting() {
        AppConfig config = AppConfig.load();

        assertThrows(
                IllegalStateException.class,
                () -> config.getRequired("setting.that.does.not.exist")
        );
    }

    @Test
    void rejectsBlankConfigurationKey() {
        AppConfig config = AppConfig.load();

        assertThrows(
                IllegalArgumentException.class,
                () -> config.getRequired(" ")
        );
    }

    @Test
    void readsPositiveIntegerSettings() {
        AppConfig config = AppConfig.load();

        assertAll(
                () -> assertEquals(
                        300,
                        config.getPositiveInt("session.idle.timeout.seconds")
                ),
                () -> assertEquals(
                        10,
                        config.getPositiveInt("db.query.timeout.seconds")
                ),
                () -> assertEquals(
                        5,
                        config.getPositiveInt("auth.max.failed.logins")
                )
        );
    }

    @Test
    void rejectsTextWhenAnIntegerIsRequested() {
        AppConfig config = AppConfig.load();

        assertThrows(
                IllegalStateException.class,
                () -> config.getPositiveInt("password.hash.algorithm")
        );
    }

    @Test
    void rejectsDecimalWhenAnIntegerIsRequested() {
        AppConfig config = AppConfig.load();

        assertThrows(
                IllegalStateException.class,
                () -> config.getPositiveInt("tx.max.amount")
        );
    }

    @Test
    void rejectsZeroForPositiveIntegerSetting() {
        Properties properties = new Properties();
        properties.setProperty("auth.max.failed.logins", "0");

        AppConfig config = AppConfig.from(properties);

        assertThrows(
                IllegalStateException.class,
                () -> config.getPositiveInt("auth.max.failed.logins")
        );
    }

    @Test
    void rejectsNegativeIntegerSetting() {
        Properties properties = new Properties();
        properties.setProperty("auth.max.failed.logins", "-1");

        AppConfig config = AppConfig.from(properties);

        assertThrows(
                IllegalStateException.class,
                () -> config.getPositiveInt("auth.max.failed.logins")
        );
    }

    @Test
    void copiesSuppliedProperties() {
        Properties properties = new Properties();
        properties.setProperty("auth.max.failed.logins", "5");

        AppConfig config = AppConfig.from(properties);

        // Changing the original must not change AppConfig.
        properties.setProperty("auth.max.failed.logins", "99");

        assertEquals(
                5,
                config.getPositiveInt("auth.max.failed.logins")
        );
    }

    @Test
    void rejectsNullProperties() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AppConfig.from(null)
        );
    }
    @Test
    void acceptsConfiguredPasswordHashSettings() {
        AppConfig config = AppConfig.load();

        assertDoesNotThrow(config::validatePasswordHashSettings);
    }

    @Test
    void rejectsUnsupportedPasswordHashAlgorithm() {
        Properties properties = new Properties();
        properties.setProperty("password.hash.algorithm", "sha256");
        properties.setProperty("password.hash.cost", "12");

        AppConfig config = AppConfig.from(properties);

        assertThrows(
                IllegalStateException.class,
                config::validatePasswordHashSettings
        );
    }

    @Test
    void rejectsBcryptCostOutsideSupportedRange() {
        Properties properties = new Properties();
        properties.setProperty("password.hash.algorithm", "bcrypt");
        properties.setProperty("password.hash.cost", "3");

        AppConfig belowMinimum = AppConfig.from(properties);

        properties.setProperty("password.hash.cost", "32");

        AppConfig aboveMaximum = AppConfig.from(properties);

        assertAll(
                () -> assertThrows(
                        IllegalStateException.class,
                        belowMinimum::validatePasswordHashSettings
                ),
                () -> assertThrows(
                        IllegalStateException.class,
                        aboveMaximum::validatePasswordHashSettings
                )
        );
    }
    @Test
    void readsConfiguredMaximumAmount() {
        AppConfig config = AppConfig.load();

        assertEquals(
                new BigDecimal("1000000.00"),
                config.getPositiveDecimal("tx.max.amount")
        );
    }

    @Test
    void normalizesAcceptedAmountsToTwoDecimalPlaces() {
        assertAll(
                () -> assertEquals(
                        new BigDecimal("10.00"),
                        configWith("tx.max.amount", "10")
                                .getPositiveDecimal("tx.max.amount")
                ),
                () -> assertEquals(
                        new BigDecimal("10.10"),
                        configWith("tx.max.amount", "10.1")
                                .getPositiveDecimal("tx.max.amount")
                ),
                () -> assertEquals(
                        new BigDecimal("10.12"),
                        configWith("tx.max.amount", "10.12")
                                .getPositiveDecimal("tx.max.amount")
                )
        );
    }

    @Test
    void rejectsZeroAndNegativeDecimalAmounts() {
        assertAll(
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> configWith("tx.max.amount", "0")
                                .getPositiveDecimal("tx.max.amount")
                ),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> configWith("tx.max.amount", "-10.00")
                                .getPositiveDecimal("tx.max.amount")
                )
        );
    }

    @Test
    void rejectsMoreThanTwoDecimalPlacesWithoutRounding() {
        assertAll(
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> configWith("tx.max.amount", "10.123")
                                .getPositiveDecimal("tx.max.amount")
                ),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> configWith("tx.max.amount", "10.120")
                                .getPositiveDecimal("tx.max.amount")
                )
        );
    }

    @Test
    void rejectsInvalidDecimalFormats() {
        assertAll(
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> configWith("tx.max.amount", "abc")
                                .getPositiveDecimal("tx.max.amount")
                ),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> configWith("tx.max.amount", "1,000.00")
                                .getPositiveDecimal("tx.max.amount")
                ),
                () -> assertThrows(
                        IllegalStateException.class,
                        () -> configWith("tx.max.amount", "1E3")
                                .getPositiveDecimal("tx.max.amount")
                )
        );
    }

    private static AppConfig configWith(String key, String value) {
        Properties properties = new Properties();
        properties.setProperty(key, value);

        return AppConfig.from(properties);
    }
    @Test
    void readsRequiredEnvironmentVariable() {
        Map<String, String> environment = Map.of(
                "DB_URL",
                "jdbc:mysql://localhost:3306/banking_db"
        );

        assertEquals(
                "jdbc:mysql://localhost:3306/banking_db",
                AppConfig.getRequiredEnvironment("DB_URL", environment)
        );
    }

    @Test
    void rejectsMissingEnvironmentVariable() {
        Map<String, String> environment = Map.of();

        assertThrows(
                IllegalStateException.class,
                () -> AppConfig.getRequiredEnvironment(
                        "DB_PASSWORD",
                        environment
                )
        );
    }

    @Test
    void rejectsBlankEnvironmentVariable() {
        Map<String, String> environment = Map.of(
                "DB_PASSWORD",
                "   "
        );

        assertThrows(
                IllegalStateException.class,
                () -> AppConfig.getRequiredEnvironment(
                        "DB_PASSWORD",
                        environment
                )
        );
    }

    @Test
    void rejectsInvalidEnvironmentVariableName() {
        Map<String, String> environment = Map.of();

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> AppConfig.getRequiredEnvironment(
                                null,
                                environment
                        )
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> AppConfig.getRequiredEnvironment(
                                " ",
                                environment
                        )
                )
        );
    }

    @Test
    void preservesWhitespaceInEnvironmentValue() {
        String testValue = "  test-only-value  ";

        Map<String, String> environment = Map.of(
                "DB_PASSWORD",
                testValue
        );

        assertEquals(
                testValue,
                AppConfig.getRequiredEnvironment(
                        "DB_PASSWORD",
                        environment
                )
        );
    }
}