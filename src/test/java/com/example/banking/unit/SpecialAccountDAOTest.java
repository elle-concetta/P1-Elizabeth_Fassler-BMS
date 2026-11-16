
package com.example.banking.unit;

import com.example.banking.dao.jdbc.*;
import com.example.banking.model.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.sql.*;
import java.time.LocalDate;
import static com.example.banking.unit.DepositFixtures.*;
import static com.example.banking.unit.TransferFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SpecialAccountDAOTest {
    private Connection c; private PreparedStatement q; private ResultSet rows;
    private final JdbcSpecialAccountDAO dao = new JdbcSpecialAccountDAO(10);
    @BeforeEach void setup() throws Exception {
        c = mock(Connection.class); q = mock(PreparedStatement.class); rows = mock(ResultSet.class);
        when(c.prepareStatement(anyString())).thenReturn(q);
        when(c.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(q);
        when(q.executeUpdate()).thenReturn(1); when(q.getGeneratedKeys()).thenReturn(rows);
        when(rows.next()).thenReturn(true); when(rows.getLong(1)).thenReturn(11L);
    }
    @Test void dedicatedInsertBindsOwnerTypePrincipalAndExplicitDate() throws Exception {
        var today = LocalDate.now(CLOCK);
        assertEquals(11, dao.insert(c, 1, RECIPIENT, AccountType.LOAN, Money.parse("25"), today));
        verify(q).setLong(1, 1); verify(q).setString(2, RECIPIENT.value());
        verify(q).setString(3, "LOAN"); verify(q).setBigDecimal(4, Money.parse("25").toBigDecimal());
        verify(q).setObject(5, today, Types.DATE);
        verify(c, never()).commit(); verify(c, never()).close();
    }
    @Test void depositAndLoanDetailInsertsAreParameterizedAndZeroRate() throws Exception {
        var date = LocalDate.now(CLOCK);
        dao.depositDetails(c, 11, Money.parse("25"), 1, date);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(c).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("0.00"));
        verify(q).setObject(4, date, Types.DATE);
        dao.loanDetails(c, 11, Money.parse("25"), 12, date);
        verify(q).setInt(3, 12);
    }
    @Test void principalUpdateGuardsOwnerTypeStatusAndPriorBalance() throws Exception {
        var before = endpoint(11, 1, AccountType.LOAN, AccountStatus.ACTIVE, "25");
        dao.update(c, before, Money.ZERO, AccountStatus.CLOSED);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(c).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("customer_id = ? AND account_id = ? AND account_type = ? AND status = ? AND balance = ?"));
        verify(q).setBigDecimal(1, Money.ZERO.toBigDecimal()); verify(q).setString(2, "CLOSED");
        verify(q).setLong(3, 1); verify(q).setLong(4, 11); verify(q).setString(5, "LOAN");
        verify(q).setString(6, "ACTIVE"); verify(q).setBigDecimal(7, Money.parse("25").toBigDecimal());
    }
    @Test void maturityIncludesExactDateAndOnlyOwnedActiveDeposits() throws Exception {
        dao.matureOwned(c, 1, LocalDate.now(CLOCK));
        var sql = ArgumentCaptor.forClass(String.class); verify(c).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("a.customer_id = ?"));
        assertTrue(sql.getValue().contains("a.account_type = 'DEPOSIT' AND a.status = 'ACTIVE'"));
        assertTrue(sql.getValue().contains("d.maturity_date <= ?"));
        assertFalse(sql.getValue().contains("SET a.balance"));
    }
    @Test void wrongRowCountsCannotBeReportedAsSuccess() throws Exception {
        when(q.executeUpdate()).thenReturn(0, 2);
        assertThrows(SQLException.class, () -> dao.depositDetails(c, 11, Money.parse("25"), 1, LocalDate.now(CLOCK)));
        assertThrows(SQLException.class, () -> dao.loanDetails(c, 11, Money.parse("25"), 1, LocalDate.now(CLOCK)));
    }
    @Test void repaymentLedgerBindsDecreasingFundingAndDecreasingDebt() throws Exception {
        var funding = source("100");
        var loan = endpoint(11, 1, AccountType.LOAN, AccountStatus.ACTIVE, "25");
        var tx = BankTransaction.dedicated(REF, TransactionType.LOAN_REPAYMENT, funding, loan, Money.parse("10"), TIME);
        new JdbcTransactionDAO(10).insertRepayment(c, tx);
        var sql = ArgumentCaptor.forClass(String.class); verify(c).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("'LOAN_REPAYMENT'"));
        verify(q).setBigDecimal(5, Money.parse("100").toBigDecimal());
        verify(q).setBigDecimal(6, Money.parse("25").toBigDecimal());
        verify(q).setBigDecimal(7, Money.parse("90").toBigDecimal());
        verify(q).setBigDecimal(8, Money.parse("15").toBigDecimal());
    }
    @Test void ordinaryOperationsCannotUseDedicatedEndpointsAndCreationIsNotHistory() {
        var loan = endpoint(11, 1, AccountType.LOAN, AccountStatus.ACTIVE, "25");
        assertThrows(IllegalArgumentException.class, () -> BankTransaction.transfer(REF, source("100"), loan, Money.parse("10"), TIME));
        assertThrows(IllegalArgumentException.class, () -> BankTransaction.withdrawal(REF, loan, Money.parse("1"), TIME));
        assertThrows(com.example.banking.exception.ValidationException.class,
                () -> HistoryQuery.transactionType("LOAN_CREATION"));
    }
}
