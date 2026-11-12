package com.example.banking.unit;

import com.example.banking.exception.AuthenticationException;
import com.example.banking.exception.SessionExpiredException;
import com.example.banking.security.SessionManager;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionManagerTest {
    private final Instant start = Instant.parse("2026-11-11T12:00:00Z");

    @Test
    void checksBoundaryBeforeRefreshingAndInvalidatesExactlyOnce() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(start, start.plusSeconds(300));
        var sessions = new SessionManager(clock, Duration.ofSeconds(300));
        sessions.start(5);
        assertThrows(SessionExpiredException.class, sessions::requireActive);
        assertThrows(AuthenticationException.class, sessions::requireActive);
        assertTrue(sessions.end().isEmpty());
    }

    @Test
    void activityRefreshExtendsOnlyAStillValidSession() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(start, start.plusSeconds(299), start.plusSeconds(598));
        var sessions = new SessionManager(clock, Duration.ofSeconds(300));
        sessions.start(5);
        assertEquals(start.plusSeconds(299), sessions.requireActive().lastActivity());
        assertEquals(start.plusSeconds(598), sessions.requireActive().lastActivity());
        assertEquals(5, sessions.end().orElseThrow().customerId());
        assertThrows(AuthenticationException.class, sessions::requireActive);
    }

    @Test
    void backwardTimeAndInvalidConfigurationFailClosed() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(start, start.minusSeconds(1));
        var sessions = new SessionManager(clock, Duration.ofSeconds(300));
        sessions.start(5);
        assertThrows(SessionExpiredException.class, sessions::requireActive);
        assertThrows(IllegalArgumentException.class,
                () -> new SessionManager(clock, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> sessions.start(0));
    }
}

