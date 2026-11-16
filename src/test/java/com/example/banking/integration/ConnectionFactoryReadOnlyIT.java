package com.example.banking.integration;

import com.example.banking.util.AppConfig;
import com.example.banking.util.ConnectionFactory;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicit, read-only connectivity check against the local application database.
 * No fixtures, schema changes, or data cleanup belong in this test.
 */
class ConnectionFactoryReadOnlyIT {

    @Test
    void connectsToBankingDatabaseThroughFactory() throws SQLException {
        AppConfig config = AppConfig.load();
        ConnectionFactory factory = ConnectionFactory.fromEnvironment();

        try (Connection connection = factory.getConnection()) {
            connection.setReadOnly(true);

            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(
                        config.getPositiveInt("db.query.timeout.seconds")
                );

                try (ResultSet result = statement.executeQuery(
                        "SELECT DATABASE() AS current_database, 1 AS connection_check"
                )) {
                    assertTrue(result.next(), "Expected one result row.");
                    assertEquals(
                            "banking_db",
                            result.getString("current_database")
                    );
                    assertEquals(1, result.getInt("connection_check"));
                    assertFalse(result.next(), "Expected only one result row.");
                }
            }
        }
    }
}
