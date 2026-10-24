package com.lrceditor;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;

public final class FlacImportSmoke {
    private FlacImportSmoke() {
    }

    public static void main(String[] args) throws Exception {
        File source = new File(args[0]);
        long started = System.currentTimeMillis();
        File wav = AudioIo.preparePlayable(source);
        long elapsed = System.currentTimeMillis() - started;
        if (wav == null || !wav.isFile() || wav.length() <= 44) {
            throw new IllegalStateException("FLAC 转码没有生成有效 WAV");
        }
        long bytes = 0;
        try (AudioInputStream input = AudioSystem.getAudioInputStream(wav)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) bytes += read;
        }
        if (bytes == 0) throw new IllegalStateException("转码后的 WAV 没有音频数据");
        System.out.println("flacImportMs=" + elapsed
                + " wavBytes=" + wav.length()
                + " pcmBytes=" + bytes);
    }
}
