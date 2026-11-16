package com.example.banking.unit;

import com.example.banking.exception.ValidationException;
import com.example.banking.model.*;
import com.example.banking.validation.TransactionValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Instant;

import static com.example.banking.unit.DepositFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class DepositValidationTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "0", "0.00", "-1", "+1", "1.001", "1.000", "1e2", "1,000", " 1", "1 ", ".5", "1.", "NaN", "1000000.01", "999999999999999999.99"})
    void invalidAmountsAreRejectedWithoutRounding(String supplied) {
        assertThrows(ValidationException.class, () -> TransactionValidator.amount(supplied, LIMIT));
    }

    @Test void nullAndEnormousInputAreRejectedWithoutParsing() {
        assertThrows(ValidationException.class, () -> TransactionValidator.amount(null, LIMIT));
        assertThrows(ValidationException.class, () -> TransactionValidator.amount("9".repeat(100000), LIMIT));
    }

    @ParameterizedTest @ValueSource(strings = {"0.01", "25", "25.5", "25.50", "1000000.00"})
    void validAmountsKeepExactDecimalValue(String supplied) {
        assertEquals(Money.parse(supplied), TransactionValidator.amount(supplied, LIMIT));
    }

    @Test void configuredMaximumIsEnforcedRatherThanHardcoded() {
        assertEquals(Money.parse("7.25"), TransactionValidator.amount("7.25", Money.parse("7.25")));
        assertThrows(ValidationException.class, () -> TransactionValidator.amount("7.26", Money.parse("7.25")));
        assertThrows(IllegalArgumentException.class, () -> TransactionValidator.amount("1", Money.ZERO));
    }

    @Test void exactTimeBoundsAreSupportedButFractionsAndOutsideValuesAreNot() {
        assertTrue(TransactionValidator.supportedTime(Instant.parse("1970-01-01T00:00:01Z")));
        assertTrue(TransactionValidator.supportedTime(Instant.parse("2038-01-19T03:14:07Z")));
        assertFalse(TransactionValidator.supportedTime(Instant.parse("1970-01-01T00:00:00Z")));
        assertFalse(TransactionValidator.supportedTime(Instant.parse("2038-01-19T03:14:08Z")));
        assertFalse(TransactionValidator.supportedTime(TIME.plusNanos(1)));
        assertFalse(TransactionValidator.supportedTime(null));
    }

    @Test void ledgerCandidateUsesExactSnapshotsAndSafeRendering() {
        Account account = account(1, AccountType.CURRENT, AccountStatus.ACTIVE, "10.10");
        BankTransaction tx = BankTransaction.deposit(REF, account, Money.parse("0.20"), TIME);
        assertEquals(Money.parse("10.10"), tx.balanceBefore());
        assertEquals(Money.parse("10.30"), tx.balanceAfter());
        assertEquals(Money.parse("10.10"), account.balance()); // snapshot wasn't mutated
        assertEquals(REF, tx.reference());
        assertEquals(7, tx.destinationId());
        assertTrue(tx.toString().contains(NUMBER.masked()));
        assertFalse(tx.toString().contains(NUMBER.value()));
        assertFalse(tx.toString().contains(REF.value()));
    }

    @Test void invalidLedgerCandidatesCannotBeConstructed() {
        assertThrows(IllegalArgumentException.class, () -> BankTransaction.deposit(REF,
                account(1, AccountType.SAVINGS, AccountStatus.ACTIVE, "0"), Money.ZERO, TIME));
        assertThrows(IllegalArgumentException.class, () -> BankTransaction.deposit(REF,
                account(1, AccountType.LOAN, AccountStatus.ACTIVE, "0"), Money.parse("1"), TIME));
        assertThrows(IllegalArgumentException.class, () -> BankTransaction.deposit(REF,
                account(1, AccountType.CURRENT, AccountStatus.FROZEN, "0"), Money.parse("1"), TIME));
        assertThrows(IllegalArgumentException.class, () -> BankTransaction.deposit(REF,
                account(1, AccountType.CURRENT, AccountStatus.ACTIVE, "99999999999999999.99"), Money.parse("0.01"), TIME));
    }
}
