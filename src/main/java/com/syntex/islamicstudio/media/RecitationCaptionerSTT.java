package com.syntex.islamicstudio.media;

import java.io.File;

/**
 * Pure raw transcription (no DB). Each recognized segment (with start/end
 * times) is written to the subtitle file.
 */
public class RecitationCaptionerSTT {

    private final File audioFile;
    private final SubtitleWriter subtitleWriter;

    public RecitationCaptionerSTT(File audioFile, String subtitlePath) throws Exception {
        this.audioFile = audioFile;
        this.subtitleWriter = new SubtitleWriter(subtitlePath);
    }

    public void start() throws Exception {
        WhisperTranscriber whisper = new WhisperTranscriber();
        var words = whisper.transcribeWithTimestamps(audioFile);

        // Build the full text once
        StringBuilder fullTextBuilder = new StringBuilder();
        for (var w : words) {
            fullTextBuilder.append(w.text).append(" ");
        }
        String fullText = fullTextBuilder.toString().trim();

        // Now write word-by-word subtitles
        for (var w : words) {
            subtitleWriter.addWordSubtitle(w.start, w.end, fullText, w.text);
        }

        subtitleWriter.close();
        System.out.println("✅ Detailed word-level subtitles written.");
    }
}
