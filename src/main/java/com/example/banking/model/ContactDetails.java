package com.example.banking.model;

/** The only profile fields editable in this batch; no identity or security fields. */
public record ContactDetails(String email, String phone, String address) {
    @Override
    public String toString() {
        return "ContactDetails[REDACTED]";
    }
}
