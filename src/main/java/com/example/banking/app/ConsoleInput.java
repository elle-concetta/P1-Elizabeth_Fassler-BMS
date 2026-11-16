package com.example.banking.app;

import com.example.banking.exception.ValidationException;

import java.io.*;
import java.util.Objects;
import java.util.function.Supplier;

/** Bounded text prompts. Passwords require a real terminal, never an echoed fallback. */
public final class ConsoleInput implements AutoCloseable {
    private final PushbackReader reader;
    private final PrintWriter output;
    private final Supplier<char[]> passwords;

    /** Injectable streams/password source permit database-free CLI tests. */
    public ConsoleInput(Reader reader, PrintWriter output, Supplier<char[]> passwords) {
        this.reader = new PushbackReader(Objects.requireNonNull(reader), 1);
        this.output = Objects.requireNonNull(output);
        this.passwords = Objects.requireNonNull(passwords);
    }

    public static ConsoleInput standard() {
        Console console = System.console();
        Reader reader = console == null ? new InputStreamReader(System.in) : console.reader();
        PrintWriter output = console == null ? new PrintWriter(System.out, true) : console.writer();
        return new ConsoleInput(reader, output, () -> {
            if (console == null) {
                throw new ValidationException(
                        "Hidden password input needs a terminal. Run the packaged JAR in PowerShell or a terminal, not the IDE Run window.");
            }
            return console.readPassword();
        });
    }

    public String text(String prompt, int maximum) {
        if (maximum <= 0) throw new IllegalArgumentException("Input bound must be positive.");
        output.print(prompt);
        output.flush();
        StringBuilder value = new StringBuilder();
        boolean oversized = false;
        try {
            int next;
            while ((next = reader.read()) != -1) {
                if (next == '\n') break;
                if (next == '\r') {
                    int following = reader.read();
                    if (following != '\n' && following != -1) reader.unread(following);
                    break;
                }
                if (value.length() < maximum) value.append((char) next);
                else oversized = true;
            }
            if (oversized) throw new ValidationException("Input is too long; nothing was submitted.");
            if (next == -1 && value.isEmpty()) return null;
            return value.toString();
        } catch (IOException failure) {
            throw new IllegalStateException("Console input could not be read.");
        }
    }

    public String field(String prompt, int maximum) {
        String value = text(prompt + " (/cancel to return): ", maximum);
        if (value == null || value.equals("/cancel")) throw new Cancelled();
        return value;
    }

    public char[] password(String prompt) {
        output.print(prompt);
        output.flush();
        char[] value = passwords.get();
        if (value == null) throw new Cancelled();
        return value;
    }

    public void println(String text) {
        output.println(text);
        output.flush();
    }

    @Override
    public void close() throws IOException {
        reader.close();
        output.flush();
    }

    /** EOF/cancel is a normal return to the welcome menu, not a stack trace. */
    public static final class Cancelled extends RuntimeException {}
}
