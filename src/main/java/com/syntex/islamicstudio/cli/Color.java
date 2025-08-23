package com.syntex.islamicstudio.cli;

/**
 * Enum of ANSI colors/styles.
 * Single source of truth: no duplication.
 */
public enum Color {
    RESET("\u001B[0m"),
    BLACK("\u001B[30m"),
    RED("\u001B[31m"),
    GREEN("\u001B[32m"),
    YELLOW("\u001B[33m"),
    BLUE("\u001B[34m"),
    PURPLE("\u001B[35m"),
    CYAN("\u001B[36m"),
    WHITE("\u001B[37m"),
    BOLD("\u001B[1m"),
    UNDERLINE("\u001B[4m"),
    LIGHT("\u001B[37;1m"),
    NONE("");

    private final String code;

    Color(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** Reset + text wrapper */
    public String wrap(String text) {
        return code + text + RESET.code;
    }

    /** Dynamic lookup by name (case‑insensitive). */
    public static Color from(String name) {
        if (name == null) return NONE;
        try {
            return Color.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}