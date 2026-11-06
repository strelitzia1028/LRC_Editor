package com.lrceditor;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class LyricExportFormatter {

    public static final String NARROW_SPACE = "\u2009";

    public enum Format {
        LRC("LRC 歌词", "lrc"),
        NETEASE_LRC("网易云多语 LRC", "lrc"),
        SRT("SRT 字幕", "srt"),
        ASS("ASS 字幕", "ass");

        public final String label;
        public final String extension;

        Format(String label, String extension) {
            this.label = label;
            this.extension = extension;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static final class Options {
        public Format format = Format.LRC;
        public boolean mergeDuplicateText = false;
        public String multiLanguageSeparator = NARROW_SPACE;
        public String fontFamily = "Microsoft YaHei";
        public int fontSize = 48;
        public int subtitleWidth = 1920;
        public int subtitleHeight = 1080;
        public Color textColor = Color.WHITE;
        public int outline = 2;
    }

    private LyricExportFormatter() {
    }

    public static String build(LrcDocument document, Options options) {
        Options actual = options == null ? new Options() : options;
        if (actual.format == Format.NETEASE_LRC) {
            return buildNetEaseLrc(document, actual);
        }
        if (actual.format == Format.SRT) return buildSrt(document);
        if (actual.format == Format.ASS) return buildAss(document, actual);
        return buildLrc(document, actual.mergeDuplicateText);
    }

    public static String buildLrc(LrcDocument document, boolean merge) {
        if (!merge) return document.serialize(true);
        Map<String, List<Long>> timestampsByText = new LinkedHashMap<>();
        for (LyricLine line : document.getLines()) {
            if (isBlank(line) || line.timeMs == null) continue;
            timestampsByText.computeIfAbsent(line.text, ignored -> new ArrayList<>())
                    .add(line.timeMs);
        }
        Set<String> emitted = new HashSet<>();
        StringBuilder output = new StringBuilder();
        for (LyricLine line : document.getLines()) {
            if (isBlank(line)) continue;
            if (line.timeMs == null) {
                output.append(line.text).append('\n');
                continue;
            }
            if (!emitted.add(line.text)) continue;
            List<Long> times = new ArrayList<>(timestampsByText.getOrDefault(
                    line.text, Collections.singletonList(line.timeMs)));
            Collections.sort(times);
            for (Long time : times) output.append(LrcIo.formatTime(time));
            output.append(line.text).append('\n');
        }
        return output.toString();
    }

    public static String buildNetEaseLrc(LrcDocument document, Options options) {
        String separator = normalizeSeparator(options.multiLanguageSeparator);
        List<Cue> cues = collectCues(document);
        Map<String, List<Long>> timestampsByText = new LinkedHashMap<>();
        if (options.mergeDuplicateText) {
            for (Cue cue : cues) {
                timestampsByText.computeIfAbsent(cue.joinedText(separator),
                        ignored -> new ArrayList<>()).add(cue.startMs);
            }
        }
        Set<String> emitted = new HashSet<>();
        StringBuilder output = new StringBuilder();
        for (Cue cue : cues) {
            String text = cue.joinedText(separator);
            if (options.mergeDuplicateText) {
                if (!emitted.add(text)) continue;
                List<Long> times = new ArrayList<>(timestampsByText.get(text));
                Collections.sort(times);
                for (Long time : times) output.append(LrcIo.formatTime(time));
            } else {
                output.append(LrcIo.formatTime(cue.startMs));
            }
            output.append(text).append('\n');
        }
        for (LyricLine line : document.getLines()) {
            if (line.timeMs == null && !line.text.trim().isEmpty()) {
                output.append(line.text).append('\n');
            }
        }
        return output.toString();
    }

    public static String buildSrt(LrcDocument document) {
        List<Cue> cues = collectCues(document);
        StringBuilder output = new StringBuilder();
        int index = 1;
        for (Cue cue : cues) {
            long end = cueEnd(cues, cue);
            output.append(index++).append('\n')
                    .append(formatSrtTime(cue.startMs)).append(" --> ")
                    .append(formatSrtTime(end)).append('\n')
                    .append(String.join("\n", cue.normalizedLines()))
                    .append("\n\n");
        }
        return output.toString();
    }

    public static String buildAss(LrcDocument document, Options options) {
        List<Cue> cues = collectCues(document);
        int width = Math.max(320, options.subtitleWidth);
        int height = Math.max(240, options.subtitleHeight);
        int fontSize = Math.max(12, options.fontSize);
        String font = options.fontFamily == null || options.fontFamily.trim().isEmpty()
                ? "Microsoft YaHei" : options.fontFamily.replace(",", " ");
        String primary = toAssColor(options.textColor == null ? Color.WHITE : options.textColor);
        int outline = Math.max(0, options.outline);
        StringBuilder output = new StringBuilder();
        output.append("[Script Info]\n")
                .append("ScriptType: v4.00+\n")
                .append("PlayResX: ").append(width).append('\n')
                .append("PlayResY: ").append(height).append('\n')
                .append("WrapStyle: 2\n")
                .append("ScaledBorderAndShadow: yes\n")
                .append("YCbCr Matrix: TV.709\n\n")
                .append("[V4+ Styles]\n")
                .append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, ")
                .append("OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ")
                .append("ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, ")
                .append("Alignment, MarginL, MarginR, MarginV, Encoding\n")
                .append("Style: Default,").append(font).append(',').append(fontSize)
                .append(',').append(primary).append(',').append(primary)
                .append(",&H00101010,&H80000000,-1,0,0,0,100,100,0,0,1,")
                .append(outline).append(",1,2,40,40,48,1\n\n")
                .append("[Events]\n")
                .append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, ")
                .append("MarginV, Effect, Text\n");
        for (Cue cue : cues) {
            long end = cueEnd(cues, cue);
            List<String> escaped = new ArrayList<>();
            for (String line : cue.normalizedLines()) escaped.add(escapeAss(line));
            output.append("Dialogue: 0,")
                    .append(formatAssTime(cue.startMs)).append(',')
                    .append(formatAssTime(end))
                    .append(",Default,,0,0,0,,")
                    .append(String.join("\\N", escaped)).append('\n');
        }
        return output.toString();
    }

    private static List<Cue> collectCues(LrcDocument document) {
        TreeMap<Long, List<String>> grouped = new TreeMap<>();
        for (LyricLine line : document.getLines()) {
            if (line.timeMs == null || line.text == null || line.text.trim().isEmpty()) {
                continue;
            }
            grouped.computeIfAbsent(line.timeMs, ignored -> new ArrayList<>())
                    .add(line.text.trim());
        }
        List<Cue> cues = new ArrayList<>();
        for (Map.Entry<Long, List<String>> entry : grouped.entrySet()) {
            cues.add(new Cue(entry.getKey(), entry.getValue()));
        }
        return cues;
    }

    private static long cueEnd(List<Cue> cues, Cue cue) {
        int index = cues.indexOf(cue);
        long next = index >= 0 && index + 1 < cues.size()
                ? cues.get(index + 1).startMs : cue.startMs + 4000;
        return Math.max(cue.startMs + 500, next);
    }

    private static String formatSrtTime(long ms) {
        long value = Math.max(0, ms);
        return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d",
                value / 3_600_000, value / 60_000 % 60,
                value / 1000 % 60, value % 1000);
    }

    private static String formatAssTime(long ms) {
        long centis = Math.max(0, Math.round(ms / 10.0));
        long hours = centis / 360_000;
        long minutes = centis / 6000 % 60;
        long seconds = centis / 100 % 60;
        long cs = centis % 100;
        return String.format(Locale.ROOT, "%d:%02d:%02d.%02d",
                hours, minutes, seconds, cs);
    }

    private static String normalizeSeparator(String separator) {
        if (separator == null || separator.isEmpty()) return NARROW_SPACE;
        if (separator.startsWith("普通空格")) return " ";
        if (separator.startsWith("斜杠")) return " / ";
        return NARROW_SPACE;
    }

    private static String toAssColor(Color color) {
        return String.format(Locale.ROOT, "&H00%02X%02X%02X",
                Math.round(color.getBlue() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getRed() * 255));
    }

    private static String escapeAss(String text) {
        return text.replace("\\", "\\\\")
                .replace("{", "｛")
                .replace("}", "｝");
    }

    private static boolean isBlank(LyricLine line) {
        return line.timeMs == null && line.text.trim().isEmpty();
    }

    private static final class Cue {
        final long startMs;
        final List<String> lines;

        Cue(long startMs, List<String> lines) {
            this.startMs = startMs;
            this.lines = lines;
        }

        String joinedText(String separator) {
            return String.join(separator, normalizedLines());
        }

        List<String> normalizedLines() {
            List<String> result = new ArrayList<>();
            for (String line : lines) {
                String[] parts = line.replace("\r\n", "\n")
                        .replace('\r', '\n').replace("\\N", "\n").split("\n", -1);
                for (String part : parts) {
                    if (!part.trim().isEmpty()) result.add(part.trim());
                }
            }
            return result;
        }
    }
}
