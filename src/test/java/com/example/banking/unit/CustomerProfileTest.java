package com.example.banking.unit;

import com.example.banking.model.Customer;
import com.example.banking.model.CustomerStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Public-profile checks, not registration validation or persistence coverage. */
class CustomerProfileTest {
    private static Customer customer(long id, CustomerStatus status) {
        return new Customer(id, "C-fixture", "Test", null, "Person",
                LocalDate.of(2000, 1, 1), null, "test@example.invalid", null,
                "tester", status);
    }

    @Test
    void preservesPublicProfileFieldsAndOptionalNulls() {
        Customer customer = customer(7, CustomerStatus.ACTIVE);
        assertAll(
                () -> assertEquals(7, customer.customerId()),
                () -> assertEquals("C-fixture", customer.customerCode()),
                () -> assertEquals("Test", customer.firstName()),
                () -> assertEquals("Person", customer.lastName()),
                () -> assertEquals(LocalDate.of(2000, 1, 1), customer.dateOfBirth()),
                () -> assertEquals("test@example.invalid", customer.email()),
                () -> assertEquals("tester", customer.username()),
                () -> assertNull(customer.middleInitial()),
                () -> assertNull(customer.address()),
                () -> assertNull(customer.phone()));
    }

    @Test
    void rejectsUnsavedIdentityAndMissingStatus() {
        assertThrows(IllegalArgumentException.class, () -> customer(0, CustomerStatus.ACTIVE));
        assertThrows(IllegalArgumentException.class, () -> customer(-1, CustomerStatus.ACTIVE));
        assertThrows(IllegalArgumentException.class, () -> customer(7, null));
    }

    @Test
    void diagnosticRenderingDoesNotIncludeProfileFields() {
        assertEquals("Customer[id=7, status=ACTIVE]",
                customer(7, CustomerStatus.ACTIVE).toString());
    }
}
