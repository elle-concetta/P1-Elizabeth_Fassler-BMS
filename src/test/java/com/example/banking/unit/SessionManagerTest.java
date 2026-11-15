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
    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

    @Test
    void expiresAtTheExactIdleBoundaryWithoutRefreshingActivity() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW, NOW.plusSeconds(300));
        var sessions = new SessionManager(clock, Duration.ofSeconds(300));
        sessions.start(7);
        assertThrows(SessionExpiredException.class, sessions::requireActive);
        assertThrows(AuthenticationException.class, sessions::requireActive);
    }

    @Test
    void activityBeforeExpiryRefreshesTheIdleDeadline() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW, NOW.plusSeconds(299), NOW.plusSeconds(500));
        var sessions = new SessionManager(clock, Duration.ofSeconds(300));
        sessions.start(7);
        assertEquals(NOW.plusSeconds(299), sessions.requireActive().lastActivity());
        assertEquals(7, sessions.requireActive().customerId());
    }

    @Test
    void logoutClearsTheSessionAndDuplicateStartsAreRejected() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW);
        var sessions = new SessionManager(clock, Duration.ofSeconds(300));
        sessions.start(7);
        assertThrows(IllegalStateException.class, () -> sessions.start(8));
        assertEquals(7, sessions.end().orElseThrow().customerId());
        assertTrue(sessions.end().isEmpty());
        assertThrows(AuthenticationException.class, sessions::requireActive);
    }

    @Test
    void backwardsTimeInvalidatesTheSession() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW, NOW.minusSeconds(1));
        var sessions = new SessionManager(clock, Duration.ofSeconds(300));
        sessions.start(7);
        assertThrows(SessionExpiredException.class, sessions::requireActive);
    }
}
