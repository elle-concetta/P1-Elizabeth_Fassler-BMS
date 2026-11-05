package com.example.banking.unit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectionMockTest {

    @Mock
    private Connection connection;

    @Test
    void shouldUseMockDatabaseConnection() throws SQLException {
        // Arrange: define what the simulated connection should return.
        when(connection.isValid(2)).thenReturn(true);

        // Act: call the method being tested.
        boolean valid = connection.isValid(2);

        // Assert: check the result and verify the interaction.
        assertTrue(valid);
        verify(connection).isValid(2);
    }
}
