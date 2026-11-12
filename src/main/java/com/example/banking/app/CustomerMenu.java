package com.example.banking.app;

import com.example.banking.model.Customer;
import com.example.banking.service.AuthService;

/** Batch-one authenticated shell. Account operations join this menu in batch two. */
public final class CustomerMenu {
    private final AuthService auth;
    private final ConsoleInput input;

    public CustomerMenu(AuthService auth, ConsoleInput input) {
        this.auth = auth;
        this.input = input;
    }

    public void run() {
        while (true) {
            input.println("\nCustomer menu\n1. View my profile\n2. Log out");
            String choice = input.text("Choice: ", 10);
            // Blocked input may have crossed the idle boundary: check now,
            // BEFORE refresh, profile access, or logout handling.
            Customer customer = auth.currentCustomer();
            if (choice == null || choice.equals("2")) {
                auth.logout();
                input.println("Logged out.");
                return;
            }
            if (choice.equals("1")) {
                input.println("Customer code: " + customer.customerCode());
                input.println("Name: " + customer.firstName() + " " + customer.lastName());
                input.println("Username: " + customer.username());
                input.println("Email: " + customer.email());
            } else {
                input.println("Choose 1 or 2.");
            }
        }
    }
}
