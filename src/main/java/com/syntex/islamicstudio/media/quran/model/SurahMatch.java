package com.syntex.islamicstudio.media.quran.model;

import lombok.Data;

@Data
public class SurahMatch {
    public int surahId;
    public int startAyah;
    public double score;
}
