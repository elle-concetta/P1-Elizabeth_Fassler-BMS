package com.example.banking.unit;

import com.example.banking.app.*;
import com.example.banking.exception.*;
import com.example.banking.model.*;
import com.example.banking.service.AuthService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.util.List;
import static com.example.banking.unit.DepositFixtures.*;
import static com.example.banking.unit.HistoryFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HistoryCliTest {
    private AuthService auth;
    private StringWriter output;

    @BeforeEach void setup() {
        auth = mock(AuthService.class); output = new StringWriter();
        when(auth.currentCustomer()).thenReturn(customer(1, CustomerStatus.ACTIVE));
        when(auth.history(anyLong(), any())).thenAnswer(call -> {
            HistoryQuery q = call.getArgument(1);
            return new HistoryPage(7, NUMBER, q, List.of(), false);
        });
    }

    @Test void accountMenuOptionEightShowsUtcFieldsAndOnlyMaskedOwnedAccount() {
        var entry = row(1, TIME);
        doAnswer(call -> new HistoryPage(7, NUMBER,
                call.getArgument(1), List.of(entry), false)).when(auth).history(anyLong(), any());
        runAccounts("8\n7\n\n\n\n\n\n0\n0\n");
        verify(auth).history(7, query(0, 20));
        String text = output.toString();
        assertTrue(text.contains("8. Transaction history"));
        assertTrue(text.contains(NUMBER.masked()));
        assertTrue(text.contains(TIME.toString()));
        assertTrue(text.contains(entry.reference().value()));
        assertTrue(text.contains("TRANSFER | DEBIT | 0.25 | SUCCESS"));
        assertFalse(text.contains(NUMBER.value()));
        verify(auth, never()).transfer(anyLong(), anyString(), anyString());
        verify(auth, never()).deposit(anyLong(), anyString());
        verify(auth, never()).withdraw(anyLong(), anyString());
    }

    @Test void nextAndPreviousKeepFiltersAndDoNotFetchForInvalidNavigation() {
        doAnswer(call -> {
            HistoryQuery q = call.getArgument(1);
            return new HistoryPage(7, NUMBER, q, q.page() == 0 ? List.of(row(3, TIME), row(2, TIME)) : List.of(row(1, TIME)),
                    q.page() == 0);
        }).when(auth).history(anyLong(), any());
        runAccounts("8\n7\n2026-11-15\n2026-11-16\nTRANSFER\n2\n0\nP\nN\nN\nP\n0\n0\n");
        var first = new HistoryQuery(0, 2, HistoryQuery.date("2026-11-15"), HistoryQuery.date("2026-11-16"), TransactionType.TRANSFER);
        verify(auth, times(2)).history(7, first);
        verify(auth).history(7, first.atPage(1));
        assertTrue(output.toString().contains("Already on the first page."));
        assertTrue(output.toString().contains("No next page for these filters."));
    }

    @ParameterizedTest @ValueSource(strings = {"0", "51", "-1", "abc", "2147483648"})
    void invalidSizesNeverReachTheProtectedQuery(String size) {
        runAccounts("8\n7\n\n\n\n" + size + "\n0\n0\n");
        verify(auth, never()).history(anyLong(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"8\n/cancel\n0\n", "8\n7\n/cancel\n0\n",
            "8\n7\n\n/cancel\n0\n", "8\n7\n\n\n/cancel\n0\n", "8\n7\n\n\n\n/cancel\n0\n", "8\n7\n"})
    void cancellationAndEofDoNotSubmitAHistoryQuery(String script) {
        runAccounts(script);
        verify(auth, never()).history(anyLong(), any());
    }

    @Test void emptyOutOfRangePageAndEofReturnWithoutAnInfiniteLoop() {
        runAccounts("8\n7\n\n\n\n20\n999\n");
        verify(auth).history(7, query(999, 20));
        assertTrue(output.toString().contains("No transactions match this page and filters."));
    }

    @Test void expiryAfterFiltersDoesNotDisplayHistory() {
        doThrow(new SessionExpiredException()).when(auth).history(anyLong(), any());
        assertThrows(SessionExpiredException.class, () -> runAccounts("8\n7\n\n\n\n\n\n"));
        assertFalse(output.toString().contains("HISTORY | Account ID"));
    }

    private void runAccounts(String script) {
        try (var input = new ConsoleInput(new StringReader(script), new PrintWriter(output), () -> null)) {
            new AccountMenu(auth, input).run();
        } catch (IOException failure) { throw new AssertionError(failure); }
    }
}

