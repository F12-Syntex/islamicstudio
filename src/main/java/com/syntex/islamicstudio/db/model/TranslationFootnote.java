// TranslationFootnote.java
package com.syntex.islamicstudio.db.model;

public class TranslationFootnote {
    private int id;
    private int ayahTranslationId;
    private String marker;
    private String content;

    public TranslationFootnote(int id, int ayahTranslationId, String marker, String content) {
        this.id = id;
        this.ayahTranslationId = ayahTranslationId;
        this.marker = marker;
        this.content = content;
    }
}