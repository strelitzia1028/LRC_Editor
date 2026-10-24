package com.lrceditor.mobile.model;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LrcIo {
    public static final Pattern LRC_TAG =
            Pattern.compile("^\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\](.*)$");

    private LrcIo() {
    }

    public static long parseTimeMs(Matcher matcher) {
        int minutes = Integer.parseInt(matcher.group(1));
        int seconds = Integer.parseInt(matcher.group(2));
        int millis = 0;
        String fraction = matcher.group(3);
        if (fraction != null) {
            if (fraction.length() == 1) {
                millis = Integer.parseInt(fraction) * 100;
            } else if (fraction.length() == 2) {
                millis = Integer.parseInt(fraction) * 10;
            } else {
                millis = Integer.parseInt(fraction);
            }
        }
        return minutes * 60_000L + seconds * 1000L + millis;
    }

    public static String formatTime(long millis) {
        long safe = Math.max(0, millis);
        return String.format(Locale.ROOT, "[%02d:%02d.%03d]",
                safe / 60_000, (safe / 1000) % 60, safe % 1000);
    }

    public static String formatDuration(long millis) {
        long safe = Math.max(0, millis);
        return String.format(Locale.ROOT, "%02d:%02d",
                safe / 60_000, (safe / 1000) % 60);
    }

    public static ReadResult read(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int count;
        while ((count = input.read(chunk)) >= 0) {
            buffer.write(chunk, 0, count);
        }
        byte[] bytes = buffer.toByteArray();
        boolean bom = bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF;
        int offset = bom ? 3 : 0;
        ByteBuffer data = ByteBuffer.wrap(bytes, offset, bytes.length - offset);
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(data)
                    .toString();
            return new ReadResult(text, "UTF-8", bom);
        } catch (CharacterCodingException ignored) {
            String text = new String(bytes, offset, bytes.length - offset, Charset.forName("GBK"));
            return new ReadResult(text, "GBK", false);
        }
    }

    public static void write(OutputStream output, String text, boolean withBom)
            throws IOException {
        if (withBom) {
            output.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        }
        output.write(text.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    public static List<LyricLine> parse(String text) {
        List<LyricLine> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        for (String raw : text.split("\\R", -1)) {
            if (raw.isEmpty()) continue;
            Matcher matcher = LRC_TAG.matcher(raw);
            if (matcher.matches()) {
                lines.add(new LyricLine(parseTimeMs(matcher), matcher.group(4)));
            } else {
                lines.add(new LyricLine(null, raw));
            }
        }
        return lines;
    }

    public static final class ReadResult {
        public final String text;
        public final String encoding;
        public final boolean hasBom;

        ReadResult(String text, String encoding, boolean hasBom) {
            this.text = text;
            this.encoding = encoding;
            this.hasBom = hasBom;
        }
    }
}
