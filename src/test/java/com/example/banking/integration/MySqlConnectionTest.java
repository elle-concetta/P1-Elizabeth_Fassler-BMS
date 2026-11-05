package com.example.banking.integration;

import org.junit.jupiter.api.Test;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlConnectionTest {
    @Test
    void shouldConnectToMySqlAndExecuteQuery() throws Exception {
        String url = requiredEnvironmentVariable("BANK_DB_URL");
        String username = requiredEnvironmentVariable("BANK_DB_USER");
        String password = requiredEnvironmentVariable("BANK_DB_PASSWORD");
        try (Connection connection =
                     DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT 1")) {
            assertTrue(result.next(), "MySQL should return a result");
            assertEquals(1, result.getInt(1));
        }
    }
    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required environment variable: " + name
            );
        }
        return value;
    }
}
