package com.lrceditor;

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
        if (source.contains("-->")
                && SRT_TIME.matcher(source).find()) {
            return parseSrt(source);
        }
        return parseLrc(source);
    }

    public static String toLrcText(List<LyricLine> lines) {
        StringBuilder output = new StringBuilder();
        for (LyricLine line : lines) {
            String text = line.text == null ? "" : line.text
                    .replace("\r\n", "\n")
                    .replace('\r', '\n')
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
        for (String raw : text.replace("\r\n", "\n").replace('\r', '\n')
                .split("\n", -1)) {
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
            for (String segment : splitLyricSegments(content)) {
                for (Long time : times) result.add(new LyricLine(time, segment));
            }
        }
        return result;
    }

    public static List<LyricLine> parseSrt(String text) {
        List<LyricLine> result = new ArrayList<>();
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] blocks = normalized.split("\\n\\s*\\n");
        for (String block : blocks) {
            String[] lines = block.split("\\n", -1);
            int timingIndex = -1;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains("-->")) {
                    timingIndex = i;
                    break;
                }
            }
            if (timingIndex < 0) continue;
            String[] times = lines[timingIndex].split("-->", 2);
            Long start = parseSrtTime(times[0]);
            if (start == null) continue;
            for (int i = timingIndex + 1; i < lines.length; i++) {
                String content = cleanSubtitleText(lines[i]);
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
        for (String raw : text.replace("\r\n", "\n").replace('\r', '\n')
                .split("\n", -1)) {
            String line = raw.trim();
            if (line.equalsIgnoreCase("[Events]")) {
                events = true;
                continue;
            }
            if (line.startsWith("[") && !line.equalsIgnoreCase("[Events]")) {
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
                        Math.max(endIndex, textIndex))) {
                    continue;
                }
                Long start = parseAssTime(fields[startIndex].trim());
                if (start == null) continue;
                String content = fields[textIndex];
                content = content.replaceAll("\\{[^}]*\\}", "")
                        .replace("\\N", "\n")
                        .replace("\\n", "\n")
                        .replace("\\h", " ");
                for (String segment : splitLyricSegments(content)) {
                    result.add(new LyricLine(start, segment));
                }
            }
        }
        return result;
    }

    private static List<String> splitLyricSegments(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) return result;
        String normalized = text.replace('\u2009', '\n')
                .replace('\u202F', '\n')
                .replace("\\N", "\n");
        for (String line : normalized.split("\n", -1)) {
            String value = cleanSubtitleText(line);
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    private static String cleanSubtitleText(String text) {
        String value = HTML_TAG.matcher(text == null ? "" : text)
                .replaceAll("")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .trim();
        return value;
    }

    private static long parseLrcTime(Matcher matcher) {
        int minutes = Integer.parseInt(matcher.group(1));
        int seconds = Integer.parseInt(matcher.group(2));
        int millis = fractionToMillis(matcher.group(3));
        return minutes * 60_000L + seconds * 1000L + millis;
    }

    private static Long parseSrtTime(String text) {
        Matcher matcher = SRT_TIME.matcher(text.trim());
        if (!matcher.find()) return null;
        long hours = Long.parseLong(matcher.group(1));
        long minutes = Long.parseLong(matcher.group(2));
        long seconds = Long.parseLong(matcher.group(3));
        long millis = fractionToMillis(matcher.group(4));
        return hours * 3_600_000 + minutes * 60_000 + seconds * 1000 + millis;
    }

    private static Long parseAssTime(String text) {
        Matcher matcher = ASS_TIME.matcher(text.trim());
        if (!matcher.find()) return null;
        long hours = Long.parseLong(matcher.group(1));
        long minutes = Long.parseLong(matcher.group(2));
        long seconds = Long.parseLong(matcher.group(3));
        long centis = Long.parseLong(matcher.group(4));
        return hours * 3_600_000 + minutes * 60_000 + seconds * 1000 + centis * 10;
    }

    private static int fractionToMillis(String fraction) {
        if (fraction == null || fraction.isEmpty()) return 0;
        int value = Integer.parseInt(fraction);
        return switch (fraction.length()) {
            case 1 -> value * 100;
            case 2 -> value * 10;
            default -> value;
        };
    }

    private static boolean isMetadata(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.startsWith("[ar:")
                || lower.startsWith("[ti:")
                || lower.startsWith("[al:")
                || lower.startsWith("[by:")
                || lower.startsWith("[offset:")
                || lower.startsWith("[re:")
                || lower.startsWith("[ve:");
    }
}
