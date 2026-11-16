package com.example.banking.unit;

import com.example.banking.app.*;
import com.example.banking.model.*;
import com.example.banking.service.AuthService;
import org.junit.jupiter.api.*;
import java.io.*;
import static com.example.banking.unit.DepositFixtures.*;
import static com.example.banking.unit.TransferFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SpecialAccountCliTest {
    private AuthService auth; private StringWriter output;
    @BeforeEach void setup() {
        auth = mock(AuthService.class); output = new StringWriter();
        when(auth.currentCustomer()).thenReturn(customer(1, CustomerStatus.ACTIVE));
    }
    @Test void fundedDepositFormPassesOnlyConfirmedValuesAndMasksNumber() throws Exception {
        var receipt = new SpecialReceipt(REF, "Term Deposit opened.", endpoint(11, 1, AccountType.DEPOSIT, AccountStatus.ACTIVE, "25"), null);
        when(auth.openTermDeposit(7, "25", 1)).thenReturn(receipt);
        run(false, "1\n7\n25\n1\nOPEN\n0\n");
        verify(auth).openTermDeposit(7, "25", 1);
        assertTrue(output.toString().contains(RECIPIENT.masked()));
        assertFalse(output.toString().contains(RECIPIENT.value()));
    }
    @Test void loanCreationDoesNotPromiseACashDisbursement() throws Exception {
        when(auth.openLoan(eq("25"), eq(12), isNull())).thenReturn(new SpecialReceipt(REF,
                "Loan created as outstanding debt only.", endpoint(11, 1, AccountType.LOAN, AccountStatus.ACTIVE, "25"), null));
        run(true, "1\n25\n12\n\nCREATE\n0\n");
        verify(auth).openLoan("25", 12, null);
        assertTrue(output.toString().contains("no cash is paid"));
    }
    @Test void settlementAndRepaymentHaveSeparateExplicitConfirmations() throws Exception {
        when(auth.settleTermDeposit(11, 7)).thenReturn(new SpecialReceipt(REF, "Settled.", endpoint(11, 1, AccountType.DEPOSIT, AccountStatus.CLOSED, "0"), null));
        run(false, "2\n11\n7\nSETTLE\n0\n"); verify(auth).settleTermDeposit(11, 7);
        when(auth.repayLoan(7, 11, "10")).thenReturn(new SpecialReceipt(REF, "Repaid.", endpoint(11, 1, AccountType.LOAN, AccountStatus.ACTIVE, "15"), null));
        run(true, "2\n7\n11\n10\nREPAY\n0\n"); verify(auth).repayLoan(7, 11, "10");
    }
    @Test void allFourCancellationsAvoidAnyFinancialSubmission() throws Exception {
        run(false, "1\n7\n25\n1\n\n0\n"); run(false, "2\n11\n7\n\n0\n");
        run(true, "1\n25\n12\n\n\n0\n"); run(true, "2\n7\n11\n10\n\n0\n");
        verify(auth, never()).openTermDeposit(anyLong(), anyString(), anyInt());
        verify(auth, never()).settleTermDeposit(anyLong(), anyLong());
        verify(auth, never()).openLoan(anyString(), anyInt(), any());
        verify(auth, never()).repayLoan(anyLong(), anyLong(), anyString());
    }
    private void run(boolean loan, String script) throws Exception {
        try (var input = new ConsoleInput(new StringReader(script), new PrintWriter(output), () -> null)) {
            new SpecialAccountMenu(auth, input).run(loan);
        }
    }
}

