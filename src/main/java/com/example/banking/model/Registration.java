package com.example.banking.model;

import java.time.LocalDate;

/** Non-secret registration input; raw passwords travel separately as char[]. */
public record Registration(String firstName, String middleInitial, String lastName,
                           LocalDate dateOfBirth, String address, String email,
                           String phone, String username) {
    @Override
    public String toString() {
        return "Registration[REDACTED]";
    }
}
