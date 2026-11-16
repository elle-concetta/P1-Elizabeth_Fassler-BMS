package com.example.banking.app;

import com.example.banking.util.LoggerFactory;
import com.example.banking.util.AppConfig;
import com.example.banking.util.ConnectionFactory;
import com.example.banking.dao.jdbc.JdbcCustomerDAO;
import com.example.banking.dao.jdbc.JdbcLoginAuditDAO;
import com.example.banking.dao.jdbc.JdbcSecurityAuditDAO;
import com.example.banking.dao.jdbc.JdbcTransactionAuditDAO;
import com.example.banking.model.Money;
import com.example.banking.security.BcryptPasswordHasher;
import com.example.banking.security.LoginAuditDelivery;
import com.example.banking.security.SessionManager;
import com.example.banking.service.AuthService;
import com.example.banking.validation.CustomerValidator;
import org.slf4j.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;

/** Composition root for the Java/JDBC CLI; never creates or migrates a schema. */
public final class BankingApplication {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(BankingApplication.class);

    private BankingApplication() {}

    public static void main(String[] args) {
        try (ConsoleInput input = ConsoleInput.standard()) {
            AppConfig config = AppConfig.load();
            ConnectionFactory connections = ConnectionFactory.fromEnvironment();
            // Event timestamps are captured at the schema's declared precision,
            // not captured with fractions and later rounded by persistence.
            Clock clock = Clock.tickSeconds(ZoneOffset.UTC);
            var sessions = new SessionManager(clock,
                    Duration.ofSeconds(config.getPositiveInt("session.idle.timeout.seconds")));
            var auth = new AuthService(connections,
                    new JdbcCustomerDAO(config.getPositiveInt("db.query.timeout.seconds")),
                    new BcryptPasswordHasher(config.getPositiveInt("password.hash.cost")),
                    new CustomerValidator(clock), sessions,
                    new LoginAuditDelivery(new JdbcLoginAuditDAO(connections)), clock,
                    config.getPositiveInt("auth.max.failed.logins"),
                    Duration.ofMinutes(config.getPositiveInt("auth.lock.minutes")),
                    config.getPositiveInt("db.query.timeout.seconds"),
                    new JdbcSecurityAuditDAO(config.getPositiveInt("db.query.timeout.seconds")),
                    new Money(config.getPositiveDecimal("tx.max.amount")),
                    new JdbcTransactionAuditDAO(connections, config.getPositiveInt("db.query.timeout.seconds")),
                    new Money(config.getPositiveDecimal("deposit.min.principal")), true);
            LOGGER.info("Banking CLI started.");
            new MainMenu(auth, input).run();
        } catch (Exception failure) {
            // Never print arbitrary exception messages or graphs (driver data,
            // credentials, hashes). The CLI's safe domain errors are handled in menus.
            System.err.println("The CLI could not start or continue. Check Java 27, app.properties, "
                    + "and the DB_URL, DB_USERNAME and DB_PASSWORD settings. No schema was changed.");
            System.exit(1);
        }
    }
}
