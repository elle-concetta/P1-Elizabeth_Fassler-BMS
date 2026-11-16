package com.example.banking.app;

import com.example.banking.exception.ValidationException;
import com.example.banking.model.*;
import com.example.banking.service.AuthService;

/** Protected history enquiry with bounded filters, masked selected account and no counterparties. */
public final class HistoryMenu {
    private final AuthService auth;
    private final ConsoleInput input;

    public HistoryMenu(AuthService auth, ConsoleInput input) {
        this.auth = auth;
        this.input = input;
    }

    public void run(long accountId) {
        input.println("HISTORY FILTERS — dates are UTC; start inclusive, end exclusive. Blank means no filter.");
        var start = HistoryQuery.date(input.field("Start date YYYY-MM-DD (optional)", 10));
        var end = HistoryQuery.date(input.field("End date YYYY-MM-DD (optional, exclusive)", 10));
        var type = HistoryQuery.transactionType(input.field(
                "Type DEPOSIT/WITHDRAWAL/TRANSFER/LOAN_REPAYMENT (optional)", 14));
        int size = number(input.field("Page size 1-50 (Enter uses 20)", 10), 20);
        int page = number(input.field("Starting page, zero-based (Enter uses 0)", 10), 0);
        HistoryQuery query = new HistoryQuery(page, size, start, end, type);
        while (true) {
            HistoryPage result = auth.history(accountId, query);
            input.println("HISTORY | Account ID: " + result.accountId() + " | Number: " + result.number().masked()
                    + " | Page: " + result.query().page() + " | Size: " + result.query().pageSize() + " | Times: UTC");
            if (result.entries().isEmpty()) input.println("No transactions match this page and filters.");
            for (HistoryEntry row : result.entries())
                input.println(row.createdAt() + " | " + row.reference().value() + " | " + row.type()
                        + " | " + row.direction() + " | " + row.amount() + " | " + row.status());
            while (true) {
                String choice = input.text("N. Next page | P. Previous page | 0. Back: ", 10);
                if (choice == null || choice.equals("0")) return;
                if (choice.equals("/cancel")) throw new ConsoleInput.Cancelled();
                if (choice.equalsIgnoreCase("N")) {
                    if (result.hasNext()) { query = query.next(); break; }
                    input.println("No next page for these filters.");
                } else if (choice.equalsIgnoreCase("P")) {
                    if (query.page() > 0) { query = query.atPage(query.page() - 1); break; }
                    input.println("Already on the first page.");
                } else input.println("Choose N, P or 0.");
            }
        }
    }

    private static int number(String value, int fallback) {
        if (value.isEmpty()) return fallback;
        try {
            if (!value.matches("[0-9]{1,10}")) throw new NumberFormatException();
            return Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            throw new ValidationException("Enter a nonnegative whole page/size within the supported range.");
        }
    }
}

