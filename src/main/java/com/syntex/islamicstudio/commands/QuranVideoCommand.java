package com.syntex.islamicstudio.commands;

import com.syntex.islamicstudio.cli.CommandCategory;
import com.syntex.islamicstudio.media.quran.QuranRecitationVideoMaker;
import picocli.CommandLine;

import java.io.File;

@CommandLine.Command(
        name = "quran-video",
        description = "Generate a Qur'anic recitation video with translation and notes"
)
@CommandCategory("Media")
public class QuranVideoCommand implements Runnable {

    @CommandLine.Parameters(
            index = "0",
            paramLabel = "AUDIO_FILE",
            description = "Path to recitation audio file (e.g., recitations/095.mp3)"
    )
    private File audioFile;

    @CommandLine.Option(
            names = {"-o", "--output"},
            description = "Output video file name (default: output/quran_video.mp4)"
    )
    private File outputFile = new File("output/quran_video.mp4");

    @Override
    public void run() {
        try {
            System.out.println("🎬 Generating Qur'anic video...");
            QuranRecitationVideoMaker maker = new QuranRecitationVideoMaker();
            maker.generateVideo(audioFile, outputFile);
            System.out.println("✅ Video generated successfully: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("❌ Failed to generate Qur'anic video: " + e.getMessage());
            e.printStackTrace();
        }
    }
}