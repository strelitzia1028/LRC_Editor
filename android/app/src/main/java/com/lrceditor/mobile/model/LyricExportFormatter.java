package com.lrceditor.mobile.model;

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

    public static final class Options {
        public LyricExportFormat format = LyricExportFormat.LRC;
        public boolean mergeDuplicateText;
        public String separator = "窄空格 U+2009";
        public int assFontSize = 48;
    }

    private LyricExportFormatter() {
    }

    public static String build(LrcDocument document, Options options) {
        Options actual = options == null ? new Options() : options;
        if (actual.format == LyricExportFormat.NETEASE_LRC) {
            return buildNetEaseLrc(document, actual);
        }
        if (actual.format == LyricExportFormat.SRT) return buildSrt(document);
        if (actual.format == LyricExportFormat.ASS) return buildAss(document, actual);
        return LrcExportFormatter.buildText(document, actual.mergeDuplicateText);
    }

    public static String buildNetEaseLrc(LrcDocument document, Options options) {
        String separator = normalizeSeparator(options.separator);
        List<Cue> cues = collectCues(document);
        StringBuilder output = new StringBuilder();
        if (options.mergeDuplicateText) {
            Map<String, List<Long>> timesByText = new LinkedHashMap<>();
            for (Cue cue : cues) {
                timesByText.computeIfAbsent(cue.joined(separator),
                        ignored -> new ArrayList<>()).add(cue.startMs);
            }
            Set<String> emitted = new HashSet<>();
            for (Cue cue : cues) {
                String text = cue.joined(separator);
                if (!emitted.add(text)) continue;
                List<Long> times = new ArrayList<>(timesByText.get(text));
                Collections.sort(times);
                for (Long time : times) output.append(LrcIo.formatTime(time));
                output.append(text).append('\n');
            }
        } else {
            for (Cue cue : cues) {
                output.append(LrcIo.formatTime(cue.startMs))
                        .append(cue.joined(separator)).append('\n');
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
                    .append(join("\n", cue.lines))
                    .append("\n\n");
        }
        return output.toString();
    }

    public static String buildAss(LrcDocument document, Options options) {
        List<Cue> cues = collectCues(document);
        int fontSize = Math.max(12, options.assFontSize);
        StringBuilder output = new StringBuilder();
        output.append("[Script Info]\n")
                .append("ScriptType: v4.00+\n")
                .append("PlayResX: 1920\nPlayResY: 1080\n")
                .append("WrapStyle: 2\nScaledBorderAndShadow: yes\n")
                .append("YCbCr Matrix: TV.709\n\n")
                .append("[V4+ Styles]\n")
                .append("Format: Name, Fontname, Fontsize, PrimaryColour, ")
                .append("SecondaryColour, OutlineColour, BackColour, Bold, Italic, ")
                .append("Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, ")
                .append("BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, ")
                .append("MarginV, Encoding\n")
                .append("Style: Default,Microsoft YaHei,").append(fontSize)
                .append(",&H00FFFFFF,&H00FFFFFF,&H00101010,&H80000000,")
                .append("-1,0,0,0,100,100,0,0,1,2,1,2,40,40,48,1\n\n")
                .append("[Events]\n")
                .append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, ")
                .append("MarginV, Effect, Text\n");
        for (Cue cue : cues) {
            List<String> escaped = new ArrayList<>();
            for (String line : cue.lines) escaped.add(escapeAss(line));
            output.append("Dialogue: 0,")
                    .append(formatAssTime(cue.startMs)).append(',')
                    .append(formatAssTime(cueEnd(cues, cue)))
                    .append(",Default,,0,0,0,,")
                    .append(join("\\N", escaped))
                    .append('\n');
        }
        return output.toString();
    }

    public static String fileName(String source, LyricExportFormat format) {
        return LrcFileNames.withExtension(source, format.extension);
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

    private static String normalizeSeparator(String separator) {
        if (separator == null) return NARROW_SPACE;
        if (separator.startsWith("普通空格")) return " ";
        if (separator.startsWith("斜杠")) return " / ";
        return NARROW_SPACE;
    }

    private static String formatSrtTime(long millis) {
        long value = Math.max(0, millis);
        return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d",
                value / 3_600_000, value / 60_000 % 60,
                value / 1000 % 60, value % 1000);
    }

    private static String formatAssTime(long millis) {
        long centis = Math.max(0, Math.round(millis / 10.0));
        return String.format(Locale.ROOT, "%d:%02d:%02d.%02d",
                centis / 360_000, centis / 6000 % 60,
                centis / 100 % 60, centis % 100);
    }

    private static String escapeAss(String text) {
        return text.replace("\\", "\\\\")
                .replace("{", "｛").replace("}", "｝");
    }

    private static final class Cue {
        final long startMs;
        final List<String> lines;

        Cue(long startMs, List<String> lines) {
            this.startMs = startMs;
            this.lines = lines;
        }

        String joined(String separator) {
            return LyricExportFormatter.join(separator, lines);
        }
    }

    private static String join(String separator, List<String> values) {
        StringBuilder output = new StringBuilder();
        for (String value : values) {
            if (output.length() > 0) output.append(separator);
            output.append(value);
        }
        return output.toString();
    }
}
