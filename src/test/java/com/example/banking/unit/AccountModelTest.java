package com.example.banking.unit;

import com.example.banking.model.*;
import com.example.banking.exception.ValidationException;
import com.example.banking.validation.AccountValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.example.banking.unit.AccountFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class AccountModelTest {
    @ParameterizedTest @EnumSource(AccountType.class)
    void allTypesHaveImmutableSnapshotsAndMaskedRendering(AccountType type) {
        Account account = account(type, 7, 1, NUMBER, AccountStatus.ACTIVE);
        assertEquals(type, account.type());
        assertEquals(7, account.accountId());
        assertEquals(1, account.customerId());
        assertEquals(NUMBER, account.number());
        assertEquals(Money.ZERO, account.balance());
        assertEquals(DATE, account.openingDate());
        assertTrue(account.toString().contains(NUMBER.masked()));
        assertFalse(account.toString().contains(NUMBER.value()));
        assertFalse(account.toString().contains("customerId"));
    }

    @ParameterizedTest @EnumSource(AccountStatus.class)
    void nonActiveAccountsRemainReadable(AccountStatus status) {
        assertEquals(status, account(AccountType.SAVINGS, 7, 1, NUMBER, status).status());
    }

    @Test void principalIsNotPresentedAsSpendableBalance() {
        assertEquals("Locked principal", account(AccountType.DEPOSIT, 7, 1, NUMBER, AccountStatus.ACTIVE).balanceLabel());
        assertEquals("Outstanding principal (not spendable)",
                account(AccountType.LOAN, 7, 1, NUMBER, AccountStatus.ACTIVE).balanceLabel());
        assertEquals("Balance", account(AccountType.CURRENT, 7, 1, NUMBER, AccountStatus.ACTIVE).balanceLabel());
    }

    @Test void invalidIdentitiesAndNegativeBalancesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> account(AccountType.SAVINGS, 0, 1, NUMBER, AccountStatus.ACTIVE));
        assertThrows(IllegalArgumentException.class, () -> account(AccountType.SAVINGS, 7, 0, NUMBER, AccountStatus.ACTIVE));
        assertThrows(IllegalArgumentException.class, () -> savings(Money.parse("-0.01"), BigDecimal.ZERO, Money.ZERO, DATE));
        assertThrows(IllegalArgumentException.class, () -> savings(Money.ZERO, BigDecimal.ZERO, Money.parse("-0.01"), DATE));
    }

    @Test void ratePrecisionAndDateBoundsAreNotCoerced() {
        for (String rate : new String[]{"-0.01", "100.01", "1.001"})
            assertThrows(IllegalArgumentException.class, () -> savings(Money.ZERO, new BigDecimal(rate), Money.ZERO, DATE));
        for (LocalDate date : new LocalDate[]{LocalDate.of(999, 12, 31), LocalDate.of(10000, 1, 1)})
            assertThrows(IllegalArgumentException.class, () -> savings(Money.ZERO, BigDecimal.ZERO, Money.ZERO, date));
        assertNotNull(savings(Money.ZERO, new BigDecimal("100.00"), Money.ZERO, LocalDate.of(1000, 1, 1)));
    }

    @Test void onlySavingsAndCurrentCanBeOpened() {
        AccountValidator.openingType(AccountType.SAVINGS);
        AccountValidator.openingType(AccountType.CURRENT);
        assertThrows(ValidationException.class, () -> AccountValidator.openingType(null));
        assertThrows(ValidationException.class, () -> AccountValidator.openingType(AccountType.DEPOSIT));
        assertThrows(ValidationException.class, () -> AccountValidator.openingType(AccountType.LOAN));
        assertThrows(ValidationException.class, () -> AccountValidator.accountId(0));
    }

    private Account savings(Money balance, BigDecimal rate, Money minimum, LocalDate opened) {
        return new SavingsAccount(7, 1, NUMBER, balance, rate, minimum, opened, AccountStatus.ACTIVE);
    }
}

