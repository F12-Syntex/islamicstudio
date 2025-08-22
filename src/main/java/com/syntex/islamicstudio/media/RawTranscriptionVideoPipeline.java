package com.syntex.islamicstudio.media;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.syntex.islamicstudio.db.DatabaseManager;

/**
 * Pipeline: Audio -> Whisper (cached) -> Detect surah + start ayah (partial match)
 * -> Align transcript -> Show current ayah (translation + footnotes) with highlights (rewindable).
 */
public class RawTranscriptionVideoPipeline {

    /** Word from Whisper */
    static class Word {
        public double start;
        public double end;
        public String text;
    }

    /** Ayah data */
    static class Ayah {
        public int number;
        public int surahId;
        public String arabic;
        public String translation;
        public List<String> words;
        public List<String> footnotes;

        public Ayah(int surahId, int number, String arabic, String translation, List<String> footnotes) {
            this.surahId = surahId;
            this.number = number;
            this.arabic = arabic;
            this.translation = translation;
            this.footnotes = footnotes;
            this.words = Arrays.asList(arabic.split("\\s+"));
        }
    }

    /** Transcript per ayah with alignment */
    static class AyahTranscript {
        public int surahId;
        public int ayahNumber;
        public double start;
        public double end;
        public List<Word> words = new ArrayList<>();
        public int[] alignment; // whisperWord[j] -> ayahWord index
    }

    /** Mapping Whisper → Qur’an word (global) */
    static class WordMapping {
        Word whisper;
        int surahId;
        int ayahNumber;
        int ayahWordIndex;
    }

    /** Surah detection result */
    private static class SurahMatch {
        int surahId;
        int startAyah;
        double score;
    }

    private static final Gson gson = new Gson();

    public static void main(String[] args) throws Exception {
        File audioFile = new File("src/main/resources/recitations/haqqah.mp3");
        File outputVideo = new File("output/transcribed_video.mp4");

        File transcriptFile = new File("output/transcripts", audioFile.getName() + ".json");
        transcriptFile.getParentFile().mkdirs();

        List<AyahTranscript> transcripts;
        List<Word> rawWords;
        SurahMatch match;
        String transcriptText;

        if (transcriptFile.exists()) {
            System.out.println("📖 Loading cached transcript: " + transcriptFile);
            try (FileReader reader = new FileReader(transcriptFile)) {
                transcripts = gson.fromJson(reader, new TypeToken<List<AyahTranscript>>() {}.getType());
            }
            rawWords = new ArrayList<>();
            for (AyahTranscript at : transcripts) rawWords.addAll(at.words);
            transcriptText = String.join(" ", rawWords.stream().map(w -> w.text).toList());

            try (Connection conn = DatabaseManager.getConnection()) {
                match = detectSurahSegment(conn, rawWords);
            }
        } else {
            System.out.println("🎙️ Running Whisper transcription...");
            WhisperTranscriber whisper = new WhisperTranscriber();
            rawWords = whisper.transcribeWithTimestamps(audioFile);
            if (rawWords.isEmpty()) {
                System.err.println("⚠️ No transcription produced!");
                return;
            }
            transcriptText = String.join(" ", rawWords.stream().map(w -> w.text).toList());

            try (Connection conn = DatabaseManager.getConnection()) {
                match = detectSurahSegment(conn, rawWords);
                List<Ayah> ayat = loadSurah(conn, match.surahId, match.startAyah, transcriptText);
                transcripts = alignTranscriptToSurah(rawWords, ayat);
            }

            try (FileWriter writer = new FileWriter(transcriptFile)) {
                gson.toJson(transcripts, writer);
            }
            System.out.println("💾 Saved transcript to: " + transcriptFile);
        }

        System.out.printf("✅ Surah %d detected, starting from ayah %d%n", match.surahId, match.startAyah);

        // Load ayahs for rendering
        List<Ayah> surahAyat;
        try (Connection conn = DatabaseManager.getConnection()) {
            surahAyat = loadSurah(conn, match.surahId, match.startAyah, transcriptText);
        }

        // 🔄 Build global word mappings
        List<WordMapping> mappings = buildWordMappings(transcripts);

        // Render frames
        File framesDir = new File("output/frames");
        framesDir.mkdirs();
        int fps = 25;
        double duration = rawWords.get(rawWords.size() - 1).end;
        int totalFrames = (int) (duration * fps);

        int width = 1280, height = 720;
        int frameIndex = 0;

        for (int f = 0; f < totalFrames; f++) {
            double t = f / (double) fps;

            WordMapping active = null;
            for (WordMapping wm : mappings) {
                if (t >= wm.whisper.start && t <= wm.whisper.end) {
                    active = wm;
                    break;
                }
            }

            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();

            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g.setColor(Color.BLACK);
            g.fillRect(0, 0, width, height);

            if (active != null) {
                Ayah ayah = findAyah(surahAyat, active.ayahNumber);

                // Arabic with highlight
                g.setFont(new Font("SansSerif", Font.BOLD, 44));
                drawAyahWithHighlight(g, ayah.arabic, active.ayahWordIndex, width, height / 2);

                // Translation
                g.setColor(Color.LIGHT_GRAY);
                g.setFont(new Font("Serif", Font.PLAIN, 28));
                drawCentered(g, ayah.translation, width, height / 2 + 80);

                // Footnotes
                if (ayah.footnotes != null && !ayah.footnotes.isEmpty()) {
                    g.setColor(Color.GRAY);
                    g.setFont(new Font("Serif", Font.ITALIC, 18));
                    int margin = 40;
                    int boxHeight = 180;
                    int yStart = height - margin - boxHeight;
                    String combined = String.join("  ", ayah.footnotes);
                    drawWrappedTextLTR(g, combined, margin, yStart, width - 2 * margin, boxHeight);
                }
            }

            g.dispose();
            File out = new File(framesDir, String.format("frame%05d.png", frameIndex++));
            ImageIO.write(img, "png", out);
        }

        // FFmpeg combine
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-threads", "4",
                "-framerate", String.valueOf(fps),
                "-i", "output/frames/frame%05d.png",
                "-i", audioFile.getAbsolutePath(),
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                "-shortest",
                outputVideo.getAbsolutePath()
        );
        pb.inheritIO();
        int exit = pb.start().waitFor();

        if (exit == 0) {
            System.out.println("✅ Video generated: " + outputVideo.getAbsolutePath());
        } else {
            System.err.println("⚠️ ffmpeg failed with exit code " + exit);
        }
    }

    // ===================== Rendering Helpers ======================

    private static void drawCentered(Graphics2D g, String text, int width, int y) {
        if (text == null || text.isBlank()) return;
        FontMetrics fm = g.getFontMetrics();
        int x = (width - fm.stringWidth(text)) / 2;
        g.drawString(text, x, y);
    }

    private static void drawAyahWithHighlight(Graphics2D g, String ayahText, int activeIndex, int width, int y) {
        if (ayahText == null || ayahText.isBlank()) return;
        FontMetrics fm = g.getFontMetrics();
        String[] words = ayahText.split("\\s+");
        List<String> reversed = new ArrayList<>(Arrays.asList(words));
        java.util.Collections.reverse(reversed);
        int totalWidth = 0;
        for (String w : reversed) totalWidth += fm.stringWidth(w + " ");
        int x = (width - totalWidth) / 2;
        for (int i = 0; i < reversed.size(); i++) {
            int originalIndex = words.length - 1 - i;
            if (originalIndex == activeIndex) g.setColor(Color.RED);
            else g.setColor(Color.WHITE);
            g.drawString(reversed.get(i), x, y);
            x += fm.stringWidth(reversed.get(i) + " ");
        }
    }

    private static void drawWrappedTextLTR(Graphics2D g, String text, int x, int y, int maxWidth, int maxHeight) {
        AttributedString attrStr = new AttributedString(text);
        attrStr.addAttribute(TextAttribute.FONT, g.getFont());
        AttributedCharacterIterator it = attrStr.getIterator();
        LineBreakMeasurer lbm = new LineBreakMeasurer(it, g.getFontRenderContext());
        float wrapWidth = maxWidth;
        int usedHeight = 0;
        while (lbm.getPosition() < it.getEndIndex() && usedHeight < maxHeight) {
            var layout = lbm.nextLayout(wrapWidth);
            y += layout.getAscent();
            layout.draw(g, x, y);
            y += layout.getDescent() + layout.getLeading();
            usedHeight += layout.getAscent() + layout.getDescent() + layout.getLeading();
        }
        if (lbm.getPosition() < it.getEndIndex()) g.drawString("...", x, y + 20);
    }

    // ===================== Alignment ======================

    /** Fuzzy word match cost (0 = match, 1 = mismatch) */
    private static int wordCost(String quranWord, String whisperWord) {
        String a = normalizeArabic(quranWord);
        String b = normalizeArabic(whisperWord);
        if (a.isEmpty() || b.isEmpty()) return 1;
        int dist = levenshtein(a, b);
        int maxLen = Math.max(a.length(), b.length());
        double sim = 1.0 - (double) dist / (double) maxLen;
        return (sim >= 0.7) ? 0 : 1;
    }

    private static int[] alignWords(List<String> ayahWords, List<Word> whisperWords) {
        int n = ayahWords.size(), m = whisperWords.size();
        int[][] dp = new int[n + 1][m + 1];
        int[][] back = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) dp[i][0] = i;
        for (int j = 0; j <= m; j++) dp[0][j] = j;

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = wordCost(ayahWords.get(i - 1), whisperWords.get(j - 1).text);
                int del = dp[i - 1][j] + 1;
                int ins = dp[i][j - 1] + 1;
                int sub = dp[i - 1][j - 1] + cost;
                dp[i][j] = Math.min(Math.min(del, ins), sub);
                if (dp[i][j] == sub) back[i][j] = 0;
                else if (dp[i][j] == del) back[i][j] = 1;
                else back[i][j] = 2;
            }
        }

        int[] map = new int[m];
        Arrays.fill(map, -1);
        int i = n, j = m;
        while (i > 0 && j > 0) {
            if (back[i][j] == 0) {
                if (dp[i][j] == dp[i - 1][j - 1]) map[j - 1] = i - 1;
                i--; j--;
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

    private static List<AyahTranscript> alignTranscriptToSurah(List<Word> words, List<Ayah> ayat) {
        List<AyahTranscript> transcripts = new ArrayList<>();
        int wordIndex = 0;
        for (Ayah ayah : ayat) {
            if (wordIndex >= words.size()) break;
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

    /** Build global mapping for rewindable highlights */
    private static List<WordMapping> buildWordMappings(List<AyahTranscript> transcripts) {
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

    // ===================== DB ======================

    private static Ayah findAyah(List<Ayah> ayat, int number) {
        for (Ayah a : ayat) if (a.number == number) return a;
        return null;
    }

    /** Detect surah + start ayah using sliding-window fuzzy match */
    private static SurahMatch detectSurahSegment(Connection conn, List<Word> words) throws Exception {
        String transcript = String.join(" ", words.stream().map(w -> w.text).toList());
        String normTranscript = normalizeArabic(transcript);

        SurahMatch best = new SurahMatch();
        best.score = -1.0;

        PreparedStatement psSurah = conn.prepareStatement("SELECT id FROM surah");
        ResultSet rsSurah = psSurah.executeQuery();

        while (rsSurah.next()) {
            int surahId = rsSurah.getInt("id");

            PreparedStatement ps = conn.prepareStatement(
                "SELECT a.ayah_number, t.text " +
                "FROM ayah a " +
                "JOIN ayah_text t ON t.ayah_id=a.id AND t.source_id=1 " +
                "WHERE a.surah_id=? ORDER BY a.ayah_number");
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

        System.out.printf("🔍 Best match: Surah %d starting at ayah %d (score=%.3f)%n",
                          best.surahId, best.startAyah, best.score);
        return best;
    }

    private static List<Ayah> loadSurah(Connection conn, int surahId, int startAyah, String transcript) throws Exception {
        List<Ayah> ayat = new ArrayList<>();
        boolean transcriptHasBismillah = transcript.contains("بسم الله");

        PreparedStatement ps = conn.prepareStatement(
            "SELECT a.id, a.ayah_number, a.surah_id, t.text, t.bismillah, tr.translation " +
            "FROM ayah a " +
            "JOIN ayah_text t ON t.ayah_id=a.id AND t.source_id=1 " +
            "JOIN ayah_translation tr ON tr.ayah_id=a.id AND tr.source_id=1 " +
            "WHERE a.surah_id=? AND a.ayah_number>=? ORDER BY a.ayah_number");
        ps.setInt(1, surahId);
        ps.setInt(2, startAyah);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            int ayahId = rs.getInt("id");
            int num = rs.getInt("ayah_number");
            String arabic = rs.getString("text");
            String bismillah = rs.getString("bismillah");
            String translation = rs.getString("translation");

            if (num == 1 && bismillah != null && !bismillah.isBlank() && transcriptHasBismillah) {
                ayat.add(new Ayah(surahId, 0, bismillah,
                        "(In the Name of Allah, the Entirely Merciful, the Especially Merciful)", List.of()));
            }

            List<String> footnotes = new ArrayList<>();
            PreparedStatement psFoot = conn.prepareStatement(
                "SELECT marker, content FROM translation_footnote " +
                "WHERE ayah_translation_id IN " +
                "(SELECT id FROM ayah_translation WHERE ayah_id=? AND source_id=1)");
            psFoot.setInt(1, ayahId);
            ResultSet rsFoot = psFoot.executeQuery();
            while (rsFoot.next()) {
                footnotes.add("[" + rsFoot.getString("marker") + "] " + rsFoot.getString("content"));
            }

            ayat.add(new Ayah(surahId, num, arabic, translation, footnotes));
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