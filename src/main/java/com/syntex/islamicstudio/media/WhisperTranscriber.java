package com.syntex.islamicstudio.media;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.audio.AudioModel;
import com.openai.models.audio.AudioResponseFormat;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;
import com.openai.models.audio.transcriptions.TranscriptionSegment;
import com.openai.models.audio.transcriptions.TranscriptionVerbose;
import com.syntex.islamicstudio.media.quran.model.Word;

/**
 * Wrapper around OpenAI Whisper API for transcription.
 * Now supports both:
 *  - word-level approximation
 *  - segment-level (ayah-level) transcription
 */
public class WhisperTranscriber {

    private final OpenAIClient client;

    public WhisperTranscriber() {
        this.client = OpenAIOkHttpClient.fromEnv();
    }

    /**
     * Simple text transcription (no timestamps).
     */
    public String transcribeText(File audioFile) {
        Path path = audioFile.toPath();
        TranscriptionCreateParams params = TranscriptionCreateParams.builder()
                .file(path)
                .model(AudioModel.WHISPER_1)
                .build();

        return client.audio()
                .transcriptions()
                .create(params)
                .asTranscription()
                .text();
    }

    /**
     * Return Whisper segments (ayah-level chunks).
     */
    public List<Segment> transcribeSegments(File audioFile) {
        Path path = audioFile.toPath();
        TranscriptionCreateParams params = TranscriptionCreateParams.builder()
                .file(path)
                .model(AudioModel.WHISPER_1)
                .responseFormat(AudioResponseFormat.VERBOSE_JSON)
                .build();

        TranscriptionCreateResponse response = client.audio().transcriptions().create(params);

        List<Segment> segmentsOut = new ArrayList<>();

        if (response.verbose() != null) {
            Optional<TranscriptionVerbose> verboseOpt = response.verbose();
            if (verboseOpt.isPresent() && verboseOpt.get().segments().isPresent()) {
                List<TranscriptionSegment> segments = verboseOpt.get().segments().get();
                for (TranscriptionSegment seg : segments) {
                    segmentsOut.add(new Segment(seg.start(), seg.end(), seg.text()));
                }
            }
        }
        return segmentsOut;
    }

    /**
     * Return word-level timestamps by splitting segments evenly.
     */
    public List<Word> transcribeWithTimestamps(File audioFile) {
        List<Word> words = new ArrayList<>();
        for (Segment seg : transcribeSegments(audioFile)) {
            String[] segWords = seg.text.trim().split("\\s+");
            double wordDur = (seg.end - seg.start) / segWords.length;
            for (int i = 0; i < segWords.length; i++) {
                Word w = new Word();
                w.text = segWords[i];
                w.start = seg.start + i * wordDur;
                w.end = w.start + wordDur;
                words.add(w);
            }
        }
        return words;
    }

    /**
     * Simple immutable record for Whisper segment.
     */
    public static class Segment {
        public final double start;
        public final double end;
        public final String text;
        public Segment(double start, double end, String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }
}