package com.syntex.islamicstudio.media.quran.model;

import java.util.Arrays;
import java.util.List;

import lombok.Data;

@Data
public class Ayah {

    public int surahId;
    public int number;
    public String surahName; 
    public String arabic;
    public String translation;
    public List<String> footnotes;
    public List<String> words;

    public Ayah(int surahId, int number, String surahName,
            String arabic, String translation, List<String> footnotes) {
        this.surahId = surahId;
        this.number = number;
        this.surahName = surahName;
        this.arabic = arabic;
        this.translation = translation;
        this.footnotes = footnotes;
        this.words = Arrays.asList(arabic.split("\\s+"));
    }
}
