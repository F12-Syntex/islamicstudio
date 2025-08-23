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
import java.nio.file.Files;
import java.sql.Connection;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.imageio.ImageIO;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.syntex.islamicstudio.db.DatabaseManager;
import com.syntex.islamicstudio.media.PixabayDownloader;
import com.syntex.islamicstudio.media.WhisperTranscriber;
import com.syntex.islamicstudio.media.quran.model.Ayah;
import com.syntex.islamicstudio.media.quran.model.AyahTranscript;
import com.syntex.islamicstudio.media.quran.model.SurahMatch;
import com.syntex.islamicstudio.media.quran.model.Word;
import com.syntex.islamicstudio.media.quran.model.WordMapping;

public class QuranRecitationVideoMaker {

    private static final Gson gson = new Gson();
    private final OpenAIClient openAi;
    private final boolean debug;

    /** Video profile presets */
    public enum VideoProfile {
        DESKTOP(1920, 1080),
        INSTAGRAM_REEL(1080, 1920),
        TIKTOK(1080, 1920),
        SQUARE(1080, 1080);

        public final int width;
        public final int height;

        VideoProfile(int w, int h) {
            this.width = w;
            this.height = h;
        }
    }

    private VideoProfile profile = VideoProfile.DESKTOP;

    public QuranRecitationVideoMaker() { this(false); }
    public QuranRecitationVideoMaker(boolean debug) {
        this.openAi = OpenAIOkHttpClient.fromEnv();
        this.debug = debug;
    }

    public void setProfile(VideoProfile profile) {
        this.profile = profile;
    }

    public void generateVideo(File audioFile, File outputVideo,
                              boolean noBgAudio, int maxVerses, double bgVolume) throws Exception {

        String baseName = audioFile.getName();
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) baseName = baseName.substring(0, dot);

        File workDir = new File("output/temp", baseName);
        File framesDir = new File(workDir, "frames");
        framesDir.mkdirs();

        File transcriptFile = new File(workDir, "transcript.json");

        List<AyahTranscript> transcripts;
        List<Word> rawWords;
        SurahMatch match;

        if (transcriptFile.exists()) {
            try (var reader = new java.io.FileReader(transcriptFile)) {
                transcripts = gson.fromJson(reader, new TypeToken<List<AyahTranscript>>() {}.getType());
            }
            rawWords = new ArrayList<>();
            for (AyahTranscript at : transcripts) rawWords.addAll(at.words);
            try (Connection conn = DatabaseManager.getConnection()) {
                match = QuranAlignmentUtils.detectSurahSegment(conn, rawWords);
            }
        } else {
            WhisperTranscriber whisper = new WhisperTranscriber();
            rawWords = whisper.transcribeWithTimestamps(audioFile);
            if (rawWords.isEmpty()) throw new IllegalStateException("No transcription produced!");

            try (Connection conn = DatabaseManager.getConnection()) {
                match = QuranAlignmentUtils.detectSurahSegment(conn, rawWords);
                List<Ayah> ayat = QuranAlignmentUtils.loadSurah(conn, match.surahId, match.startAyah, joinWords(rawWords));
                List<WordMapping> mappings = QuranAlignmentUtils.alignTranscriptFlexible(rawWords, ayat);
                transcripts = QuranAlignmentUtils.buildAyahTranscripts(mappings);
            }

            try (FileWriter writer = new FileWriter(transcriptFile)) {
                gson.toJson(transcripts, writer);
            }
        }

        List<Ayah> surahAyat;
        try (Connection conn = DatabaseManager.getConnection()) {
            surahAyat = QuranAlignmentUtils.loadSurah(conn, match.surahId, match.startAyah, joinWords(rawWords));
        }
        if (transcripts == null || transcripts.isEmpty()) {
            List<WordMapping> mappings = QuranAlignmentUtils.alignTranscriptFlexible(rawWords, surahAyat);
            transcripts = QuranAlignmentUtils.buildAyahTranscripts(mappings);
        }

        // Apply max verses limit
        if (maxVerses > 0 && transcripts.size() > maxVerses) {
            transcripts = transcripts.subList(0, maxVerses);
        }
        if (maxVerses > 0 && surahAyat.size() > maxVerses) {
            surahAyat = surahAyat.subList(0, maxVerses);
        }

        String suggestion = suggestBackground(match.surahId, match.startAyah, surahAyat);
        List<File> bgVideos = PixabayDownloader.downloadBackgroundVideos(suggestion, workDir, 3);

        // Render frames with sequential numbering
        List<File> frameFiles = renderAyahFrames(framesDir, surahAyat, transcripts);

        if (outputVideo == null) outputVideo = new File(workDir, baseName + ".mp4");

        runOptimizedFfmpeg(frameFiles, audioFile, outputVideo, bgVideos, transcripts, noBgAudio, bgVolume);

        cleanupTempDir(workDir);
    }

    private List<File> renderAyahFrames(File framesDir, List<Ayah> surahAyat,
                                        List<AyahTranscript> transcripts) throws Exception {
        int width = profile.width, height = profile.height;

        Font arabicFont = new Font("Serif", Font.BOLD, height / 20);
        Font englishFont = new Font("Serif", Font.PLAIN, height / 35);
        Font footnoteFont = new Font("Serif", Font.ITALIC, height / 45);
        Font titleFont = new Font("Serif", Font.BOLD, height / 30);
        Font rawFont = new Font("Monospaced", Font.PLAIN, height / 50);

        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<File>> tasks = new ArrayList<>();

        int index = 0;
        for (AyahTranscript at : transcripts) {
            final int frameIndex = index++;
            tasks.add(pool.submit(() -> {
                try {
                    Ayah ayah = surahAyat.stream().filter(a -> a.number == at.ayahNumber).findFirst().orElse(null);
                    if (ayah == null) return null;

                    BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = img.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                    g.setColor(new Color(0, 0, 0, 180));
                    g.fillRect(0, 0, width, height);

                    int y = height / 10;

                    g.setFont(titleFont);
                    drawTextWithShadow(g, "Surah " + ayah.surahName, width, y, Color.YELLOW);
                    y += titleFont.getSize() * 3;

                    g.setFont(arabicFont);
                    y = drawWrappedTextCentered(g, ayah.arabic, width, y, Color.WHITE);
                    y += arabicFont.getSize() * 2;

                    g.setFont(englishFont);
                    y = drawWrappedTextCentered(g, ayah.translation, width, y, Color.LIGHT_GRAY);

                    if (ayah.footnotes != null && !ayah.footnotes.isEmpty()) {
                        g.setFont(footnoteFont);
                        String combined = String.join("  ", ayah.footnotes);
                        drawWrappedTextCentered(g, combined, width, height - 200, Color.GRAY);
                    }

                    if (debug) {
                        g.setFont(rawFont);
                        drawTextWithShadow(g,
                                "[RAW] " + (at.words.isEmpty() ? "" : at.words.get(at.words.size()/2).text),
                                width, height - 60, Color.CYAN);
                    }

                    g.dispose();
                    File out = new File(framesDir, String.format("ayah_seq_%03d.png", frameIndex));
                    ImageIO.write(img, "png", out);
                    return out;
                } catch (Exception e) { e.printStackTrace(); return null; }
            }));
        }

        List<File> results = new ArrayList<>();
        for (Future<File> f : tasks) {
            File file = f.get();
            if (file != null && file.exists()) results.add(file);
        }
        pool.shutdown();
        return results;
    }

    private static void runOptimizedFfmpeg(List<File> frameFiles, File audioFile, File outputVideo,
                                           List<File> bgVideos, List<AyahTranscript> transcripts,
                                           boolean noBgAudio, double bgVolume) throws Exception {
        double audioDuration = getAudioDuration(audioFile);
        double timeOffset = transcripts.isEmpty() ? 0.0 : transcripts.get(0).start;

        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg"); cmd.add("-y");

        for (File bg : bgVideos) {
            cmd.add("-stream_loop"); cmd.add("-1"); cmd.add("-i"); cmd.add(bg.getAbsolutePath());
        }
        for (File img : frameFiles) {
            cmd.add("-i"); cmd.add(img.getAbsolutePath());
        }
        cmd.add("-i"); cmd.add(audioFile.getAbsolutePath());

        StringBuilder filter = new StringBuilder();

        for (int i=0; i<bgVideos.size(); i++) {
            filter.append("[").append(i).append(":v]")
                .append("scale=1920:1080,fps=30,format=yuv420p")
                .append(",colorchannelmixer=aa=0.6")
                .append("[v").append(i).append("];");
        }

        String last = "[v0]";
        for (int i=1; i<bgVideos.size(); i++) {
            String next = "[v"+i+"]";
            String out = "[vx"+i+"]";
            double offset = (audioDuration/bgVideos.size())*i;
            filter.append(last).append(next)
                  .append("xfade=transition=fade:duration=2:offset=")
                  .append(offset).append(out).append(";");
            last = out;
        }

        String videoBase = last;
        for (int i=0; i<frameFiles.size(); i++) {
            AyahTranscript at = transcripts.get(i);
            double start = Math.max(0, at.start - timeOffset);
            // 🟢 Fix: last ayah holds until end of audio
            double nextStart = (i < transcripts.size()-1)
                    ? Math.max(0, transcripts.get(i+1).start - timeOffset)
                    : audioDuration;

            String imgIn = "[" + (bgVideos.size() + i) + ":v]";
            String out = "[vv" + i + "]";
            filter.append(videoBase).append(imgIn)
                .append("overlay=(W-w)/2:(H-h)/2:enable='between(t\\,")
                .append(start).append("\\,").append(nextStart).append(")'")
                .append(out).append(";");
            videoBase = out;
        }

        int recitationIndex = bgVideos.size() + frameFiles.size();

        if (!noBgAudio) {
            List<String> amixInputsList = new ArrayList<>();
            for (int i=0; i<bgVideos.size(); i++) {
                ProcessBuilder probe = new ProcessBuilder("ffprobe", "-i", bgVideos.get(i).getAbsolutePath(),
                        "-show_streams", "-select_streams", "a", "-loglevel", "error");
                Process proc = probe.start();
                String probeOut = new String(proc.getInputStream().readAllBytes());
                proc.waitFor();

                if (!probeOut.isBlank()) {
                    filter.append("[").append(i).append(":a]volume=").append(bgVolume)
                          .append("[aud").append(i).append("];");
                    amixInputsList.add("[aud"+i+"]");
                }
            }
            amixInputsList.add("[" + recitationIndex + ":a]");
            filter.append(String.join("", amixInputsList))
                  .append("amix=inputs=").append(amixInputsList.size())
                  .append(":normalize=0[aout];");
        } else {
            filter.append("[").append(recitationIndex).append(":a]anull[aout];");
        }

        cmd.add("-filter_complex"); cmd.add(filter.toString());
        cmd.add("-map"); cmd.add(videoBase);
        cmd.add("-map"); cmd.add("[aout]");
        cmd.add("-t"); cmd.add(String.valueOf(audioDuration));
        cmd.add("-shortest");

        cmd.add("-c:v"); cmd.add("libx264");
        cmd.add("-pix_fmt"); cmd.add("yuv420p");
        cmd.add("-c:a"); cmd.add("aac");
        cmd.add(outputVideo.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        int exit = pb.start().waitFor();
        if (exit != 0) throw new IllegalStateException("ffmpeg failed with exit code " + exit);
    }

    private static double getAudioDuration(File audioFile) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("ffprobe","-v","error",
                "-show_entries","format=duration",
                "-of","default=noprint_wrappers=1:nokey=1",
                audioFile.getAbsolutePath());
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes()).trim();
        String error = new String(proc.getErrorStream().readAllBytes()).trim();
        int exit = proc.waitFor();
        if (exit != 0 || output.isBlank()) {
            throw new IllegalStateException("ffprobe failed to get duration. Exit=" + exit + ", stderr=" + error);
        }
        return Double.parseDouble(output);
    }

    private static void cleanupTempDir(File dir) {
        if (dir == null || !dir.exists()) return;
        try {
            Files.walk(dir.toPath()).map(java.nio.file.Path::toFile)
                    .sorted((a,b)->-a.compareTo(b)).forEach(File::delete);
            System.out.println("🧹 Cleaned temp dir: " + dir.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("⚠ Failed to clean temp dir: " + e.getMessage());
        }
    }

    private String suggestBackground(int surahId, int startAyah, List<Ayah> ayat) {
        try {
            String surahName = ayat.isEmpty() ? ("Surah "+surahId) : ("Surah "+ayat.get(0).surahName);
            int endAyah = ayat.isEmpty() ? startAyah : ayat.get(ayat.size()-1).number;
            String fullTranslation = ayat.stream().map(a->a.translation)
                    .filter(t -> t!=null && !t.isBlank())
                    .reduce((a,b)->a+" "+b).orElse("");

            // 🔒 Always use fixed userPrompt
            String userPrompt = "Suggest a halal permissible background video theme for a Qur'an recitation.\n\n"
                    + "Surah: " + surahName + "\n"
                    + "Ayahs: " + startAyah + " to " + endAyah + "\n"
                    + "Theme (from translation): \"" + fullTranslation + "\"\n\n"
                    + "Guidelines:\n"
                    + "- Respond with a very short descriptive phrase only.\n"
                    + "- Avoid humans, animals, or any impermissible imagery.\n"
                    + "- Prefer natural landscapes, abstract light, calligraphy textures, night sky, or oceans.";

            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model("gpt-4o-mini")
                    .addUserMessage(userPrompt)
                    .maxTokens(100).temperature(0.6).build();

            ChatCompletion completion = openAi.chat().completions().create(params);
            return completion.choices().get(0).message().content().orElse("Abstract light background");
        } catch (Exception e) { return "Abstract gradient background (fallback)"; }
    }

    private static int drawWrappedTextCentered(Graphics2D g, String text, int width, int y, Color color) {
        if (text == null || text.isBlank()) return y;
        AttributedString attrStr = new AttributedString(text);
        attrStr.addAttribute(TextAttribute.FONT, g.getFont());
        AttributedCharacterIterator it = attrStr.getIterator();
        LineBreakMeasurer lbm = new LineBreakMeasurer(it, g.getFontRenderContext());
        float wrapWidth = width - 200;
        while (lbm.getPosition() < it.getEndIndex()) {
            var layout = lbm.nextLayout(wrapWidth);
            float dx = (width - layout.getAdvance()) / 2;
            g.setColor(new Color(0,0,0,180)); layout.draw(g, dx+3, y+3);
            g.setColor(color); layout.draw(g, dx, y);
            y += layout.getAscent()+layout.getDescent()+layout.getLeading();
        }
        return y;
    }

    private static void drawTextWithShadow(Graphics2D g, String text, int width, int y, Color color) {
        if (text == null || text.isBlank()) return;
        AttributedString attrStr = new AttributedString(text);
        attrStr.addAttribute(TextAttribute.FONT, g.getFont());
        AttributedCharacterIterator it = attrStr.getIterator();
        LineBreakMeasurer lbm = new LineBreakMeasurer(it, g.getFontRenderContext());
        float wrapWidth = width - 200;
        while (lbm.getPosition() < it.getEndIndex()) {
            var layout = lbm.nextLayout(wrapWidth);
            float dx = (width - layout.getAdvance()) / 2;
            g.setColor(new Color(0,0,0,180)); layout.draw(g, dx+3, y+3);
            g.setColor(color); layout.draw(g, dx, y);
            y += layout.getAscent()+layout.getDescent()+layout.getLeading();
        }
    }

    private static String joinWords(List<Word> words) {
        return String.join(" ", words.stream().map(w->w.text).toList());
    }
}