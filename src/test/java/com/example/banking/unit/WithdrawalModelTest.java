package com.example.banking.unit;

import com.example.banking.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.example.banking.unit.DepositFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class WithdrawalModelTest {
    @ParameterizedTest @EnumSource(value = AccountType.class, names = {"SAVINGS", "CURRENT"})
    void exactDebitProducesAnImmutableMaskedSourceReceipt(AccountType type) {
        Account before = account(1, type, AccountStatus.ACTIVE, "100");
        var tx = BankTransaction.withdrawal(REF, before, Money.parse("25.10"), TIME);
        assertEquals(TransactionType.WITHDRAWAL, tx.type());
        assertEquals(7, tx.sourceId());
        assertEquals(7, tx.accountId());
        assertThrows(IllegalStateException.class, tx::destinationId);
        assertEquals(Money.parse("100"), tx.balanceBefore());
        assertEquals(Money.parse("74.90"), tx.balanceAfter());
        assertEquals(Money.parse("100"), before.balance());
        assertEquals(TIME, tx.createdAt());
        assertTrue(tx.toString().contains(NUMBER.masked()));
        assertFalse(tx.toString().contains(NUMBER.value()));
        assertFalse(tx.toString().contains(REF.value()));
    }

    @Test void allFundsMayBeWithdrawnWhenTheStoredMinimumIsZero() {
        var tx = BankTransaction.withdrawal(REF, account(1, AccountType.CURRENT, AccountStatus.ACTIVE, "0.01"),
                Money.parse("0.01"), TIME);
        assertEquals(Money.ZERO, tx.balanceAfter());
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-0.01", "100.01"})
    void invalidOrUnaffordableAmountsCannotConstructAWithdrawal(String amount) {
        assertThrows(IllegalArgumentException.class, () -> BankTransaction.withdrawal(REF,
                account(1, AccountType.SAVINGS, AccountStatus.ACTIVE, "100"), Money.parse(amount), TIME));
    }

    @ParameterizedTest @EnumSource(value = AccountStatus.class, names = {"FROZEN", "MATURED", "CLOSED"})
    void inactiveAccountsCannotBeDebited(AccountStatus status) {
        assertThrows(IllegalArgumentException.class, () -> BankTransaction.withdrawal(REF,
                account(1, AccountType.SAVINGS, status, "100"), Money.parse("1"), TIME));
    }

    @ParameterizedTest @EnumSource(value = AccountType.class, names = {"DEPOSIT", "LOAN"})
    void principalAccountsAreNotOrdinaryWithdrawalSources(AccountType type) {
        assertThrows(IllegalArgumentException.class, () -> BankTransaction.withdrawal(REF,
                account(1, type, AccountStatus.ACTIVE, "100"), Money.parse("1"), TIME));
    }

    @Test void nonzeroStoredMinimumIsRespectedExactly() {
        var account = new SavingsAccount(7, 1, NUMBER, Money.parse("100"), BigDecimal.ZERO,
                Money.parse("10"), LocalDate.of(2026, 11, 15), AccountStatus.ACTIVE);
        assertEquals(Money.parse("10"), BankTransaction.withdrawal(REF, account, Money.parse("90"), TIME).balanceAfter());
        assertThrows(IllegalArgumentException.class,
                () -> BankTransaction.withdrawal(REF, account, Money.parse("90.01"), TIME));
    }

    @Test void depositCannotBeMistakenForASourceReceipt() {
        assertThrows(IllegalStateException.class, () -> receipt("1").sourceId());
        assertEquals(TransactionType.DEPOSIT, receipt("1").type());
    }
}

