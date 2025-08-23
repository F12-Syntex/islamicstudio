package com.syntex.islamicstudio.media.quran;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.syntex.islamicstudio.db.DatabaseManager;
import com.syntex.islamicstudio.media.WhisperTranscriber;
import com.syntex.islamicstudio.media.quran.model.Ayah;
import com.syntex.islamicstudio.media.quran.model.AyahTranscript;
import com.syntex.islamicstudio.media.quran.model.SurahMatch;
import com.syntex.islamicstudio.media.quran.model.Word;

public class QuranRecitationVideoMaker {

    private static final Gson gson = new Gson();
    private final OpenAIClient openAi;

    public QuranRecitationVideoMaker() {
        this.openAi = OpenAIOkHttpClient.fromEnv();
    }

    public void generateVideo(File audioFile, File outputVideo) throws Exception {
        String baseName = audioFile.getName();
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) {
            baseName = baseName.substring(0, dot);
        }

        File workDir = new File("output/temp", baseName);
        File framesDir = new File(workDir, "frames");
        framesDir.mkdirs();

        File transcriptFile = new File(workDir, "transcript.json");

        List<AyahTranscript> transcripts;
        List<Word> rawWords;
        SurahMatch match;

        if (transcriptFile.exists()) {
            try (var reader = new java.io.FileReader(transcriptFile)) {
                transcripts = gson.fromJson(reader, new TypeToken<List<AyahTranscript>>() {
                }.getType());
            }
            rawWords = new ArrayList<>();
            for (AyahTranscript at : transcripts) {
                rawWords.addAll(at.words);
            }
            try (Connection conn = DatabaseManager.getConnection()) {
                match = QuranAlignmentUtils.detectSurahSegment(conn, rawWords);
            }
        } else {
            WhisperTranscriber whisper = new WhisperTranscriber();
            rawWords = whisper.transcribeWithTimestamps(audioFile);
            if (rawWords.isEmpty()) {
                throw new IllegalStateException("No transcription produced!");
            }

            try (Connection conn = DatabaseManager.getConnection()) {
                match = QuranAlignmentUtils.detectSurahSegment(conn, rawWords);
                List<Ayah> ayat = QuranAlignmentUtils.loadSurah(conn, match.surahId, match.startAyah, joinWords(rawWords));
                transcripts = QuranAlignmentUtils.alignTranscriptToSurah(rawWords, ayat);
            }

            try (FileWriter writer = new FileWriter(transcriptFile)) {
                gson.toJson(transcripts, writer);
            }
        }

        List<Ayah> surahAyat;
        try (Connection conn = DatabaseManager.getConnection()) {
            surahAyat = QuranAlignmentUtils.loadSurah(conn, match.surahId, match.startAyah, joinWords(rawWords));
        }

        String suggestion = suggestBackground(match.surahId, match.startAyah, surahAyat);
        File bgVideo = PixabayDownloader.downloadBackgroundVideo(suggestion, workDir);

        // render one PNG per ayah
        renderAyahFrames(framesDir, surahAyat, transcripts);

        if (outputVideo == null) {
            outputVideo = new File(workDir, baseName + ".mp4");
        }

        runOptimizedFfmpeg(framesDir, audioFile, outputVideo, bgVideo, transcripts);
    }

    // ================= Rendering ================= //
    private static void renderAyahFrames(File framesDir, List<Ayah> surahAyat,
            List<AyahTranscript> transcripts) throws Exception {
        int width = 1920, height = 1080;

        Font arabicFont = new Font("Serif", Font.BOLD, 72);
        Font englishFont = new Font("Serif", Font.PLAIN, 36);
        Font footnoteFont = new Font("Serif", Font.ITALIC, 24);
        Font titleFont = new Font("Serif", Font.BOLD, 40);

        for (AyahTranscript at : transcripts) {
            Ayah ayah = surahAyat.stream()
                    .filter(a -> a.number == at.ayahNumber)
                    .findFirst().orElse(null);
            if (ayah == null) {
                continue;
            }

            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int y = 100;

            g.setFont(titleFont);
            g.setColor(Color.YELLOW);
            drawWrappedTextCentered(g, "سورة " + ayah.surahName, width, y);
            y += 60;

            // Arabic
            g.setFont(arabicFont);
            g.setColor(Color.WHITE);
            y = drawWrappedTextCentered(g, ayah.arabic, width, y);

            y += 40;

            // Translation
            g.setFont(englishFont);
            g.setColor(Color.WHITE);
            y = drawWrappedTextCentered(g, ayah.translation, width, y);

            // Footnotes at bottom in black box
            if (ayah.footnotes != null && !ayah.footnotes.isEmpty()) {
                String combined = String.join("  ", ayah.footnotes);
                g.setFont(footnoteFont);

                int boxHeight = 150;
                g.setColor(new Color(0, 0, 0, 200));
                g.fillRect(0, height - boxHeight - 20, width, boxHeight);

                g.setColor(Color.WHITE);
                drawWrappedTextCentered(g, combined, width, height - boxHeight);
            }

            g.dispose();
            File out = new File(framesDir, String.format("ayah_%03d.png", at.ayahNumber));
            ImageIO.write(img, "png", out);
        }
    }

    // ================= Optimized ffmpeg ================= //
    private static void runOptimizedFfmpeg(File framesDir, File audioFile, File outputVideo,
            File bgVideo, List<AyahTranscript> transcripts) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-y");

        // loop the background video infinitely until audio ends
        cmd.add("-stream_loop");
        cmd.add("-1");
        cmd.add("-i");
        cmd.add(bgVideo.getAbsolutePath());

        // add ayah images as inputs
        for (AyahTranscript at : transcripts) {
            File img = new File(framesDir, String.format("ayah_%03d.png", at.ayahNumber));
            cmd.add("-i");
            cmd.add(img.getAbsolutePath());
        }

        // add audio
        cmd.add("-i");
        cmd.add(audioFile.getAbsolutePath());

        // build filter_complex string
        StringBuilder filter = new StringBuilder();
        filter.append("[0:v]scale=1920:1080,eq=brightness=-0.3[bg];");
        String last = "[bg]";

        for (int i = 0; i < transcripts.size(); i++) {
            AyahTranscript at = transcripts.get(i);
            double start = at.start;
            double end = at.end;
            String imgIn = "[" + (i + 1) + ":v]";
            String out = "[v" + (i + 1) + "]";
            filter.append(last).append(imgIn)
                    .append("overlay=(W-w)/2:(H-h)/2:enable=between(t\\,")
                    .append(start).append("\\,").append(end).append(")")
                    .append(out).append(";");
            last = out;
        }

        // proper final output
        filter.append(last).append("copy[vout]");

        cmd.add("-filter_complex");
        cmd.add(filter.toString());

        // map video + audio
        cmd.add("-map");
        cmd.add("[vout]");
        cmd.add("-map");
        cmd.add(transcripts.size() + 1 + ":a"); // audio is last input
        cmd.add("-shortest");

        // encoding options
        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-pix_fmt");
        cmd.add("yuv420p");
        cmd.add("-c:a");
        cmd.add("aac");

        cmd.add(outputVideo.getAbsolutePath());

        // run ffmpeg
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        int exit = pb.start().waitFor();
        if (exit != 0) {
            throw new IllegalStateException("ffmpeg failed with exit code " + exit);
        }
    }

    // ================= OpenAI Suggestion ================= //
    private String suggestBackground(int surahId, int startAyah, List<Ayah> ayat) {
        try {
            String surahName = ayat.isEmpty() ? ("Surah " + surahId) : ("Surah " + ayat.get(0).surahName);
            int endAyah = ayat.isEmpty() ? startAyah : ayat.get(ayat.size() - 1).number;
            String sampleTranslation = ayat.isEmpty() ? "" : ayat.get(0).translation;
            if (sampleTranslation.length() > 150) {
                sampleTranslation = sampleTranslation.substring(0, 150) + "...";
            }

            String userPrompt = "Suggest a halal permissible background video theme for a Qur'an recitation.\n\n"
                    + "Surah: " + surahName + "\n"
                    + "Ayahs: " + startAyah + " to " + endAyah + "\n"
                    + "Theme (from translation): \"" + sampleTranslation + "\"\n\n"
                    + "Guidelines:\n"
                    + "- Respond with a very short descriptive phrase only.\n"
                    + "- Avoid humans, animals, or any impermissible imagery.\n"
                    + "- Prefer natural landscapes, abstract light, calligraphy textures, night sky, or oceans.\n"
                    + "- Prompt is for pixabay, so please chose something that would return results.";

            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model("gpt-4o-mini")
                    .addUserMessage(userPrompt)
                    .maxTokens(100)
                    .temperature(0.6)
                    .build();

            ChatCompletion completion = openAi.chat().completions().create(params);

            return completion.choices().get(0).message().content().orElse("Abstract light background");

        } catch (Exception e) {
            return "Abstract gradient background (fallback)";
        }
    }

    // ================= Drawing Helpers ================= //
    private static int drawWrappedTextCentered(Graphics2D g, String text, int width, int y) {
        if (text == null || text.isBlank()) {
            return y; // nothing to draw
        }

        AttributedString attrStr = new AttributedString(text);
        attrStr.addAttribute(TextAttribute.FONT, g.getFont());
        AttributedCharacterIterator it = attrStr.getIterator();
        LineBreakMeasurer lbm = new LineBreakMeasurer(it, g.getFontRenderContext());

        float wrapWidth = width - 200;
        while (lbm.getPosition() < it.getEndIndex()) {
            var layout = lbm.nextLayout(wrapWidth);
            float dx = (width - layout.getAdvance()) / 2;
            y += layout.getAscent();
            layout.draw(g, dx, y);
            y += layout.getDescent() + layout.getLeading();
        }
        return y;
    }

    private static String joinWords(List<Word> words) {
        return String.join(" ", words.stream().map(w -> w.text).toList());
    }
}
