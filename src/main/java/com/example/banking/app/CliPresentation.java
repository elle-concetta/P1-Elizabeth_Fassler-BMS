package com.example.banking.app;

/** Plain ASCII renders consistently in PowerShell, IDE terminals and saved output. */
public final class CliPresentation {
    private static final int WIDTH = 60;

    private CliPresentation() {}

    public static String welcome() {
        String border = "+" + "-".repeat(WIDTH) + "+";
        String[] lines = {
                "", "     /\\     ", "    /  \\    ", "   /____\\   ",
                "   | || |   ", "   |_||_|   ", "",
                "S E C U R E   B A N K I N G", "CLI EDITION", "",
                "REGISTER  |  SIGN IN  |  VIEW YOUR PROFILE", ""
        };
        StringBuilder banner = new StringBuilder(border);
        for (String line : lines) {
            int left = (WIDTH - line.length()) / 2;
            banner.append('\n').append('|')
                    .append(" ".repeat(left)).append(line)
                    .append(" ".repeat(WIDTH - left - line.length())).append('|');
        }
        return banner.append('\n').append(border)
                .append("\nSecure Banking CLI - registration and login")
                .append("\nPasswords are hidden during entry. Use /cancel in text forms to go back.")
                .toString();
    }
}
