package com.example.banking.unit;

import com.example.banking.integration.LoginAuditTestDatabase;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Configuration checks only: deliberately never opens JDBC connections. */
class LoginAuditDatabaseGuardTest {
    private static Map<String, String> valid() {
        return new HashMap<>(Map.of(
                "LOGIN_AUDIT_IT_APPROVED", LoginAuditTestDatabase.APPROVAL,
                "TEST_DB_URL", "jdbc:mysql://127.0.0.1:3307/banking_test",
                "TEST_DB_USERNAME", "banking_test_runner",
                "TEST_DB_PASSWORD", "synthetic-guard-test-value"));
    }

    @Test
    void acceptsOnlyExplicitTestSettings() {
        assertDoesNotThrow(() -> LoginAuditTestDatabase.validateSettings(valid()));
    }

    @Test
    void acceptsRawEscapedTestDatabaseGrantAndExistingSpellings() {
        for (String grant : new String[]{
                "GRANT USAGE ON *.* TO `banking_test_runner`@`localhost`",
                "GRANT SELECT, INSERT, CREATE, DROP, REFERENCES ON `banking\\_test`.* TO `banking_test_runner`@`localhost`",
                "GRANT SELECT ON `banking_test`.* TO `banking_test_runner`@`localhost`"}) {
            assertDoesNotThrow(() -> LoginAuditTestDatabase.validateGrant(grant));
        }
    }

    @Test
    void rejectsOtherSchemasGlobalRolesObjectGrantsAndGrantOption() {
        for (String grant : new String[]{
                "GRANT SELECT ON `banking_db`.* TO `banking_test_runner`@`localhost`",
                "GRANT SELECT ON `bankingXtest`.* TO `banking_test_runner`@`localhost`",
                "GRANT SELECT ON `banking\\_test_extra`.* TO `banking_test_runner`@`localhost`",
                "GRANT SELECT ON *.* TO `banking_test_runner`@`localhost`",
                "GRANT SELECT ON `banking_test`.`customers` TO `banking_test_runner`@`localhost`",
                "GRANT SELECT (`customer_id`) ON `banking_test`.`customers` TO `banking_test_runner`@`localhost`",
                "GRANT EXECUTE ON PROCEDURE `banking_test`.`outside_access` TO `banking_test_runner`@`localhost`",
                "GRANT `reader`@`localhost` TO `banking_test_runner`@`localhost`",
                "GRANT SELECT ON `banking\\_test`.* TO `banking_test_runner`@`localhost` WITH GRANT OPTION",
                "GRANT SELECT ON `banking\\\\_test`.* TO `banking_test_runner`@`localhost`",
                ""}) {
            assertThrows(IllegalStateException.class,
                    () -> LoginAuditTestDatabase.validateGrant(grant), grant);
        }
        assertThrows(IllegalStateException.class,
                () -> LoginAuditTestDatabase.validateGrant(null));
    }

    @Test
    void eachRequiredSettingFailsClosedWhenMissingOrBlank() {
        for (String key : valid().keySet()) {
            var missing = valid();
            missing.remove(key);
            assertThrows(IllegalArgumentException.class,
                    () -> LoginAuditTestDatabase.validateSettings(missing));
            missing.put(key, " ");
            assertThrows(IllegalArgumentException.class,
                    () -> LoginAuditTestDatabase.validateSettings(missing));
        }
    }

    @Test
    void refusesUnsafeTargetsAndUrlOverrides() {
        for (String url : new String[]{
                "jdbc:mysql://127.0.0.1:3307/banking_db",
                "jdbc:mysql://example.invalid:3307/banking_test",
                "jdbc:mysql://127.0.0.1:3307/banking_test?sessionVariables=x",
                "jdbc:mysql://127.0.0.1:3307/banking_test?user=root",
                "jdbc:mysql://root:password@127.0.0.1:3307/banking_test",
                "jdbc:mysql:loadbalance://127.0.0.1:3307/banking_test",
                "jdbc:mysql://127.0.0.1:3307/%62anking_test",
                "jdbc:mysql://127.0.0.1/banking_test"}) {
            var settings = valid();
            settings.put("TEST_DB_URL", url);
            assertThrows(IllegalArgumentException.class,
                    () -> LoginAuditTestDatabase.validateSettings(settings));
        }
    }

    @Test
    void refusesApplicationFallbackAndAdministrator() {
        assertThrows(IllegalArgumentException.class,
                () -> LoginAuditTestDatabase.validateSettings(Map.of(
                        "DB_URL", "jdbc:mysql://127.0.0.1:3306/banking_db",
                        "DB_USERNAME", "app", "DB_PASSWORD", "synthetic")));
        var settings = valid();
        settings.put("TEST_DB_USERNAME", "root");
        assertThrows(IllegalArgumentException.class,
                () -> LoginAuditTestDatabase.validateSettings(settings));
        var wrongApproval = valid();
        wrongApproval.put("LOGIN_AUDIT_IT_APPROVED", "true");
        assertThrows(IllegalArgumentException.class,
                () -> LoginAuditTestDatabase.validateSettings(wrongApproval));
    }
}
