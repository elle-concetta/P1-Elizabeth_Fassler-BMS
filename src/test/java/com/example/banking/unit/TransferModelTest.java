package com.example.banking.unit;

import com.example.banking.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

import static com.example.banking.unit.DepositFixtures.*;
import static com.example.banking.unit.TransferFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class TransferModelTest {
    @Test void candidateConservesMoneyAndHasExactIndependentSnapshots() {
        var source = source("100");
        var destination = recipient(11, 2, "20");
        var tx = BankTransaction.transfer(REF, source, destination, Money.parse("25"), TIME);
        assertEquals(TransactionType.TRANSFER, tx.type());
        assertEquals(7, tx.sourceId());
        assertEquals(11, tx.destinationId());
        assertEquals(Money.parse("100"), tx.balanceBefore());
        assertEquals(Money.parse("75"), tx.balanceAfter());
        assertEquals(Money.parse("20"), tx.destinationBalanceBefore());
        assertEquals(Money.parse("45"), tx.destinationBalanceAfter());
        assertEquals(tx.balanceBefore().add(tx.destinationBalanceBefore()),
                tx.balanceAfter().add(tx.destinationBalanceAfter()));
        assertEquals(Money.parse("100"), source.balance());
        assertEquals(Money.parse("20"), destination.balance());
    }

    @ParameterizedTest @EnumSource(value = AccountType.class, names = {"SAVINGS", "CURRENT"})
    void eitherOrdinaryTypeCanBeSourceOrDestination(AccountType type) {
        var source = endpoint(7, 1, type, AccountStatus.ACTIVE, "1");
        var destination = endpoint(11, 2, type, AccountStatus.ACTIVE, "0");
        assertEquals(Money.ZERO, BankTransaction.transfer(REF, source, destination, Money.parse("1"), TIME).balanceAfter());
    }

    @Test void sameIdOrSameNumberCannotBeTransferredBetween() {
        assertThrows(IllegalArgumentException.class,
                () -> BankTransaction.transfer(REF, source("100"), source("100"), Money.parse("1"), TIME));
        var sameNumber = new CurrentAccount(11, 2, NUMBER, Money.ZERO, BigDecimal.ZERO,
                Money.ZERO, LocalDate.of(2026, 11, 15), AccountStatus.ACTIVE);
        assertThrows(IllegalArgumentException.class,
                () -> BankTransaction.transfer(REF, source("100"), sameNumber, Money.parse("1"), TIME));
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "100.01"})
    void nonpositiveOrUnaffordableAmountsCannotCreateACandidate(String amount) {
        assertThrows(IllegalArgumentException.class, () -> BankTransaction.transfer(REF,
                source("100"), recipient(11, 2, "20"), Money.parse(amount), TIME));
    }

    @ParameterizedTest @EnumSource(value = AccountStatus.class, names = {"FROZEN", "MATURED", "CLOSED"})
    void inactiveEndpointOnEitherSideIsRejected(AccountStatus status) {
        assertThrows(IllegalArgumentException.class, () -> BankTransaction.transfer(REF,
                endpoint(7, 1, AccountType.SAVINGS, status, "100"), recipient(11, 2, "20"), Money.parse("1"), TIME));
        assertThrows(IllegalArgumentException.class, () -> BankTransaction.transfer(REF,
                source("100"), endpoint(11, 2, AccountType.CURRENT, status, "20"), Money.parse("1"), TIME));
    }

    @ParameterizedTest @EnumSource(value = AccountType.class, names = {"DEPOSIT", "LOAN"})
    void principalAccountsAreNotOrdinaryTransferEndpoints(AccountType type) {
        assertThrows(IllegalArgumentException.class, () -> BankTransaction.transfer(REF,
                endpoint(7, 1, type, AccountStatus.ACTIVE, "100"), recipient(11, 2, "20"), Money.parse("1"), TIME));
        assertThrows(IllegalArgumentException.class, () -> BankTransaction.transfer(REF,
                source("100"), endpoint(11, 2, type, AccountStatus.ACTIVE, "20"), Money.parse("1"), TIME));
    }

    @Test void destinationOverflowAndSourceMinimumAreRejectedWithoutRounding() {
        assertThrows(IllegalArgumentException.class, () -> BankTransaction.transfer(REF,
                source("100"), recipient(11, 2, "99999999999999999.99"), Money.parse("0.01"), TIME));
        var minimum = new SavingsAccount(7, 1, NUMBER, Money.parse("100"), BigDecimal.ZERO,
                Money.parse("90"), LocalDate.of(2026, 11, 15), AccountStatus.ACTIVE);
        assertThrows(IllegalArgumentException.class, () -> BankTransaction.transfer(REF,
                minimum, recipient(11, 2, "20"), Money.parse("10.01"), TIME));
    }

    @Test void senderReceiptHasNoRecipientBalanceIdentityOrLedgerAccessor() {
        var receipt = TransferReceipt.from(candidate());
        assertEquals(REF, receipt.reference());
        assertEquals(7, receipt.sourceId());
        assertEquals(NUMBER, receipt.sourceNumber());
        assertEquals(RECIPIENT, receipt.destinationNumber());
        assertEquals(Money.parse("75"), receipt.sourceBalanceAfter());
        var methods = Arrays.stream(TransferReceipt.class.getDeclaredMethods()).map(m -> m.getName()).toList();
        assertFalse(methods.contains("destinationId"));
        assertFalse(methods.contains("destinationBalanceBefore"));
        assertFalse(methods.contains("destinationBalanceAfter"));
        assertFalse(methods.contains("transaction"));
        assertFalse(receipt.toString().contains(NUMBER.value()));
        assertFalse(receipt.toString().contains(RECIPIENT.value()));
        assertFalse(receipt.toString().contains(REF.value()));
    }

    @Test void ordinaryReceiptsCannotBePassedOffAsTransfers() {
        assertThrows(IllegalArgumentException.class, () -> TransferReceipt.from(receipt("1")));
    }
}

