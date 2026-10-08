package com.lrceditor;

import org.jflac.FLACDecoder;
import org.jflac.PCMProcessor;
import org.jflac.metadata.StreamInfo;
import org.jflac.util.ByteData;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** 音频工具：FLAC 播放 / 波形都通过 preparePlayable 拿到可播放的 File */
public class AudioIo {

    private static final Map<File, File> cache = new HashMap<>();

    public static boolean isFlac(File f) {
        return f.getName().toLowerCase(Locale.ROOT).endsWith(".flac");
    }

    /**
     * 若为 FLAC，用 jflac 解码为临时 WAV 并返回；否则原样返回。
     * 结果按源文件缓存，切歌只解一次。
     */
    public static File preparePlayable(File f) throws IOException {
        if (!isFlac(f)) return f;
        File cached = cache.get(f);
        if (cached != null && cached.exists() && cached.lastModified() >= f.lastModified())
            return cached;

        File wav = Files.createTempFile("lrceditor_", ".wav").toFile();
        wav.deleteOnExit();
        decodeFlacToWav(f, wav);
        cache.put(f, wav);
        return wav;
    }

    private static void decodeFlacToWav(File flac, File wav) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(flac))) {
            FLACDecoder decoder = new FLACDecoder(in);
            final int[] fmt = new int[3];                    // sampleRate, channels, bits
            final ByteArrayOutputStream pcm = new ByteArrayOutputStream(1 << 20);

            decoder.addPCMProcessor(new PCMProcessor() {
                @Override public void processStreamInfo(StreamInfo info) {
                    fmt[0] = (int) info.getSampleRate();
                    fmt[1] = info.getChannels();
                    fmt[2] = info.getBitsPerSample();
                }
                @Override public void processPCM(ByteData data) {
                    // 注意：ByteData 可能是复用的缓冲区，必须主动拷贝有效长度
                    pcm.write(data.getData(), 0, data.getLen());
                }
            });
            decoder.decode();

            try (OutputStream os = new BufferedOutputStream(new FileOutputStream(wav))) {
                writeWavHeader(os, pcm.size(), fmt[0], fmt[1], fmt[2]);
                pcm.writeTo(os);
            }
        }
    }

    private static void writeWavHeader(OutputStream os, int dataLen,
                                       int sampleRate, int channels, int bits) throws IOException {
        int byteRate   = sampleRate * channels * bits / 8;
        int blockAlign = channels * bits / 8;

        ascii(os, "RIFF"); intLE(os, 36 + dataLen); ascii(os, "WAVE");
        ascii(os, "fmt "); intLE(os, 16); shortLE(os, 1);
        shortLE(os, channels); intLE(os, sampleRate);
        intLE(os, byteRate);   shortLE(os, blockAlign); shortLE(os, bits);
        ascii(os, "data");     intLE(os, dataLen);
    }

    private static void ascii  (OutputStream o, String s) throws IOException { o.write(s.getBytes("ASCII")); }
    private static void intLE  (OutputStream o, int v)  throws IOException {
        o.write(v & 0xFF); o.write((v >>> 8) & 0xFF); o.write((v >>> 16) & 0xFF); o.write((v >>> 24) & 0xFF);
    }
    private static void shortLE(OutputStream o, int v)  throws IOException {
        o.write(v & 0xFF); o.write((v >>> 8) & 0xFF);
    }
}
