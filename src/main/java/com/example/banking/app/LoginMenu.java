package com.example.banking.app;

import com.example.banking.service.AuthService;

/** Collects credentials; authentication, locking and audit stay in the service. */
public final class LoginMenu {
    private final AuthService auth;
    private final ConsoleInput input;

    public LoginMenu(AuthService auth, ConsoleInput input) {
        this.auth = auth;
        this.input = input;
    }

    public void run() {
        String username = input.field("Username", 512);
        char[] raw = input.password("Password (hidden): ");
        auth.login(username, raw); // service clears the caller's password buffer on every path
        input.println("Login successful.");
        new CustomerMenu(auth, input).run();
    }
}
