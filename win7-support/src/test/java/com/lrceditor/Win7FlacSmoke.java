package com.lrceditor;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;

public final class Win7FlacSmoke {

    private Win7FlacSmoke() {
    }

    public static void main(String[] args) throws Exception {
        File source = new File(args[0]);
        AudioInputStream stream = AudioSystem.getAudioInputStream(source);
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = stream.read(buffer)) >= 0) {
            total += read;
        }
        stream.close();
        System.out.println("flacBytes=" + total
                + " format=" + stream.getFormat());
        if (total == 0) {
            throw new IllegalStateException("FLAC decoder returned no audio");
        }
    }
}
