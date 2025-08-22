package com.syntex.islamicstudio.media;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import com.syntex.islamicstudio.db.DatabaseManager;

/**
 * Pipeline: Audio -> Whisper transcription -> Identify surah -> Video with ayah
 * text Karaoke-style highlighting of current word (red).
 */
public class RawTranscriptionVideoPipeline {

    static class Word {
        public double start;
        public double end;
        public String text;
    }

    static class Ayah {
        public int number;
        public String arabic;
        public String translation;
        public List<String> words;

        public Ayah(int number, String arabic, String translation) {
            this.number = number;
            this.arabic = arabic;
            this.translation = translation;
            this.words = Arrays.asList(arabic.split("\\s+"));
        }
    }

    private static class AyahWord {
        Ayah ayah;
        int index;
        String word;
        double start;
        double end;

        AyahWord(Ayah ayah, int index, String word) {
            this.ayah = ayah;
            this.index = index;
            this.word = word;
        }
    }

    public static void main(String[] args) throws Exception {
        File audioFile = new File("src/main/resources/recitations/095.mp3");
        File outputVideo = new File("output/transcribed_video.mp4");

        // 1. Transcribe with Whisper (word-level approximation)
        WhisperTranscriber whisper = new WhisperTranscriber();
        List<Word> words = whisper.transcribeWithTimestamps(audioFile);
        if (words.isEmpty()) {
            System.err.println("⚠️ No transcription produced!");
            return;
        }

        // 2. Identify surah from DB
        int surahId;
        List<Ayah> ayat;
        try (Connection conn = DatabaseManager.getConnection()) {
            surahId = detectSurah(conn, words);
            ayat = loadSurah(conn, surahId);
        }
        System.out.println("✅ Detected surah id = " + surahId + " with " + ayat.size() + " ayat");

        // 3. Build ayah word sequence WITH ayah boundaries respected
        List<AyahWord> ayahWords = new ArrayList<>();
        int wordIndex = 0;
        for (Ayah ayah : ayat) {
            for (int i = 0; i < ayah.words.size() && wordIndex < words.size(); i++) {
                AyahWord aw = new AyahWord(ayah, i, ayah.words.get(i));
                Word w = words.get(wordIndex);
                aw.start = w.start;
                aw.end = w.end;
                ayahWords.add(aw);
                wordIndex++;
            }
            if (wordIndex >= words.size()) break;
        }

        // 4. Render frames
        File framesDir = new File("output/frames");
        framesDir.mkdirs();
        int fps = 25;
        double duration = words.get(words.size() - 1).end;
        int totalFrames = (int) (duration * fps);

        int width = 1280, height = 720;
        int frameIndex = 0;

        for (int f = 0; f < totalFrames; f++) {
            double t = f / (double) fps;

            // find active word
            AyahWord active = null;
            for (AyahWord aw : ayahWords) {
                if (t >= aw.start && t <= aw.end) {
                    active = aw;
                    break;
                }
            }

            // draw frame
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();

            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g.setColor(Color.BLACK);
            g.fillRect(0, 0, width, height);

            if (active != null) {
                g.setFont(new Font("SansSerif", Font.BOLD, 44));
                drawAyahWithHighlight(g, active.ayah.arabic, active.index, width, height / 2);

                g.setColor(Color.LIGHT_GRAY);
                g.setFont(new Font("Serif", Font.PLAIN, 28));
                drawCentered(g, active.ayah.translation, width, height / 2 + 80);
            }

            g.dispose();
            File out = new File(framesDir, String.format("frame%05d.png", frameIndex++));
            ImageIO.write(img, "png", out);
        }

        // 5. Combine with ffmpeg
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y",
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

    private static void drawCentered(Graphics2D g, String text, int width, int y) {
        if (text == null || text.isBlank()) return;
        FontMetrics fm = g.getFontMetrics();
        int x = (width - fm.stringWidth(text)) / 2;
        g.drawString(text, x, y);
    }

    /**
     * Draw ayah text with karaoke effect: highlight nth word in red.
     * Rendered right-to-left (words reversed).
     */
    private static void drawAyahWithHighlight(Graphics2D g, String ayahText, int activeIndex, int width, int y) {
        if (ayahText == null || ayahText.isBlank()) return;

        FontMetrics fm = g.getFontMetrics();
        String[] words = ayahText.split("\\s+");

        // reverse order for Arabic rendering
        List<String> reversed = new ArrayList<>(Arrays.asList(words));
        java.util.Collections.reverse(reversed);

        int totalWidth = 0;
        for (String w : reversed) {
            totalWidth += fm.stringWidth(w + " ");
        }

        int x = (width - totalWidth) / 2;

        for (int i = 0; i < reversed.size(); i++) {
            // find the "visual index" of active word
            int originalIndex = words.length - 1 - i;
            if (originalIndex == activeIndex) {
                g.setColor(Color.RED);
            } else {
                g.setColor(Color.WHITE);
            }
            g.drawString(reversed.get(i), x, y);
            x += fm.stringWidth(reversed.get(i) + " ");
        }
    }

    private static int detectSurah(Connection conn, List<Word> words) throws Exception {
        String transcription = String.join(" ", words.stream().map(w -> w.text).toList());
        String norm = normalizeArabic(transcription);

        PreparedStatement ps = conn.prepareStatement("SELECT id FROM surah");
        ResultSet rs = ps.executeQuery();

        int bestId = 1;
        int bestScore = Integer.MAX_VALUE;

        while (rs.next()) {
            int surahId = rs.getInt("id");
            PreparedStatement ps2 = conn.prepareStatement(
                    "SELECT GROUP_CONCAT(t.text, ' ') as fulltext " +
                    "FROM ayah a " +
                    "JOIN ayah_text t ON t.ayah_id=a.id AND t.source_id=1 " +
                    "WHERE a.surah_id=?"
            );
            ps2.setInt(1, surahId);
            ResultSet rs2 = ps2.executeQuery();
            if (rs2.next()) {
                String dbText = normalizeArabic(rs2.getString("fulltext"));
                int dist = levenshtein(norm, dbText);
                if (dist < bestScore) {
                    bestScore = dist;
                    bestId = surahId;
                }
            }
        }
        return bestId;
    }

    private static List<Ayah> loadSurah(Connection conn, int surahId) throws Exception {
        List<Ayah> ayat = new ArrayList<>();
        PreparedStatement ps = conn.prepareStatement(
                "SELECT a.ayah_number, t.text, t.bismillah, tr.translation " +
                "FROM ayah a " +
                "JOIN ayah_text t ON t.ayah_id=a.id AND t.source_id=1 " +
                "JOIN ayah_translation tr ON tr.ayah_id=a.id AND tr.source_id=1 " +
                "WHERE a.surah_id=? ORDER BY a.ayah_number"
        );
        ps.setInt(1, surahId);
        ResultSet rs = ps.executeQuery();

        boolean bismillahAdded = false;
        while (rs.next()) {
            String arabic = rs.getString("text");
            String bismillah = rs.getString("bismillah");

            // If surah has bismillah and we haven't added it yet, make it a separate ayah
            if (!bismillahAdded && bismillah != null && !bismillah.isBlank()) {
                ayat.add(new Ayah(0, bismillah.trim(), "(In the Name of Allah, the Entirely Merciful, the Especially Merciful)"));
                bismillahAdded = true;
            }

            ayat.add(new Ayah(rs.getInt("ayah_number"), arabic, rs.getString("translation")));
        }
        return ayat;
    }

    private static String normalizeArabic(String input) {
        if (input == null) return "";
        return input.replaceAll("[\\u064B-\\u065F]", "") // remove harakat
                .replaceAll("[^\\p{IsArabic} ]", "") // keep Arabic + space
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
                dp[i][j] = Math.min(Math.min(
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }
}