package com.syntex.islamicstudio.env;

public enum EnvKey {
    API_KEY("API_KEY"),
    DEBUG("DEBUG"),
    PORT("PORT"),
    PIXABAY_API_KEY("PIXABAY_API_KEY");

    private final String key;

    EnvKey(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}