package com.syntex.islamicstudio.commands;

import java.io.File;

import com.syntex.islamicstudio.cli.CommandCategory;
import com.syntex.islamicstudio.media.quran.QuranRecitationVideoMaker;
import com.syntex.islamicstudio.media.quran.QuranRecitationVideoMaker.VideoProfile;

import picocli.CommandLine;

@CommandLine.Command(
        name = "quran-video",
        description = "Generate a Qur'anic recitation video with translation, notes, and background"
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

    @CommandLine.Option(
            names = {"--debug"},
            description = "Enable debug mode (overlay raw transcript and highlight current word)"
    )
    private boolean debug = false;

    @CommandLine.Option(
            names = {"--profile"},
            description = "Video profile: ${COMPLETION-CANDIDATES} (default: DESKTOP)"
    )
    private VideoProfile profile = VideoProfile.DESKTOP;

    @CommandLine.Option(
            names = {"--no-bg-audio"},
            description = "Disable background video audio (keep recitation only)"
    )
    private boolean noBgAudio = false;

    @CommandLine.Option(
            names = {"--max-verses"},
            description = "Maximum number of verses to include in the video (default: unlimited)"
    )
    private int maxVerses = 0;

    @CommandLine.Option(
            names = {"--bg-volume"},
            description = "Background video audio volume (0.0 - 1.0, default: 0.2)"
    )
    private double bgVolume = 0.2;

    @Override
    public void run() {
        try {
            System.out.println("🎬 Generating Qur'anic video...");
            QuranRecitationVideoMaker maker = new QuranRecitationVideoMaker(debug);
            maker.setProfile(profile);
            maker.generateVideo(audioFile, outputFile, noBgAudio, maxVerses, bgVolume);
            System.out.println("✅ Video generated successfully: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("❌ Failed to generate Qur'anic video: " + e.getMessage());
            e.printStackTrace();
        }
    }
}