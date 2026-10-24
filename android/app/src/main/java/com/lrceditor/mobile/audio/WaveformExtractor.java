package com.lrceditor.mobile.audio;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class WaveformExtractor {
    public interface Callback {
        void onProgress(int percent);
        void onWaveform(float[] minimum, float[] maximum, long durationMs);
        void onError(String message);
    }

    private WaveformExtractor() {
    }

    public static void extract(Context context, Uri uri, float bucketSeconds,
                               Callback callback) {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(context, uri, null);
            int trackIndex = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat candidate = extractor.getTrackFormat(i);
                String mime = candidate.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    trackIndex = i;
                    format = candidate;
                    break;
                }
            }
            if (trackIndex < 0 || format == null) {
                callback.onError("音频轨道不存在");
                return;
            }
            extractor.selectTrack(trackIndex);
            String mime = format.getString(MediaFormat.KEY_MIME);
            int sampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44_100;
            long durationUs = format.containsKey(MediaFormat.KEY_DURATION)
                    ? format.getLong(MediaFormat.KEY_DURATION) : 0;
            int bucketSamples = Math.max(1, Math.round(sampleRate * bucketSeconds));
            int initialBuckets = durationUs > 0
                    ? Math.max(1, (int) Math.ceil(durationUs / 1_000_000.0 / bucketSeconds))
                    : 2048;

            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();

            float[] minimum = new float[initialBuckets];
            float[] maximum = new float[initialBuckets];
            Arrays.fill(minimum, 1f);
            Arrays.fill(maximum, -1f);

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            int bucket = 0;
            int bucketSampleCount = 0;
            int pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;
            long lastProgressCheckUs = 0;

            while (!outputDone) {
                if (!inputDone) {
                    int inputIndex = codec.dequeueInputBuffer(10_000);
                    if (inputIndex >= 0) {
                        ByteBuffer input = codec.getInputBuffer(inputIndex);
                        int size = input == null ? -1 : extractor.readSampleData(input, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size,
                                    extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                int outputIndex = codec.dequeueOutputBuffer(info, 10_000);
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outputFormat = codec.getOutputFormat();
                    sampleRate = outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                            ? outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : sampleRate;
                    bucketSamples = Math.max(1, Math.round(sampleRate * bucketSeconds));
                    if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    }
                } else if (outputIndex >= 0) {
                    if (durationUs > 0 && info.presentationTimeUs - lastProgressCheckUs
                            >= durationUs / 100) {
                        lastProgressCheckUs = info.presentationTimeUs;
                        int percent = (int) Math.max(0, Math.min(99,
                                info.presentationTimeUs * 100L / durationUs));
                        callback.onProgress(percent);
                    }
                    ByteBuffer output = codec.getOutputBuffer(outputIndex);
                    if (output != null && info.size > 0) {
                        output.position(info.offset);
                        output.limit(info.offset + info.size);
                        output.order(ByteOrder.nativeOrder());
                        while (output.remaining() >= (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2)) {
                            while (bucket >= maximum.length) {
                                minimum = Arrays.copyOf(minimum, minimum.length * 2);
                                maximum = Arrays.copyOf(maximum, maximum.length * 2);
                                Arrays.fill(minimum, minimum.length / 2,
                                        minimum.length, 1f);
                                Arrays.fill(maximum, maximum.length / 2,
                                        maximum.length, -1f);
                            }
                            float sample;
                            if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                sample = output.getFloat();
                            } else {
                                sample = output.getShort() / 32768f;
                            }
                            maximum[bucket] = Math.max(maximum[bucket], sample);
                            minimum[bucket] = Math.min(minimum[bucket], sample);
                            bucketSampleCount++;
                            if (bucketSampleCount >= bucketSamples) {
                                bucket++;
                                bucketSampleCount = 0;
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                }
            }

            int bucketCount = bucket + (bucketSampleCount > 0 ? 1 : 0);
            if (bucketCount <= 0) {
                callback.onError("没有可用的音频采样");
                return;
            }
            float[] finalMin = new float[bucketCount];
            float[] finalMax = new float[bucketCount];
            for (int i = 0; i < bucketCount; i++) {
                if (maximum[i] < minimum[i]) {
                    finalMin[i] = 0;
                    finalMax[i] = 0;
                } else {
                    finalMin[i] = Math.max(-1f, minimum[i]);
                    finalMax[i] = Math.min(1f, maximum[i]);
                }
            }
            long durationMs = durationUs > 0
                    ? durationUs / 1000
                    : Math.round(bucketCount * bucketSeconds * 1000);
            callback.onWaveform(finalMin, finalMax, durationMs);
        } catch (Throwable error) {
            callback.onError(error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage());
        } finally {
            try {
                extractor.release();
            } catch (Throwable ignored) {
            }
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Throwable ignored) {
                }
                try {
                    codec.release();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
