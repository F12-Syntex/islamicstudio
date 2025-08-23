package com.syntex.islamicstudio.media.quran;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.syntex.islamicstudio.media.quran.model.Ayah;
import com.syntex.islamicstudio.media.quran.model.AyahTranscript;
import com.syntex.islamicstudio.media.quran.model.SurahMatch;
import com.syntex.islamicstudio.media.quran.model.Word;
import com.syntex.islamicstudio.media.quran.model.WordMapping;

/**
 * Utility methods for aligning Whisper transcripts with Qur'anic text in DB.
 */
public class QuranAlignmentUtils {

    /**
     * Fuzzy word match cost (0 = match, 1 = mismatch).
     */
    private static int wordCost(String quranWord, String whisperWord) {
        String a = normalizeArabic(quranWord);
        String b = normalizeArabic(whisperWord);
        if (a.isEmpty() || b.isEmpty()) {
            return 1;
        }
        int dist = levenshtein(a, b);
        int maxLen = Math.max(a.length(), b.length());
        double sim = 1.0 - (double) dist / (double) maxLen;
        return (sim >= 0.7) ? 0 : 1;
    }

    /**
     * Align whisper words to ayah words (dynamic programming).
     */
    public static int[] alignWords(List<String> ayahWords, List<Word> whisperWords) {
        int n = ayahWords.size(), m = whisperWords.size();
        int[][] dp = new int[n + 1][m + 1];
        int[][] back = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = wordCost(ayahWords.get(i - 1), whisperWords.get(j - 1).text);
                int del = dp[i - 1][j] + 1;
                int ins = dp[i][j - 1] + 1;
                int sub = dp[i - 1][j - 1] + cost;
                dp[i][j] = Math.min(Math.min(del, ins), sub);
                if (dp[i][j] == sub) {
                    back[i][j] = 0;
                } else if (dp[i][j] == del) {
                    back[i][j] = 1;
                } else {
                    back[i][j] = 2;
                }
            }
        }

        int[] map = new int[m];
        Arrays.fill(map, -1);
        int i = n, j = m;
        while (i > 0 && j > 0) {
            if (back[i][j] == 0) {
                if (dp[i][j] == dp[i - 1][j - 1]) {
                    map[j - 1] = i - 1;
                }
                i--;
                j--;
            } else if (back[i][j] == 1) {
                i--;
            } else {
                j--;
            }
        }

        // fallback: map unmapped Whisper words to nearest Qur’an word
        for (int w = 0; w < m; w++) {
            if (map[w] == -1 && n > 0) {
                map[w] = Math.min(w, n - 1);
            }
        }
        return map;
    }

    /**
     * Align full transcript to surah ayat.
     */
    public static List<AyahTranscript> alignTranscriptToSurah(List<Word> words, List<Ayah> ayat) {
        List<AyahTranscript> transcripts = new ArrayList<>();
        int wordIndex = 0;
        for (Ayah ayah : ayat) {
            if (wordIndex >= words.size()) {
                break;
            }
            AyahTranscript at = new AyahTranscript();
            at.surahId = ayah.surahId;
            at.ayahNumber = ayah.number;

            List<Word> ayahWords = new ArrayList<>();
            for (int i = 0; i < ayah.words.size() && wordIndex < words.size(); i++) {
                ayahWords.add(words.get(wordIndex++));
            }
            at.words = ayahWords;

            if (!ayahWords.isEmpty()) {
                at.start = ayahWords.get(0).start;
                at.end = ayahWords.get(ayahWords.size() - 1).end;
            }

            at.alignment = alignWords(ayah.words, ayahWords);
            transcripts.add(at);
        }
        return transcripts;
    }

    /**
     * Build global mapping for rewindable highlights.
     */
    public static List<WordMapping> buildWordMappings(List<AyahTranscript> transcripts) {
        List<WordMapping> mappings = new ArrayList<>();
        for (AyahTranscript at : transcripts) {
            for (int j = 0; j < at.words.size(); j++) {
                Word w = at.words.get(j);
                int mappedIndex = (at.alignment != null && j < at.alignment.length) ? at.alignment[j] : -1;
                WordMapping wm = new WordMapping();
                wm.whisper = w;
                wm.surahId = at.surahId;
                wm.ayahNumber = at.ayahNumber;
                wm.ayahWordIndex = mappedIndex;
                mappings.add(wm);
            }
        }
        return mappings;
    }

    /**
     * Detect surah + start ayah using fuzzy sliding-window match.
     */
    public static SurahMatch detectSurahSegment(Connection conn, List<Word> words) throws Exception {
        String transcript = String.join(" ", words.stream().map(w -> w.text).toList());
        String normTranscript = normalizeArabic(transcript);

        SurahMatch best = new SurahMatch();
        best.score = -1.0;

        PreparedStatement psSurah = conn.prepareStatement("SELECT id FROM surah");
        ResultSet rsSurah = psSurah.executeQuery();

        while (rsSurah.next()) {
            int surahId = rsSurah.getInt("id");

            PreparedStatement ps = conn.prepareStatement(
                    "SELECT a.ayah_number, t.text FROM ayah a "
                    + "JOIN ayah_text t ON t.ayah_id=a.id AND t.source_id=1 "
                    + "WHERE a.surah_id=? ORDER BY a.ayah_number");
            ps.setInt(1, surahId);
            ResultSet rs = ps.executeQuery();

            List<String> ayahTexts = new ArrayList<>();
            while (rs.next()) {
                ayahTexts.add(normalizeArabic(rs.getString("text")));
            }

            int windowSize = 15;
            for (int start = 0; start < ayahTexts.size(); start++) {
                int end = Math.min(start + windowSize, ayahTexts.size());
                String chunk = String.join(" ", ayahTexts.subList(start, end));

                int dist = levenshtein(normTranscript, chunk);
                int maxLen = Math.max(normTranscript.length(), chunk.length());
                double similarity = 1.0 - (double) dist / (double) maxLen;

                if (similarity > best.score) {
                    best.surahId = surahId;
                    best.startAyah = start + 1;
                    best.score = similarity;
                }
            }
        }

        return best;
    }

    public static List<Ayah> loadSurah(Connection conn, int surahId, int startAyah, String transcript) throws Exception {
        List<Ayah> ayat = new ArrayList<>();

        // get surah name (Arabic)
        String surahName = "?";
        try (PreparedStatement ps = conn.prepareStatement("SELECT name_ar FROM surah WHERE id=?")) {
            ps.setInt(1, surahId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                surahName = rs.getString("name_ar");
            }
        }

        PreparedStatement ps = conn.prepareStatement(
                "SELECT a.id, a.ayah_number, a.surah_id, t.text, t.bismillah, tr.translation "
                + "FROM ayah a "
                + "JOIN ayah_text t ON t.ayah_id=a.id AND t.source_id=1 "
                + "JOIN ayah_translation tr ON tr.ayah_id=a.id AND tr.source_id=1 "
                + "WHERE a.surah_id=? AND a.ayah_number>=? ORDER BY a.ayah_number");
        ps.setInt(1, surahId);
        ps.setInt(2, startAyah);
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            int ayahId = rs.getInt("id");
            int num = rs.getInt("ayah_number");
            String arabic = rs.getString("text");
            String bismillah = rs.getString("bismillah");
            String translation = rs.getString("translation");

            // ✅ If bismillah is present, treat it as a separate ayah (0)
            if (bismillah != null && !bismillah.isBlank()) {
                ayat.add(new Ayah(
                        surahId,
                        0, // ayah number 0 for bismillah
                        surahName,
                        bismillah,
                        "", // no translation
                        new ArrayList<>() // no footnotes
                ));
            }

            List<String> footnotes = new ArrayList<>();
            PreparedStatement psFoot = conn.prepareStatement(
                    "SELECT marker, content FROM translation_footnote "
                    + "WHERE ayah_translation_id IN "
                    + "(SELECT id FROM ayah_translation WHERE ayah_id=? AND source_id=1)");
            psFoot.setInt(1, ayahId);
            ResultSet rsFoot = psFoot.executeQuery();
            while (rsFoot.next()) {
                footnotes.add("[" + rsFoot.getString("marker") + "] " + rsFoot.getString("content"));
            }

            // Normal ayah
            ayat.add(new Ayah(surahId, num, surahName, arabic, translation, footnotes));
        }

        return ayat;
    }

    // ===== Normalization + Levenshtein ===== //
    private static String normalizeArabic(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("[\\u064B-\\u065F]", "")
                .replaceAll("[^\\p{IsArabic} ]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }
}
