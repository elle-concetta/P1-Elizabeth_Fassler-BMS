package com.example.banking.unit;

import com.example.banking.app.CliPresentation;
import com.example.banking.model.AccountNumber;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CliPresentationTest {
    @Test
    void bannerIsAsciiAndHasAlignedBordersAndPasswordGuidance() {
        String banner = CliPresentation.welcome();
        assertTrue(banner.chars().allMatch(c -> c < 128));
        assertTrue(banner.contains("S E C U R E   B A N K I N G"));
        assertTrue(banner.contains("Passwords are hidden"));
        for (String line : banner.split("\n")) {
            if (line.startsWith("|") || line.startsWith("+")) {
                assertEquals(62, line.length());
            }
        }
    }

    @Test
    void accountPresentationShowsOnlyTheLastFourDigits() {
        var account = AccountNumber.fromPayload("12345678901234567890123");
        assertEquals("****" + account.value().substring(20), account.masked());
        assertFalse(account.toString().contains(account.value()));
    }
}
