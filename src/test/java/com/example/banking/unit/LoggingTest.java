package com.example.banking.unit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.status.Status;
import com.example.banking.util.LoggerFactory;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingTest {

    @Test
    void usesLogbackWithTheSourceClassName() {
        Logger logger = LoggerFactory.getLogger(LoggingTest.class);

        assertInstanceOf(ch.qos.logback.classic.Logger.class, logger);
        assertEquals(LoggingTest.class.getName(), logger.getName());
    }

    @Test
    void rejectsNullSourceClass() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LoggerFactory.getLogger(null)
        );
    }

    @Test
    void masksLongAccountNumbersToTheirLastFourCharacters() {
        assertAll(
                () -> assertEquals(
                        "****5678", LoggerFactory.maskAccountNumber("12345678")
                ),
                () -> assertEquals(
                        "****AB12", LoggerFactory.maskAccountNumber("ACCT99AB12")
                )
        );
    }

    @Test
    void fullyHidesMissingShortOrMalformedAccountNumbers() {
        assertAll(
                () -> assertEquals("****", LoggerFactory.maskAccountNumber(null)),
                () -> assertEquals("****", LoggerFactory.maskAccountNumber("")),
                () -> assertEquals("****", LoggerFactory.maskAccountNumber("1234")),
                () -> assertEquals("****", LoggerFactory.maskAccountNumber("12")),
                () -> assertEquals("****", LoggerFactory.maskAccountNumber("1234\n5678"))
        );
    }

    @Test
    void loadsConsoleConfigurationWithoutLogbackErrors() {
        LoggerContext context = assertInstanceOf(
                LoggerContext.class, org.slf4j.LoggerFactory.getILoggerFactory()
        );
        ch.qos.logback.classic.Logger root =
                context.getLogger(Logger.ROOT_LOGGER_NAME);
        ConsoleAppender<?> console = assertInstanceOf(
                ConsoleAppender.class, root.getAppender("CONSOLE")
        );

        assertAll(
                () -> assertEquals(Level.INFO, root.getLevel()),
                () -> assertEquals("System.err", console.getTarget()),
                () -> assertTrue(console.isStarted()),
                () -> assertFalse(
                        context.getStatusManager().getCopyOfStatusList().stream()
                                .anyMatch(status -> status.getLevel() == Status.ERROR),
                        "Logback reported a configuration error."
                )
        );
    }

    @Test
    void formatsParameterizedMessageWithoutTheFullAccountNumber() {
        ch.qos.logback.classic.Logger logger = assertInstanceOf(
                ch.qos.logback.classic.Logger.class,
                LoggerFactory.getLogger(LoggingTest.class)
        );
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(logger.getLoggerContext());
        appender.start();

        Level previousLevel = logger.getLevel();
        boolean previousAdditivity = logger.isAdditive();
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        logger.addAppender(appender);

        try {
            String syntheticAccountNumber = "12345678";
            logger.info(
                    "Connection demo reference={} account={}",
                    "DEMO-ONLY",
                    LoggerFactory.maskAccountNumber(syntheticAccountNumber)
            );

            assertEquals(1, appender.list.size());
            ILoggingEvent event = appender.list.get(0);
            assertNotNull(event);
            assertEquals(Level.INFO, event.getLevel());
            assertEquals(
                    "Connection demo reference=DEMO-ONLY account=****5678",
                    event.getFormattedMessage()
            );
            assertFalse(event.getFormattedMessage().contains(syntheticAccountNumber));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            logger.setAdditive(previousAdditivity);
            appender.stop();
        }
    }
}

