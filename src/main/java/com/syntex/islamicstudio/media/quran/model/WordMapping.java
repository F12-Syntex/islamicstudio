package com.syntex.islamicstudio.media.quran.model;

import lombok.Data;

@Data
public class WordMapping {
    public Word whisper;
    public int surahId;
    public int ayahNumber;
    public int ayahWordIndex;
}