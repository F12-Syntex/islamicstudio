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
 * Supports:
 * - plain text
 * - segment-level (ayah)
 * - word-level timestamps (true Whisper output)
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
                .model(AudioModel.GPT_4O_TRANSCRIBE)
                .build();

        return client.audio()
                .transcriptions()
                .create(params)
                .asTranscription()
                .text();
    }

    /**
     * Return Whisper segments (ayah-level chunks with start/end).
     */
    public List<Segment> transcribeSegments(File audioFile) {
        Path path = audioFile.toPath();
        TranscriptionCreateParams params = TranscriptionCreateParams.builder()
                .file(path)
                .model(AudioModel.GPT_4O_TRANSCRIBE)
                .responseFormat(AudioResponseFormat.VERBOSE_JSON)
                .timestampGranularities(List.of(TranscriptionCreateParams.TimestampGranularity.SEGMENT))
                .build();

        TranscriptionCreateResponse response = client.audio().transcriptions().create(params);

        List<Segment> segmentsOut = new ArrayList<>();

        if (response.verbose().isPresent()) {
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
     * Return word-level timestamps (from Whisper API directly).
     */
    public List<Word> transcribeWithTimestamps(File audioFile) {
        Path path = audioFile.toPath();
        TranscriptionCreateParams params = TranscriptionCreateParams.builder()
                .file(path)
                .model(AudioModel.WHISPER_1)
                .responseFormat(AudioResponseFormat.VERBOSE_JSON)
                .timestampGranularities(List.of(TranscriptionCreateParams.TimestampGranularity.WORD))
                .build();

        TranscriptionCreateResponse response = client.audio().transcriptions().create(params);

        List<Word> words = new ArrayList<>();
        if (response.verbose().isPresent() && response.verbose().get().words().isPresent()) {
            for (var w : response.verbose().get().words().get()) {
                Word word = new Word();
                word.text = w.word();
                word.start = w.start();
                word.end = w.end();
                words.add(word);
            }
        } else {
            throw new IllegalStateException("Whisper did not return word-level timestamps.");
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