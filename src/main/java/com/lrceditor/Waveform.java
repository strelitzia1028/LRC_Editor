package com.lrceditor;

import javafx.concurrent.Task;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Waveform {
    public final float[] min;
    public final float[] max;
    public final double  secPerBucket;

    public Waveform(float[] min, float[] max, double secPerBucket) {
        this.min = min; this.max = max; this.secPerBucket = secPerBucket;
    }
    public int bucketCount() { return min.length; }
}

/** 后台线程执行；每帧 progressProperty 可驱动进度条 */
class WaveformTask extends Task<Waveform> {
    private final File   file;
    private final double bucketSec;

    WaveformTask(File file, double bucketSec) {
        this.file = file;
        this.bucketSec = bucketSec;
    }

    @Override
    protected Waveform call() throws Exception {
        File src = AudioIo.preparePlayable(file);           // FLAC 会在这里变成临时 WAV

        try (AudioInputStream in = AudioSystem.getAudioInputStream(src)) {
            AudioFormat f = in.getFormat();
            // 统一转成 16-bit 单声道 PCM 便于分析
            AudioFormat pcm = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    f.getSampleRate(), 16, 1, 2, f.getSampleRate(), false);

            try (AudioInputStream conv = AudioSystem.getAudioInputStream(pcm, in)) {
                int bytesPerSec = (int) (f.getSampleRate() * 2);
                int chunkBytes  = Math.max(2, (int) (bytesPerSec * bucketSec));

                byte[] buf = new byte[chunkBytes];
                List<float[]> buckets = new ArrayList<>();

                long totalEstimate = src.length();   // 粗估，用来更新进度
                long readTotal = 0;
                int n;

                while ((n = conv.read(buf)) > 0) {
                    float mn = 0f, mx = 0f;
                    for (int i = 0; i + 1 < n; i += 2) {
                        short s = (short) ((buf[i] & 0xFF) | (buf[i + 1] << 8));
                        float v = s / 32768f;
                        if (v < mn) mn = v;
                        if (v > mx) mx = v;
                    }
                    buckets.add(new float[]{mn, mx});
                    readTotal += n;
                    updateProgress(readTotal, totalEstimate > 0 ? totalEstimate : -1);
                }

                int k = buckets.size();
                float[] mins = new float[k], maxs = new float[k];
                for (int i = 0; i < k; i++) {
                    mins[i] = buckets.get(i)[0];
                    maxs[i] = buckets.get(i)[1];
                }
                double secPerBucket = (k == 0) ? bucketSec : (double) readTotal / bytesPerSec / k;
                return new Waveform(mins, maxs, secPerBucket);
            }
        }
    }
}
