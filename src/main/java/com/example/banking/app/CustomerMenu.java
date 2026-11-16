package com.example.banking.app;

import com.example.banking.model.Customer;
import com.example.banking.model.ContactDetails;
import com.example.banking.exception.ValidationException;
import java.util.Arrays;
import com.example.banking.service.AuthService;

/** Authenticated profile, password and owned-account routing. */
public final class CustomerMenu {
    private final AuthService auth;
    private final ConsoleInput input;

    public CustomerMenu(AuthService auth, ConsoleInput input) {
        this.auth = auth;
        this.input = input;
    }

    public void run() {
        while (true) {
            input.println("\nCustomer menu\n1. View my profile\n2. Log out\n3. Edit contact details\n4. Change password\n5. My accounts");
            String choice = input.text("Choice: ", 10);
            // Blocked input may have crossed the idle boundary: check now,
            // BEFORE refresh, profile access, or logout handling.
            auth.currentCustomer();
            if (choice == null || choice.equals("2")) {
                auth.logout();
                input.println("Logged out.");
                return;
            }
            if (choice.equals("1")) {
                Customer customer = auth.currentProfile();
                input.println("Customer code: " + customer.customerCode());
                input.println("Name: " + customer.firstName() + " " + customer.lastName());
                input.println("Username: " + customer.username());
                input.println("Email: " + customer.email());
                input.println("Phone: " + customer.phone());
                input.println("Address: " + (customer.address() == null ? "(not set)" : customer.address()));
            } else if (choice.equals("3")) {
                try {
                    editContact();
                } catch (ConsoleInput.Cancelled cancelled) {
                    input.println("Edit cancelled. No changes saved.");
                } catch (ValidationException invalid) {
                    input.println(invalid.getMessage());
                }
            } else if (choice.equals("4")) {
                try {
                    changePassword();
                } catch (ConsoleInput.Cancelled cancelled) {
                    input.println("Password change cancelled. No password was changed.");
                } catch (ValidationException invalid) {
                    input.println(invalid.getMessage());
                }
            } else if (choice.equals("5")) {
                new AccountMenu(auth, input).run();
            } else {
                input.println("Choose 1, 2, 3, 4 or 5.");
            }
        }
    }

    private void changePassword() {
        String ready = input.field("Press Enter to begin password change", 10);
        if (!ready.isEmpty()) throw new ValidationException("Press Enter to continue or /cancel to return.");
        char[] current = null, replacement = null, confirmation = null;
        try {
            current = input.password("Current password (hidden): ");
            replacement = input.password("New password (hidden; minimum 10 characters, all four classes): ");
            confirmation = input.password("Confirm new password (hidden): ");
            auth.changePassword(current, replacement, confirmation);
            input.println("Password changed. Your session remains active. Keep the new password private.");
        } finally {
            if (current != null) Arrays.fill(current, '\0');
            if (replacement != null) Arrays.fill(replacement, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }

    private void editContact() {
        Customer current = auth.currentProfile();
        input.println("Edit email, phone and address only. Press Enter to keep the shown value.");
        String email = input.field("Email [" + current.email() + "]", 254);
        String phone = input.field("Phone [" + current.phone() + "] (10 digits)", 30);
        String address = input.field("Address [" + (current.address() == null ? "not set" : current.address())
                + "] (/clear removes it)", 510);
        ContactDetails details = new ContactDetails(
                email.isEmpty() ? current.email() : email,
                phone.isEmpty() ? current.phone() : phone,
                address.isEmpty() ? current.address() : address.equals("/clear") ? null : address);
        auth.updateContact(details);
        input.println("Contact details saved. Choose 1 to view your updated profile.");
    }
}
