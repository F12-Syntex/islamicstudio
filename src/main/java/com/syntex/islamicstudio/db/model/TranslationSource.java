// TranslationSource.java
package com.syntex.islamicstudio.db.model;

public class TranslationSource {
    private int id;
    private String language;
    private String name;
    private String author;

    public TranslationSource(int id, String language, String name, String author) {
        this.id = id;
        this.language = language;
        this.name = name;
        this.author = author;
    }
}