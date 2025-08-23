package com.syntex.islamicstudio.media;

import java.io.File;
import java.sql.Connection;
import java.util.List;
import java.util.StringJoiner;

import com.syntex.islamicstudio.db.DatabaseManager;
import com.syntex.islamicstudio.media.quran.QuranAlignmentUtils;
import com.syntex.islamicstudio.media.quran.model.Ayah;
import com.syntex.islamicstudio.media.quran.model.AyahTranscript;
import com.syntex.islamicstudio.media.quran.model.SurahMatch;
import com.syntex.islamicstudio.media.quran.model.Word;
import com.syntex.islamicstudio.media.quran.model.WordMapping;

/**
 * Generates subtitles for a recitation using flexible DP alignment against the
 * Qur'an DB. Supports both word-level and ayah-level subtitles. RAW line now
 * shows only the current word being recited.
 */
public class RecitationCaptionerSTT {

    public enum SubtitleMode { WORD, AYA }

    private final File audioFile;
    private final SubtitleWriter subtitleWriter;
    private final SubtitleMode mode;

    public RecitationCaptionerSTT(File audioFile, String subtitlePath, SubtitleMode mode) throws Exception {
        this.audioFile = audioFile;
        this.subtitleWriter = new SubtitleWriter(subtitlePath);
        this.mode = mode;
    }

    public void start() throws Exception {
        WhisperTranscriber whisper = new WhisperTranscriber();
        List<Word> words = whisper.transcribeWithTimestamps(audioFile);
        if (words.isEmpty()) throw new IllegalStateException("No transcription produced!");

        SurahMatch match;
        try (Connection conn = DatabaseManager.getConnection()) {
            match = QuranAlignmentUtils.detectSurahSegment(conn, words);
        }

        List<Ayah> ayat;
        try (Connection conn = DatabaseManager.getConnection()) {
            ayat = QuranAlignmentUtils.loadSurah(conn, match.surahId, match.startAyah,
                    String.join(" ", words.stream().map(w -> w.text).toList()));
        }

        List<WordMapping> mappings = QuranAlignmentUtils.alignTranscriptFlexible(words, ayat);
        List<AyahTranscript> transcripts = QuranAlignmentUtils.buildAyahTranscripts(mappings);

        if (mode == SubtitleMode.WORD) {
            writeWordLevelSubs(mappings);
        } else {
            writeAyahLevelSubs(transcripts, ayat);
        }

        subtitleWriter.close();
        System.out.println("✅ Subtitles written in " + mode + " mode.");
    }

    private void writeWordLevelSubs(List<WordMapping> mappings) {
        for (WordMapping wm : mappings) {
            Word w = wm.whisper;
            subtitleWriter.addWordSubtitle(w.start, w.end, w.text, w.text, w.text);
        }
    }

    private void writeAyahLevelSubs(List<AyahTranscript> transcripts, List<Ayah> ayat) {
        for (AyahTranscript at : transcripts) {
            Ayah ayah = ayat.stream().filter(a -> a.number == at.ayahNumber).findFirst().orElse(null);
            if (ayah == null) continue;

            double start = at.start;
            double end = at.end;
            String arabic = ayah.arabic;
            String translation = ayah.translation;

            StringJoiner footnotes = new StringJoiner("  ");
            if (ayah.footnotes != null) for (String fn : ayah.footnotes) footnotes.add(fn);

            String currentWord = at.words.isEmpty() ? "" : at.words.get(at.words.size() / 2).text;

            subtitleWriter.addSubtitle(
                    start, end,
                    arabic,
                    translation + (footnotes.length() > 0 ? "\n" + footnotes : ""),
                    currentWord
            );
        }
    }
}