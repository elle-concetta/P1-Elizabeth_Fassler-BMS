package com.example.banking.validation;

import com.example.banking.exception.ValidationException;
import com.example.banking.model.AccountType;

/** Only ordinary, zero-funded accounts can be opened in this increment. */
public final class AccountValidator {
    private AccountValidator() {}

    public static void openingType(AccountType type) {
        if (type != AccountType.SAVINGS && type != AccountType.CURRENT)
            throw new ValidationException("Choose Savings or Current. Deposit and Loan opening are not available.");
    }

    public static void accountId(long id) {
        if (id <= 0) throw new ValidationException("Choose a positive account ID.");
    }
}
