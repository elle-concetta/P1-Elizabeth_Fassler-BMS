package com.example.banking.unit;

import com.example.banking.app.ConsoleInput;
import com.example.banking.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/** Stream-only checks for the supplied implementation; no terminal or database. */
class ConsoleInputTest {
    private static ConsoleInput input(String script) {
        return new ConsoleInput(new StringReader(script),
                new PrintWriter(new StringWriter()), () -> new char[0]);
    }

    @Test
    void drainsOversizedInputAndPreservesTheNextWindowsLine() {
        var input = input("x".repeat(10000) + "\r\nnext\r\n");
        assertThrows(ValidationException.class, () -> input.text("Value: ", 10));
        assertEquals("next", input.text("Value: ", 10));
        assertNull(input.text("Value: ", 10));
    }

    @Test
    void acceptsExactBoundAndDistinguishesBlankInputFromEof() {
        var input = input("12345\n\nlast");
        assertEquals("12345", input.text("", 5));
        assertEquals("", input.text("", 5));
        assertEquals("last", input.text("", 5));
        assertNull(input.text("", 5));
    }

    @Test
    void treatsCancelAndEofAsCancelledFields() {
        var input = input("/cancel\n");
        assertThrows(ConsoleInput.Cancelled.class, () -> input.field("Name", 20));
        assertThrows(ConsoleInput.Cancelled.class, () -> input.field("Name", 20));
    }

    @Test
    void neverEchoesTheSuppliedPassword() {
        StringWriter output = new StringWriter();
        char[] password = "Synthetic-Test-Only-1!".toCharArray();
        var input = new ConsoleInput(new StringReader(""), new PrintWriter(output),
                () -> password);
        assertSame(password, input.password("Password: "));
        assertEquals("Password: ", output.toString());
    }

    @Test
    void treatsMissingPasswordAsCancellationAndRejectsInvalidBounds() {
        var input = new ConsoleInput(new StringReader(""),
                new PrintWriter(new StringWriter()), () -> null);
        assertThrows(ConsoleInput.Cancelled.class, () -> input.password("Password: "));
        assertThrows(IllegalArgumentException.class, () -> input.text("", 0));
        assertThrows(IllegalArgumentException.class, () -> input.text("", -1));
    }
}
