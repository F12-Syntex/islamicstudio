package com.syntex.islamicstudio.util;

import java.awt.Font;
import java.io.InputStream;

public class FontLoader {
    public static Font loadFont(String resourcePath, float size) {
        try (InputStream is = FontLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Font not found in resources: " + resourcePath);
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, is);
            return font.deriveFont(size);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load font: " + resourcePath, e);
        }
    }
}