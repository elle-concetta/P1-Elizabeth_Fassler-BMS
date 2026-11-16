package com.example.banking.model;

import java.time.LocalDate;

/** Public profile only. Authentication hashes never belong in this object. */
public record Customer(long customerId, String customerCode, String firstName,
                       String middleInitial, String lastName, LocalDate dateOfBirth,
                       String address, String email, String phone, String username,
                       CustomerStatus status) {
    public Customer {
        if (customerId <= 0 || status == null) {
            throw new IllegalArgumentException("A saved customer identity is required.");
        }
    }

    @Override
    public String toString() {
        return "Customer[id=" + customerId + ", status=" + status + "]";
    }
}

