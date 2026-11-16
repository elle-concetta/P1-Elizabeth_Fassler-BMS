package com.example.banking.app;

import com.example.banking.exception.AccountOpeningUnconfirmedException;
import com.example.banking.exception.PersistenceException;
import com.example.banking.exception.ValidationException;
import com.example.banking.exception.FinancialOperationException;
import com.example.banking.model.Account;
import com.example.banking.model.AccountType;
import com.example.banking.service.AuthService;

/** Masks account numbers explicitly; accepts database IDs, never last-four as identity. */
public final class AccountMenu {
    private final AuthService auth;
    private final ConsoleInput input;

    public AccountMenu(AuthService auth, ConsoleInput input) {
        this.auth = auth;
        this.input = input;
    }

    public void run() {
        while (true) {
            auth.currentCustomer();
            input.println("\nMY ACCOUNTS\n1. Open Savings account (zero balance)"
                    + "\n2. Open Current account (zero balance)\n3. List my accounts"
                    + "\n4. View account balance/details\n5. Deposit money\n6. Withdraw money\n0. Back to customer menu");
            try {
                String choice = input.text("Choice: ", 10);
                if (choice == null || choice.equals("0")) return;
                switch (choice) {
                    case "1" -> open(AccountType.SAVINGS);
                    case "2" -> open(AccountType.CURRENT);
                    case "3" -> list();
                    case "4" -> details();
                    case "5" -> money(false);
                    case "6" -> money(true);
                    default -> input.println("Choose 0, 1, 2, 3, 4, 5 or 6.");
                }
            } catch (ConsoleInput.Cancelled cancelled) {
                input.println("Account action cancelled.");
            } catch (FinancialOperationException failure) {
                if (auth.pendingDeposit().isPresent()) throw failure;
                input.println(failure.getMessage());
            } catch (ValidationException | PersistenceException | AccountOpeningUnconfirmedException safe) {
                input.println(safe.getMessage());
            }
        }
    }

    private void open(AccountType type) {
        input.println("Open a " + type + " account with balance 0.00, rate 0.00% and minimum balance 0.00.");
        String confirmation = input.field("Type OPEN to confirm (Enter cancels)", 10);
        if (!confirmation.equals("OPEN")) {
            input.println("Account opening cancelled. No account was opened.");
            return;
        }
        Account account = auth.openAccount(type);
        input.println("Account opened successfully.");
        display(account);
    }

    private void list() {
        var accounts = auth.accounts();
        if (accounts.isEmpty()) {
            input.println("You do not have any accounts yet.");
            return;
        }
        for (Account account : accounts) display(account);
    }

    private void details() {
        display(auth.accountDetails(readAccountId()));
    }

    private long readAccountId() {
        String supplied = input.field("Account ID from your account list", 19);
        long id;
        try {
            if (!supplied.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
            id = Long.parseLong(supplied);
        } catch (NumberFormatException invalid) {
            throw new ValidationException("Choose a positive account ID from your account list.");
        }
        return id;
    }

    private void money(boolean withdrawal) {
        String operation = withdrawal ? "Withdrawal" : "Deposit";
        String confirmationWord = withdrawal ? "WITHDRAW" : "DEPOSIT";
        long id = readAccountId();
        String amount = input.field(operation + " amount (plain decimal, at most two decimal places)", 40);
        String confirmation = input.field("Type " + confirmationWord + " to submit (Enter cancels)", 10);
        if (!confirmation.equals(confirmationWord)) {
            input.println(operation + " cancelled. No money was changed.");
            return;
        }
        var receipt = withdrawal ? auth.withdraw(id, amount) : auth.deposit(id, amount);
        input.println(operation + " complete.");
        input.println("Reference: " + receipt.reference().value());
        input.println("Account ID: " + receipt.accountId() + " | Number: " + receipt.number().masked());
        input.println((withdrawal ? "Withdrawn: " : "Deposited: ") + receipt.amount() + " | Balance before: " + receipt.balanceBefore()
                + " | Balance after: " + receipt.balanceAfter());
    }

    private void display(Account account) {
        input.println("Account ID: " + account.accountId() + " | Number: " + account.number().masked()
                + " | Type: " + account.type() + " | Status: " + account.status());
        input.println(account.balanceLabel() + ": " + account.balance()
                + " | Opened: " + account.openingDate());
        input.println("Annual rate: " + account.interestRate().toPlainString()
                + "% | Minimum balance: " + account.minimumBalance());
    }
}