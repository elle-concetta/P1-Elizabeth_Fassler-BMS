package com.example.banking.integration;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static com.example.banking.integration.LoginAuditTestDatabase.SqlModeProfile.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Database-free checks of the optional read-only mode guard. */
class LoginAuditSqlModeProfileTest {
    private static final String STRICT = "ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,"
            + "NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION";

    @Test
    void matchesExactModeSetsRegardlessOfServerOrdering() {
        assertTrue(DEFAULT_STRICT.matches(STRICT));
        assertTrue(DEFAULT_STRICT.matches("NO_ENGINE_SUBSTITUTION,ERROR_FOR_DIVISION_BY_ZERO,"
                + "NO_ZERO_DATE,NO_ZERO_IN_DATE,STRICT_TRANS_TABLES,ONLY_FULL_GROUP_BY"));
        assertTrue(PERMISSIVE.matches(""));
        assertTrue(TRUNCATE_FRACTIONAL.matches("TIME_TRUNCATE_FRACTIONAL"));
        assertFalse(PERMISSIVE.matches(null));
        assertFalse(PERMISSIVE.matches("NO_ENGINE_SUBSTITUTION"));
        assertFalse(DEFAULT_STRICT.matches("STRICT_TRANS_TABLES"));
        assertFalse(DEFAULT_STRICT.matches(STRICT + ",TIME_TRUNCATE_FRACTIONAL"));
        assertFalse(TRUNCATE_FRACTIONAL.matches(STRICT + ",TIME_TRUNCATE_FRACTIONAL"));
    }

    @Test
    void refusesWrongVersionOrGlobalOrSessionModeUsingOnlyReadOnlySql() throws Exception {
        for (String[] values : new String[][]{
                {"8.4.5", "", ""},
                {"8.0.42", "", ""},
                {"8.4.5", STRICT, ""},
                {"8.4.5", "", STRICT}}) {
            try (Connection connection = mock(Connection.class);
                 Statement statement = mock(Statement.class);
                 ResultSet rows = mock(ResultSet.class)) {
                when(connection.createStatement()).thenReturn(statement);
                when(statement.executeQuery(
                        "SELECT VERSION(), @@global.sql_mode, @@session.sql_mode")).thenReturn(rows);
                when(rows.next()).thenReturn(true);
                when(rows.getString(1)).thenReturn(values[0]);
                when(rows.getString(2)).thenReturn(values[1]);
                when(rows.getString(3)).thenReturn(values[2]);
                if (values[0].equals("8.4.5") && values[1].isEmpty() && values[2].isEmpty()) {
                    assertDoesNotThrow(() -> PERMISSIVE.verify(connection));
                } else {
                    assertThrows(IllegalStateException.class, () -> PERMISSIVE.verify(connection));
                }
                verify(statement).executeQuery(
                        "SELECT VERSION(), @@global.sql_mode, @@session.sql_mode");
                verify(statement).close();
                verifyNoMoreInteractions(statement);
            }
        }
    }
}

