// AyahText.java
package com.syntex.islamicstudio.db.model;

public class AyahText {
    private int id;
    private int ayahId;
    private int sourceId;
    private String text;
    private String bismillah;

    public AyahText(int id, int ayahId, int sourceId, String text, String bismillah) {
        this.id = id;
        this.ayahId = ayahId;
        this.sourceId = sourceId;
        this.text = text;
        this.bismillah = bismillah;
    }
}