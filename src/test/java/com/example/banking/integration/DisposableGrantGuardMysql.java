package com.example.banking.integration;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Owns a new server, never attaches to a supplied URL or existing data directory.
 * Administrator connections are provisioning/observation only, never harness connections.
 */
final class DisposableGrantGuardMysql implements AutoCloseable {
    static final String APPROVAL = "PROVISION_DISPOSABLE_MYSQL_FOR_GRANT_GUARDS";
    static final String PARTIAL_SETUP_APPROVAL = "PROVISION_DISPOSABLE_MYSQL_FOR_PARTIAL_SETUP";
    private final Path directory;
    private final String binary;
    private final String password;
    private final int port;
    private Process server;

    DisposableGrantGuardMysql() throws Exception {
        this(false);
    }

    static DisposableGrantGuardMysql forPartialSetup() throws Exception {
        return new DisposableGrantGuardMysql(true);
    }

    private DisposableGrantGuardMysql(boolean partialSetup) throws Exception {
        String propertyPrefix = partialSetup ? "partialSetup" : "grantGuard";
        String approval = partialSetup ? PARTIAL_SETUP_APPROVAL : APPROVAL;
        if (!approval.equals(System.getProperty(propertyPrefix + ".approved"))) {
            throw new IllegalArgumentException("Separate disposable "
                    + (partialSetup ? "partial-setup" : "grant-test") + " approval is required.");
        }
        password = System.getenv("TEST_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("TEST_DB_PASSWORD is required, without application fallback.");
        }
        binary = System.getProperty(propertyPrefix + ".mysqld", "mysqld");
        Path parent = Path.of("generated-artifacts",
                partialSetup ? "partial-setup-live" : "grant-guard-live").toAbsolutePath();
        Files.createDirectories(parent);
        directory = Files.createTempDirectory(parent, "mysql-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        try (var socket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
            port = socket.getLocalPort();
        }
        try {
            var initialize = command("--initialize-insecure");
            initialize.redirectErrorStream(true).redirectOutput(directory.resolve("initialize.log").toFile());
            Process process = initialize.start();
            try {
                if (!process.waitFor(90, TimeUnit.SECONDS) || process.exitValue() != 0) {
                    throw new IllegalStateException("Disposable MySQL initialization failed; inspect generated logs.");
                }
            } finally {
                stop(process);
            }
            // The initial root@localhost is socket-only with skip-name-resolve.
            // Bootstrap an exact loopback account, never a wildcard host.
            Path bootstrap = directory.resolve("bootstrap.sql");
            Files.writeString(bootstrap,
                    "RENAME USER 'root'@'localhost' TO 'root'@'127.0.0.1';\n");
            server = command("--port=" + port, "--bind-address=127.0.0.1",
                    "--socket=" + directory.resolve("mysql.sock"),
                    "--pid-file=" + directory.resolve("mysql.pid"),
                    "--init-file=" + bootstrap,
                    "--mysqlx=0", "--skip-log-bin", "--skip-name-resolve")
                    .redirectErrorStream(true)
                    .redirectOutput(directory.resolve("server.log").toFile()).start();
            long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
            boolean ready = false;
            String lastFailure = "no connection attempted";
            while (server.isAlive() && System.nanoTime() < deadline) {
                Connection initial;
                try {
                    initial = connect("");
                } catch (java.sql.SQLException notReady) {
                    lastFailure = "SQLState " + notReady.getSQLState() + ", code " + notReady.getErrorCode();
                    Thread.sleep(100);
                    continue;
                }
                try (Connection connection = initial) {
                    verifyOwnedServer(connection);
                    try (var statement = connection.prepareStatement(
                            "ALTER USER 'root'@'127.0.0.1' IDENTIFIED BY ?")) {
                        statement.setString(1, password);
                        statement.execute();
                    }
                    ready = true;
                    break;
                }
            }
            if (!ready) {
                throw new IllegalStateException("Disposable MySQL did not become ready ("
                        + lastFailure + "); inspect generated logs.");
            }
            try (Connection connection = admin();
                 var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT VERSION()")) {
                result.next();
                String version = result.getString(1);
                if (!version.matches("8\\.(?:[4-9]|[1-9][0-9]+)\\..*")) {
                    throw new IllegalStateException("Disposable fixture tests require MySQL 8.4+ within supported MySQL 8.");
                }
                System.out.println("Disposable " + (partialSetup ? "partial-setup" : "grant-guard")
                        + " server: MySQL " + version);
            }
        } catch (Exception | Error failure) {
            close();
            throw failure;
        }
    }

    private ProcessBuilder command(String... arguments) {
        List<String> command = new ArrayList<>(List.of(binary, "--no-defaults",
                "--datadir=" + directory.resolve("data")));
        command.addAll(List.of(arguments));
        return new ProcessBuilder(command);
    }

    private Connection connect(String credential) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user", "root");
        properties.setProperty("password", credential);
        properties.setProperty("connectTimeout", "1000");
        properties.setProperty("socketTimeout", "5000");
        return DriverManager.getConnection(url(), properties);
    }

    Connection admin() throws Exception {
        if (server == null || !server.isAlive()) {
            throw new IllegalStateException("Owned disposable server is not running.");
        }
        Connection connection = connect(password);
        try {
            verifyOwnedServer(connection);
            return connection;
        } catch (Exception | Error failure) {
            connection.close();
            throw failure;
        }
    }

    private void verifyOwnedServer(Connection connection) throws Exception {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT @@datadir")) {
            if (!result.next() || !directory.resolve("data").toRealPath()
                    .equals(Path.of(result.getString(1)).toRealPath())) {
                throw new IllegalStateException("Refusing a server not owned by this test process.");
            }
        }
    }

    String url() {
        return "jdbc:mysql://127.0.0.1:" + port + "/";
    }

    private static void stop(Process process) throws InterruptedException {
        if (process != null && process.isAlive()) {
            process.destroy();
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                if (!process.waitFor(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Disposable MySQL did not stop.");
                }
            }
        }
    }

    @Override
    public void close() throws InterruptedException {
        stop(server);
    }
}
