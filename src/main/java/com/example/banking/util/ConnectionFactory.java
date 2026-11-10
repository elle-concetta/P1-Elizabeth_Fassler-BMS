package com.example.banking.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

/**
 * Opens MySQL connections without running SQL or managing transactions.
 * The caller owns each returned connection and must close it.
 */
public final class ConnectionFactory {

    @FunctionalInterface
    public interface Connector {
        Connection connect(String url, Properties credentials) throws SQLException;
    }

    private final String url;
    private final String username;
    private final String password;
    private final Connector connector;

    public ConnectionFactory(String url, String username, String password) {
        this(url, username, password, DriverManager::getConnection);
    }

    /** Supplies the connection-opening operation so unit tests need no database. */
    public ConnectionFactory(
            String url, String username, String password, Connector connector
    ) {
        requireValue(url, "DB_URL");
        requireValue(username, "DB_USERNAME");
        requireValue(password, "DB_PASSWORD");
        if (!url.trim().startsWith("jdbc:mysql:")) {
            throw new IllegalArgumentException(
                    "DB_URL must be a MySQL JDBC URL."
            );
        }
        if (connector == null) {
            throw new IllegalArgumentException("Connector must not be null.");
        }
        this.url = url.trim();
        this.username = username.trim();
        this.password = password;
        this.connector = connector;
    }

    public static ConnectionFactory fromEnvironment() {
        return fromEnvironment(System.getenv(), DriverManager::getConnection);
    }

    /** Tests supply synthetic settings and a mock connector. */
    public static ConnectionFactory fromEnvironment(
            Map<String, String> environment, Connector connector
    ) {
        return new ConnectionFactory(
                AppConfig.getRequiredEnvironment("DB_URL", environment),
                AppConfig.getRequiredEnvironment("DB_USERNAME", environment),
                AppConfig.getRequiredEnvironment("DB_PASSWORD", environment),
                connector
        );
    }

    public Connection getConnection() throws SQLException {
        Properties credentials = new Properties();
        credentials.setProperty("user", username);
        credentials.setProperty("password", password);

        Connection connection = connector.connect(url, credentials);
        if (connection == null) {
            throw new SQLException("Connector did not return a connection.", "08001");
        }
        return connection;
    }

    private static void requireValue(String value, String setting) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required database setting: " + setting
            );
        }
    }
}
