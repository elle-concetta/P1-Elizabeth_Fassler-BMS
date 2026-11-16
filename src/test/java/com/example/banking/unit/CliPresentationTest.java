package com.example.banking.unit;

import com.example.banking.app.CliPresentation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CliPresentationTest {
    @Test
    void welcomeUsesAlignedAsciiWithoutColorsClearingOrConnectionClaims() {
        String welcome = CliPresentation.welcome();
        String[] lines = welcome.split("\n");
        assertEquals("+" + "-".repeat(60) + "+", lines[0]);
        for (int i = 1; i <= 12; i++) {
            assertEquals(62, lines[i].length());
            assertTrue(lines[i].startsWith("|") && lines[i].endsWith("|"));
        }
        assertEquals(lines[0], lines[13]);
        assertTrue(welcome.contains("S E C U R E   B A N K I N G"));
        assertTrue(welcome.contains("Passwords are hidden"));
        assertTrue(welcome.chars().allMatch(c -> c == '\n' || (c >= 32 && c <= 126)));
        assertFalse(welcome.contains("\u001b"));
        assertFalse(welcome.toLowerCase().contains("connected"));
    }
}
