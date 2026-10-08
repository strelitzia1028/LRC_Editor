package com.lrceditor;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LrcIo {

    /** 匹配 [mm:ss]、[mm:ss.xx]、[mm:ss.xxx]、[mm:ss:xx] */
    public static final Pattern LRC_TAG =
            Pattern.compile("^\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\](.*)$");

    public static long parseTimeMs(Matcher m) {
        int min = Integer.parseInt(m.group(1));
        int sec = Integer.parseInt(m.group(2));
        int ms  = 0;
        String frac = m.group(3);
        if (frac != null) {
            ms = switch (frac.length()) {
                case 1 -> Integer.parseInt(frac) * 100;
                case 2 -> Integer.parseInt(frac) * 10;
                default -> Integer.parseInt(frac);
            };
        }
        return min * 60_000L + sec * 1000L + ms;
    }

    public static String formatTime(long ms) {
        long min = ms / 60_000;
        long s   = (ms / 1000) % 60;
        long mil = ms % 1000;
        return String.format("[%02d:%02d.%03d]", min, s, mil);
    }

    public static String formatDuration(double sec) {
        long ms = (long) (sec * 1000);
        return formatTime(ms).replace('[', ' ').replace(']', ' ').trim();
    }

    // ---------------------------------------------------------------
    // 读取（自动 BOM / UTF-8 / GBK 识别）
    // ---------------------------------------------------------------
    public static class ReadResult {
        public final String text;
        public final String encoding;
        public final boolean hasBom;
        ReadResult(String t, String e, boolean b) { text = t; encoding = e; hasBom = b; }
    }

    public static ReadResult read(File f) throws IOException {
        byte[] bytes = Files.readAllBytes(f.toPath());

        boolean hasBom = bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF;

        if (hasBom) {
            bytes = Arrays.copyOfRange(bytes, 3, bytes.length);
            return new ReadResult(new String(bytes, StandardCharsets.UTF_8), "UTF-8", true);
        }

        // 严格的 UTF-8 解码测试
        try {
            Charset utf8 = StandardCharsets.UTF_8;
            java.nio.charset.CharsetDecoder dec = utf8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
            String s = dec.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            return new ReadResult(s, "UTF-8", false);
        } catch (Exception ignore) {
            // 回退 GBK
            return new ReadResult(new String(bytes, Charset.forName("GBK")), "GBK", false);
        }
    }

    /** withBom = true 时写出 UTF-8 BOM（兼容部分老播放器中文显示） */
    public static void write(File f, String text, boolean withBom) throws IOException {
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(f))) {
            if (withBom) os.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            os.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    // ---------------------------------------------------------------
    // LRC 文本 <-> LyricLine 列表
    // ---------------------------------------------------------------
    public static List<LyricLine> parse(String text) {
        List<LyricLine> out = new ArrayList<>();
        if (text.isEmpty()) return out;
        for (String raw : text.split("\\R", -1)) {
            if (raw.isEmpty()) continue;
            Matcher m = LRC_TAG.matcher(raw);
            if (m.matches()) out.add(new LyricLine(parseTimeMs(m), m.group(4)));
            else             out.add(new LyricLine(null, raw));
        }
        return out;
    }
}
