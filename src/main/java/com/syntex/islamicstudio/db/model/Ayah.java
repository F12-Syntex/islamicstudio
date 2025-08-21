// Ayah.java
package com.syntex.islamicstudio.db.model;

public class Ayah {
    private int id;
    private int surahId;
    private int ayahNumber;

    public Ayah(int id, int surahId, int ayahNumber) {
        this.id = id;
        this.surahId = surahId;
        this.ayahNumber = ayahNumber;
    }

    public int getId() { return id; }
    public int getSurahId() { return surahId; }
    public int getAyahNumber() { return ayahNumber; }
}