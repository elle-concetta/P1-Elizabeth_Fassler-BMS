package com.example.banking.app;

import com.example.banking.exception.BankingException;
import com.example.banking.exception.ValidationException;
import com.example.banking.model.Registration;
import com.example.banking.service.AuthService;
import com.example.banking.validation.CustomerValidator;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Terminal routing only. SQL, passwords, and transaction rules live elsewhere. */
public final class MainMenu {
    private final AuthService auth;
    private final ConsoleInput input;

    public MainMenu(AuthService auth, ConsoleInput input) {
        this.auth = auth;
        this.input = input;
    }

    public void run() {
        input.println(CliPresentation.welcome());
        while (true) {
            try {
                if (auth.pendingDeposit().isPresent()) {
                    var pending = auth.pendingDeposit().orElseThrow();
                    input.println(pending.evidence().event()
                            + " RECONCILIATION REQUIRED. Authentication is blocked.");
                    input.println("Reference: " + pending.evidence().referenceNumber().orElseThrow().value());
                    input.println("Keep this process open and ask an administrator to reconcile. "
                            + "Evidence is only in memory; exiting loses it. Do not restart or repeat the money operation.");
                    String exit = input.text("Type EXIT to acknowledge evidence loss and close: ", 10);
                    if (exit == null || exit.equals("EXIT")) return;
                    continue;
                }
                if (auth.pendingPasswordChange().isPresent()) {
                    input.println(auth.pendingPasswordChange().orElseThrow().getMessage());
                    input.println("Authentication is blocked pending manual reconciliation. Record the reference.");
                    String exit = input.text("Type EXIT to acknowledge and close: ", 10);
                    if (exit == null || exit.equals("EXIT")) return;
                    continue;
                }
                if (auth.pendingAudit().isPresent()) {
                    input.println("AUDIT STORAGE NOT CONFIRMED. Authentication is blocked.");
                    input.println("Evidence remains only in memory. Exiting loses unsaved evidence; "
                            + "do not restart or repeat the operation to retry it.");
                    String exit = input.text("Type EXIT to acknowledge and close: ", 10);
                    if (exit == null || exit.equals("EXIT")) return;
                    continue;
                }
                input.println("\n  GET STARTED\n  1. Register\n  2. Log in\n  3. Exit");
                String choice = input.text("Choice: ", 10);
                if (choice == null || choice.equals("3")) return;
                switch (choice) {
                    case "1" -> register();
                    case "2" -> new LoginMenu(auth, input).run();
                    default -> input.println("Choose 1, 2 or 3.");
                }
            } catch (ConsoleInput.Cancelled cancelled) {
                input.println("Cancelled. Returned to the welcome menu.");
            } catch (BankingException failure) {
                input.println(failure.getMessage());
                // A form/input error inside the authenticated shell must not
                // strand a live session while returning to the welcome menu.
                if (auth.pendingAudit().isEmpty() && auth.pendingDeposit().isEmpty()) {
                    try {
                        auth.logout();
                    } catch (BankingException auditFailure) {
                        input.println(auditFailure.getMessage());
                    }
                }
            }
        }
    }

    private void register() {
        input.println("Enter registration details. Optional fields may be blank.");
        String first = input.field("First name", 120);
        String middle = input.field("Middle initial (optional)", 2);
        String last = input.field("Last name", 120);
        String birthText = input.field("Birth date YYYY-MM-DD (optional)", 10);
        LocalDate birth = null;
        if (!birthText.isEmpty()) {
            try {
                birth = LocalDate.parse(birthText);
            } catch (DateTimeParseException invalid) {
                throw new ValidationException("Birth date must be a valid YYYY-MM-DD date.");
            }
        }
        String address = input.field("Address (optional)", 510);
        String email = input.field("Email", 254);
        String phone = input.field("Phone (required, 10 digits)", 30);
        CustomerValidator.validatePhone(phone);
        String username = input.field("Username", 80);
        char[] raw = input.password("Password (hidden; minimum 10 characters, all four classes): ");
        auth.register(new Registration(first, middle, last, birth, address, email, phone, username), raw);
        input.println("Registration complete. You can now log in.");
    }
}
