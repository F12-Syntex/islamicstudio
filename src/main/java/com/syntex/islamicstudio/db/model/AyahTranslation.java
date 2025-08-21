// AyahTranslation.java
package com.syntex.islamicstudio.db.model;

public class AyahTranslation {
    private int id;
    private int ayahId;
    private int sourceId;
    private String translation;

    public AyahTranslation(int id, int ayahId, int sourceId, String translation) {
        this.id = id;
        this.ayahId = ayahId;
        this.sourceId = sourceId;
        this.translation = translation;
    }
}