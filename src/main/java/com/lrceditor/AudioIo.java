package com.lrceditor;

import org.jflac.FLACDecoder;
import org.jflac.PCMProcessor;
import org.jflac.metadata.StreamInfo;
import org.jflac.util.ByteData;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
        synchronized (AudioIo.class) {
            File cached = cache.get(f);
            if (cached != null && cached.exists()
                    && cached.lastModified() >= f.lastModified()) {
                return cached;
            }

            File wav = Files.createTempFile("lrceditor_", ".wav").toFile();
            wav.deleteOnExit();
            try {
                if (!decodeFlacWithFfmpeg(f, wav)) {
                    decodeFlacToWav(f, wav);
                }
                if (!wav.exists() || wav.length() <= 44) {
                    throw new IOException("FLAC 解码后没有得到有效音频数据");
                }
                cache.put(f, wav);
                return wav;
            } catch (IOException | RuntimeException error) {
                if (wav.exists()) wav.delete();
                throw error;
            }
        }
    }

    private static boolean decodeFlacWithFfmpeg(File flac, File wav) throws IOException {
        Path ffmpeg = LyricVideoExporter.findFfmpeg();
        if (ffmpeg == null) return false;

        List<String> command = Arrays.asList(
                ffmpeg.toString(),
                "-hide_banner",
                "-loglevel", "error",
                "-nostdin",
                "-y",
                "-i", flac.getAbsolutePath(),
                "-vn",
                "-acodec", "pcm_s16le",
                wav.getAbsolutePath());
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "UTF-8"))) {
            StringBuilder log = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (log.length() > 0) log.append('\n');
                log.append(line);
            }
            output = log.toString();
        }
        try {
            if (!process.waitFor(15, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                return false;
            }
        } catch (InterruptedException error) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("FLAC 解码被中断", error);
        }
        int exit = process.exitValue();
        if (exit == 0 && wav.exists() && wav.length() > 44) {
            return true;
        }
        if (wav.exists()) wav.delete();
        System.err.println("FFmpeg FLAC 转码失败：" + output);
        return false;
    }

    private static void decodeFlacToWav(File flac, File wav) throws IOException {
        RandomAccessFile output = new RandomAccessFile(wav, "rw");
        final long[] written = {0};
        final IOException[] failure = {null};
        try (InputStream in = new BufferedInputStream(new FileInputStream(flac))) {
            FLACDecoder decoder = new FLACDecoder(in);
            final int[] fmt = new int[3];                    // sampleRate, channels, bits
            output.setLength(0);
            output.write(new byte[44]);

            decoder.addPCMProcessor(new PCMProcessor() {
                @Override public void processStreamInfo(StreamInfo info) {
                    fmt[0] = (int) info.getSampleRate();
                    fmt[1] = info.getChannels();
                    fmt[2] = info.getBitsPerSample();
                }
                @Override public void processPCM(ByteData data) {
                    try {
                        output.write(data.getData(), 0, data.getLen());
                        written[0] += data.getLen();
                    } catch (IOException error) {
                        failure[0] = error;
                        throw new RuntimeException(error);
                    }
                }
            });
            try {
                decoder.decode();
            } catch (RuntimeException error) {
                if (failure[0] != null) throw failure[0];
                throw error;
            }
            if (failure[0] != null) throw failure[0];
            if (fmt[0] <= 0 || fmt[1] <= 0 || fmt[2] <= 0) {
                throw new IOException("FLAC 流信息无效");
            }
            long dataLength = written[0];
            if (dataLength > Integer.MAX_VALUE - 36L) {
                throw new IOException("FLAC 音频过大，无法转换为标准 WAV");
            }
            output.seek(0);
            writeWavHeader(output, (int) dataLength, fmt[0], fmt[1], fmt[2]);
        } finally {
            output.close();
        }
    }

    private static void writeWavHeader(RandomAccessFile os, int dataLen,
                                       int sampleRate, int channels, int bits) throws IOException {
        int byteRate   = sampleRate * channels * bits / 8;
        int blockAlign = channels * bits / 8;

        ascii(os, "RIFF"); intLE(os, 36 + dataLen); ascii(os, "WAVE");
        ascii(os, "fmt "); intLE(os, 16); shortLE(os, 1);
        shortLE(os, channels); intLE(os, sampleRate);
        intLE(os, byteRate);   shortLE(os, blockAlign); shortLE(os, bits);
        ascii(os, "data");     intLE(os, dataLen);
    }

    private static void ascii  (RandomAccessFile o, String s) throws IOException { o.write(s.getBytes("ASCII")); }
    private static void intLE  (RandomAccessFile o, int v)  throws IOException {
        o.write(v & 0xFF); o.write((v >>> 8) & 0xFF); o.write((v >>> 16) & 0xFF); o.write((v >>> 24) & 0xFF);
    }
    private static void shortLE(RandomAccessFile o, int v)  throws IOException {
        o.write(v & 0xFF); o.write((v >>> 8) & 0xFF);
    }
}
