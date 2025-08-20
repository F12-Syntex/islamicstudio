package com.syntex.islamicstudio.env;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Central environment manager for reading .env and system variables.
 */
public class EnvManager {

    private static EnvManager instance;
    private final Dotenv dotenv;

    private EnvManager() {
        dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
    }

    public static EnvManager getInstance() {
        if (instance == null) {
            instance = new EnvManager();
        }
        return instance;
    }

    public String get(EnvKey key) {
        return dotenv.get(key.key());
    }

    public String getOrDefault(EnvKey key, String defaultValue) {
        return dotenv.get(key.key(), defaultValue);
    }

    public int getInt(EnvKey key, int defaultValue) {
        try {
            return Integer.parseInt(getOrDefault(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(EnvKey key, boolean defaultValue) {
        String value = getOrDefault(key, String.valueOf(defaultValue));
        return value.equalsIgnoreCase("true")
                || value.equalsIgnoreCase("1")
                || value.equalsIgnoreCase("yes");
    }

    public void printAll() {
        System.out.println("=== Loaded Environment Variables ===");
        dotenv.entries().forEach(entry
                -> System.out.println(entry.getKey() + "=" + entry.getValue())
        );
    }
}
