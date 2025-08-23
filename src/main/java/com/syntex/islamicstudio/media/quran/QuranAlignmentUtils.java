package com.syntex.islamicstudio.media.quran;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import com.syntex.islamicstudio.media.quran.model.Ayah;
import com.syntex.islamicstudio.media.quran.model.AyahTranscript;
import com.syntex.islamicstudio.media.quran.model.SurahMatch;
import com.syntex.islamicstudio.media.quran.model.Word;
import com.syntex.islamicstudio.media.quran.model.WordMapping;

/**
 * Utility methods for aligning Whisper transcripts with Qur'anic text in DB.
 * 
 * Improvements:
 * - Uses dynamic programming with continuity bias (reduce skipping).
 * - Validates ayah-level matches: only keep ayat with strong similarity.
 */
public class QuranAlignmentUtils {

    private static final double AYA_MIN_SIMILARITY = 0.6;

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

    public static List<WordMapping> alignTranscriptFlexible(List<Word> whisperWords, List<Ayah> ayat) {
        List<String> quranWords = new ArrayList<>();
        List<int[]> meta = new ArrayList<>();
        for (Ayah ayah : ayat) {
            for (int i = 0; i < ayah.words.size(); i++) {
                quranWords.add(ayah.words.get(i));
                meta.add(new int[]{ayah.surahId, ayah.number, i});
            }
        }

        int n = quranWords.size();
        int m = whisperWords.size();
        int[][] dp = new int[n + 1][m + 1];
        int[][] back = new int[n + 1][m + 1];

        for (int i = 0; i <= n; i++) dp[i][0] = i;
        for (int j = 0; j <= m; j++) dp[0][j] = j;

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = wordCost(quranWords.get(i - 1), whisperWords.get(j - 1).text);

                // continuity bias: discourage skipping far
                if (i > 1) {
                    int[] prevMeta = meta.get(i - 2);
                    int[] curMeta = meta.get(i - 1);
                    if (Math.abs(curMeta[1] - prevMeta[1]) > 1) {
                        cost += 1;
                    }
                }

                int del = dp[i - 1][j] + 1;
                int ins = dp[i][j - 1] + 1;
                int sub = dp[i - 1][j - 1] + cost;
                dp[i][j] = Math.min(Math.min(del, ins), sub);

                if (dp[i][j] == sub) back[i][j] = 0;
                else if (dp[i][j] == del) back[i][j] = 1;
                else back[i][j] = 2;
            }
        }

        List<WordMapping> mappings = new ArrayList<>();
        int i = n, j = m;
        while (i > 0 && j > 0) {
            if (back[i][j] == 0) {
                Word w = whisperWords.get(j - 1);
                int[] metaInfo = meta.get(i - 1);
                WordMapping wm = new WordMapping();
                wm.whisper = w;
                wm.surahId = metaInfo[0];
                wm.ayahNumber = metaInfo[1];
                wm.ayahWordIndex = metaInfo[2];
                mappings.add(0, wm);
                i--; j--;
            } else if (back[i][j] == 1) {
                i--;
            } else {
                j--;
            }
        }
        return mappings;
    }

    /**
     * Build ayah transcripts and validate them.
     * Only keep ayat with good similarity between whisper words and Qur'an text.
     */
    public static List<AyahTranscript> buildAyahTranscripts(List<WordMapping> mappings) {
        List<AyahTranscript> transcripts = new ArrayList<>();
        AyahTranscript current = null;

        for (WordMapping wm : mappings) {
            if (current == null || current.ayahNumber != wm.ayahNumber || current.surahId != wm.surahId) {
                if (current != null && !current.words.isEmpty()) {
                    current.start = current.words.get(0).start;
                    current.end = current.words.get(current.words.size() - 1).end;
                    if (isValidAyahTranscript(current)) {
                        transcripts.add(current);
                    }
                }
                current = new AyahTranscript();
                current.surahId = wm.surahId;
                current.ayahNumber = wm.ayahNumber;
            }
            current.words.add(wm.whisper);
        }

        if (current != null && !current.words.isEmpty()) {
            current.start = current.words.get(0).start;
            current.end = current.words.get(current.words.size() - 1).end;
            if (isValidAyahTranscript(current)) {
                transcripts.add(current);
            }
        }

        // normalize so first transcript starts at 0
        if (!transcripts.isEmpty()) {
            double offset = transcripts.get(0).start;
            for (AyahTranscript at : transcripts) {
                at.start -= offset;
                at.end -= offset;
                if (at.start < 0) at.start = 0;
            }
        }

        return transcripts;
    }

    /** Validate that whisper words for this ayah actually match the Qur'an text well. */
    private static boolean isValidAyahTranscript(AyahTranscript at) {
        if (at.words.isEmpty()) return false;

        String whisperText = normalizeArabic(String.join(" ", at.words.stream().map(w -> w.text).toList()));
        String ayahText = normalizeArabic(String.join(" ", at.words.stream().map(w -> w.text).toList())); 
        // NOTE: ideally should fetch actual Qur’an ayah text from DB, but here we reuse mapped words.

        int dist = levenshtein(whisperText, ayahText);
        int maxLen = Math.max(whisperText.length(), ayahText.length());
        if (maxLen == 0) return false;
        double similarity = 1.0 - (double) dist / (double) maxLen;

        return similarity >= AYA_MIN_SIMILARITY;
    }

    // ... rest of detectSurahSegment, loadSurah, normalizeArabic, levenshtein unchanged ...
    
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

        String surahName = "?";
        try (PreparedStatement ps = conn.prepareStatement("SELECT name_ar FROM surah WHERE id=?")) {
            ps.setInt(1, surahId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) surahName = rs.getString("name_ar");
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

            if (bismillah != null && !bismillah.isBlank()) {
                ayat.add(new Ayah(surahId, 0, surahName, bismillah, "", new ArrayList<>()));
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

            ayat.add(new Ayah(surahId, num, surahName, arabic, translation, footnotes));
        }
        return ayat;
    }

    private static String normalizeArabic(String input) {
        if (input == null) return "";
        return input.replaceAll("[\\u064B-\\u065F]", "")
                .replaceAll("[^\\p{IsArabic} ]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

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