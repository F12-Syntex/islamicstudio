package com.syntex.islamicstudio;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {

    private final Properties props = new Properties();

    public Config() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                props.load(input);
            } else {
                System.err.println("⚠ config.properties not found!");
            }
        } catch (IOException e) {
            System.err.println("⚠ Error loading config.properties: " + e.getMessage());
        }
    }

    public String get(String key) {
        return props.getProperty(key, "");
    }
}
