package com.syntex.islamicstudio.media.quran.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class AyahTranscript {
    public int surahId;
    public int ayahNumber;
    public double start;
    public double end;
    public List<Word> words = new ArrayList<>();
    public int[] alignment; // whisperWord[j] -> ayahWord index
}