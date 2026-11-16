package com.example.banking.app;

import com.example.banking.exception.ValidationException;
import com.example.banking.model.*;
import com.example.banking.service.AuthService;

/** Forms only: dedicated principal rules and SQL remain in the services/DAOs. */
public final class SpecialAccountMenu {
    private final AuthService auth;
    private final ConsoleInput input;
    public SpecialAccountMenu(AuthService auth, ConsoleInput input) { this.auth = auth; this.input = input; }

    public void run(boolean loan) {
        while (true) {
            auth.currentCustomer();
            input.println(loan ? "LOANS - balance is debt, never spendable cash.\n1. Create Loan\n2. Repay Loan\n0. Back"
                    : "TERM DEPOSITS - principal locked until maturity, no interest engine.\n1. Open funded Deposit\n2. Settle matured Deposit\n0. Back");
            String choice = input.text("Choice: ", 10);
            if (choice == null || choice.equals("0")) return;
            SpecialReceipt receipt;
            if (choice.equals("1")) {
                long funding = loan ? 0 : id("Owned Savings/Current funding account ID");
                String principal = input.field("Principal (positive plain decimal)", 40);
                int months = months(input.field(loan ? "Positive tenure months" : "Positive term months", 10));
                var start = loan ? HistoryQuery.date(input.field("Loan start date YYYY-MM-DD (Enter uses today)", 10)) : null;
                input.println(loan ? "Creation records outstanding debt only; no cash is paid to another account."
                        : "Funding is debited atomically. No early withdrawal; rate is 0.00% metadata.");
                String expected = loan ? "CREATE" : "OPEN";
                if (!confirm(expected)) { input.println("Creation cancelled. No account or money was changed."); continue; }
                receipt = loan ? auth.openLoan(principal, months, start) : auth.openTermDeposit(funding, principal, months);
            } else if (choice.equals("2")) {
                long first = id(loan ? "Owned Savings/Current funding account ID" : "Owned term Deposit account ID");
                long second = id(loan ? "Owned Loan account ID" : "Owned Savings/Current settlement destination ID");
                String amount = loan ? input.field("Repayment amount", 40) : null;
                if (!confirm(loan ? "REPAY" : "SETTLE")) {
                    input.println("Action cancelled. No money was changed."); continue;
                }
                receipt = loan ? auth.repayLoan(first, second, amount) : auth.settleTermDeposit(first, second);
            } else { input.println("Choose 0, 1 or 2."); continue; }
            input.println(receipt.message());
            input.println("Reference: " + receipt.reference().value());
            Account account = receipt.account();
            input.println("Account ID: " + account.accountId() + " | Number: " + account.number().masked()
                    + " | Type: " + account.type() + " | Status: " + account.status());
            input.println(account.balanceLabel() + ": " + account.balance());
            if (receipt.movement() != null)
                input.println("Movement: " + receipt.movement().type() + " | Amount: " + receipt.movement().amount());
        }
    }
    private boolean confirm(String expected) {
        return input.field("Type " + expected + " to confirm (Enter cancels)", 10).equals(expected);
    }
    private long id(String prompt) {
        try {
            String value = input.field(prompt, 19);
            if (!value.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) { throw new ValidationException("Choose a positive owned account ID."); }
    }
    private static int months(String value) {
        try {
            if (!value.matches("[1-9][0-9]{0,9}")) throw new NumberFormatException();
            int months = Integer.parseInt(value);
            if (months <= 0) throw new NumberFormatException();
            return months;
        } catch (NumberFormatException invalid) { throw new ValidationException("Months must be a positive supported whole number."); }
    }
}

