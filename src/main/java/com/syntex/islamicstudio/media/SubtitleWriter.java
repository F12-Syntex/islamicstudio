package com.syntex.islamicstudio.media;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicInteger;

public class SubtitleWriter {

    private final PrintWriter out;
    private final AtomicInteger counter = new AtomicInteger();

    public SubtitleWriter(String path) throws Exception {
        this.out = new PrintWriter(new FileWriter(path, false), true);
    }

    /**
     * Ayah-level subtitles
     */
    public void addSubtitle(double startSec, double endSec,
            String arabic, String translation, String currentWord) {
        int idx = counter.incrementAndGet();
        out.println(idx);
        out.printf("%02d:%02d:%02d,%03d --> %02d:%02d:%02d,%03d%n",
                (int) (startSec / 3600), (int) ((startSec % 3600) / 60), (int) (startSec % 60), (int) ((startSec * 1000) % 1000),
                (int) (endSec / 3600), (int) ((endSec % 3600) / 60), (int) (endSec % 60), (int) ((endSec * 1000) % 1000));
        out.println(arabic);
        out.println(translation);
        out.println("[RAW] " + currentWord);   // ✅ just the current word
        out.println();
    }

    /**
     * Word-level subtitles
     */
    public void addWordSubtitle(double startSec, double endSec,
            String word, String currentWord, String rawWord) {
        int idx = counter.incrementAndGet();
        out.println(idx);
        out.printf("%02d:%02d:%02d,%03d --> %02d:%02d:%02d,%03d%n",
                (int) (startSec / 3600), (int) ((startSec % 3600) / 60), (int) (startSec % 60), (int) ((startSec * 1000) % 1000),
                (int) (endSec / 3600), (int) ((endSec % 3600) / 60), (int) (endSec % 60), (int) ((endSec * 1000) % 1000));

        // 👇 Only show the current word once
        out.println(word);
        out.println();
    }

    public void close() {
        out.close();
    }
}
