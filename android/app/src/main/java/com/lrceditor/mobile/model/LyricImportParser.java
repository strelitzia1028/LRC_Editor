package com.lrceditor.mobile.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyricImportParser {

    private static final Pattern LRC_TAG = Pattern.compile(
            "\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern SRT_TIME = Pattern.compile(
            "(\\d{1,3}):(\\d{2}):(\\d{2})[,.]?(\\d{1,3})?");
    private static final Pattern ASS_TIME = Pattern.compile(
            "(\\d{1,3}):(\\d{2}):(\\d{2})[.](\\d{1,2})");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

    private LyricImportParser() {
    }

    public static List<LyricLine> parse(String text, String fileName) {
        String source = text == null ? "" : text;
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ass")) return parseAss(source);
        if (lower.endsWith(".srt")) return parseSrt(source);
        if (source.contains("[Events]") && source.contains("Dialogue:")) {
            return parseAss(source);
        }
        if (source.contains("-->") && SRT_TIME.matcher(source).find()) {
            return parseSrt(source);
        }
        return parseLrc(source);
    }

    public static String toLrcText(List<LyricLine> lines) {
        StringBuilder output = new StringBuilder();
        for (LyricLine line : lines) {
            String text = line.text == null ? "" : line.text
                    .replace("\r\n", "\n").replace('\r', '\n')
                    .replace("\n", "\\N");
            if (line.timeMs == null) {
                if (!text.trim().isEmpty()) output.append(text).append('\n');
            } else {
                output.append(LrcIo.formatTime(line.timeMs))
                        .append(text).append('\n');
            }
        }
        return output.toString();
    }

    public static List<LyricLine> parseLrc(String text) {
        List<LyricLine> result = new ArrayList<>();
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        for (String raw : normalized.split("\n", -1)) {
            if (raw.isEmpty()) continue;
            Matcher matcher = LRC_TAG.matcher(raw);
            List<Long> times = new ArrayList<>();
            int textStart = 0;
            while (matcher.find() && matcher.start() == textStart) {
                times.add(parseLrcTime(matcher));
                textStart = matcher.end();
            }
            String content = raw.substring(textStart).trim();
            if (times.isEmpty()) {
                if (!isMetadata(raw) && !content.isEmpty()) {
                    result.add(new LyricLine(null, content));
                }
                continue;
            }
            for (String segment : splitSegments(content)) {
                for (Long time : times) result.add(new LyricLine(time, segment));
            }
        }
        return result;
    }

    public static List<LyricLine> parseSrt(String text) {
        List<LyricLine> result = new ArrayList<>();
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        for (String block : normalized.split("\\n\\s*\\n")) {
            String[] lines = block.split("\\n", -1);
            int timing = -1;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains("-->")) {
                    timing = i;
                    break;
                }
            }
            if (timing < 0) continue;
            Long start = parseSrtTime(lines[timing].split("-->", 2)[0]);
            if (start == null) continue;
            for (int i = timing + 1; i < lines.length; i++) {
                String content = clean(lines[i]);
                if (!content.isEmpty()) result.add(new LyricLine(start, content));
            }
        }
        return result;
    }

    public static List<LyricLine> parseAss(String text) {
        List<LyricLine> result = new ArrayList<>();
        boolean events = false;
        int startIndex = 1;
        int endIndex = 2;
        int textIndex = 9;
        int fieldCount = 10;
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        for (String raw : normalized.split("\n", -1)) {
            String line = raw.trim();
            if ("[Events]".equalsIgnoreCase(line)) {
                events = true;
                continue;
            }
            if (line.startsWith("[") && !"[Events]".equalsIgnoreCase(line)) {
                events = false;
                continue;
            }
            if (!events) continue;
            if (line.startsWith("Format:")) {
                String[] headers = line.substring(7).split(",");
                fieldCount = headers.length;
                for (int i = 0; i < headers.length; i++) {
                    String header = headers[i].trim();
                    if ("Start".equalsIgnoreCase(header)) startIndex = i;
                    else if ("End".equalsIgnoreCase(header)) endIndex = i;
                    else if ("Text".equalsIgnoreCase(header)) textIndex = i;
                }
            } else if (line.startsWith("Dialogue:")) {
                String[] fields = line.substring(9).split(",", fieldCount);
                if (fields.length <= Math.max(startIndex,
                        Math.max(endIndex, textIndex))) continue;
                Long start = parseAssTime(fields[startIndex].trim());
                if (start == null) continue;
                String content = fields[textIndex]
                        .replaceAll("\\{[^}]*\\}", "")
                        .replace("\\N", "\n").replace("\\n", "\n")
                        .replace("\\h", " ");
                for (String segment : splitSegments(content)) {
                    result.add(new LyricLine(start, segment));
                }
            }
        }
        return result;
    }

    private static List<String> splitSegments(String text) {
        List<String> result = new ArrayList<>();
        String normalized = text.replace('\u2009', '\n')
                .replace('\u202F', '\n').replace("\\N", "\n");
        for (String part : normalized.split("\n", -1)) {
            String value = clean(part);
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    private static String clean(String text) {
        return HTML_TAG.matcher(text == null ? "" : text).replaceAll("")
                .replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").trim();
    }

    private static long parseLrcTime(Matcher matcher) {
        return Long.parseLong(matcher.group(1)) * 60_000
                + Long.parseLong(matcher.group(2)) * 1000
                + fraction(matcher.group(3));
    }

    private static Long parseSrtTime(String text) {
        Matcher matcher = SRT_TIME.matcher(text.trim());
        if (!matcher.find()) return null;
        return Long.parseLong(matcher.group(1)) * 3_600_000
                + Long.parseLong(matcher.group(2)) * 60_000
                + Long.parseLong(matcher.group(3)) * 1000
                + fraction(matcher.group(4));
    }

    private static Long parseAssTime(String text) {
        Matcher matcher = ASS_TIME.matcher(text.trim());
        if (!matcher.find()) return null;
        return Long.parseLong(matcher.group(1)) * 3_600_000
                + Long.parseLong(matcher.group(2)) * 60_000
                + Long.parseLong(matcher.group(3)) * 1000
                + Long.parseLong(matcher.group(4)) * 10;
    }

    private static int fraction(String value) {
        if (value == null || value.isEmpty()) return 0;
        int number = Integer.parseInt(value);
        if (value.length() == 1) return number * 100;
        if (value.length() == 2) return number * 10;
        return number;
    }

    private static boolean isMetadata(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.startsWith("[ar:") || lower.startsWith("[ti:")
                || lower.startsWith("[al:") || lower.startsWith("[by:")
                || lower.startsWith("[offset:") || lower.startsWith("[re:")
                || lower.startsWith("[ve:");
    }
}
