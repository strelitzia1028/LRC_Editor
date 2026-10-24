package com.lrceditor.mobile.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LrcPasteParser {
    private static final Pattern SINGLE_TAG = Pattern.compile(
            "^\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]$");

    private LrcPasteParser() {
    }

    public static List<LyricLine> parse(String text, Long previousTimeMs) {
        List<LyricLine> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return result;
        String[] rawLines = text.split("\\R", -1);
        boolean hasAnyTimestamp = false;
        for (String raw : rawLines) {
            if (!leadingTimestamps(raw).times.isEmpty()) {
                hasAnyTimestamp = true;
                break;
            }
        }
        Long previous = hasAnyTimestamp ? previousTimeMs : Long.valueOf(0);
        for (String raw : rawLines) {
            if (raw == null || raw.trim().isEmpty()) continue;
            ParsedLine parsed = leadingTimestamps(raw);
            if (parsed.text.trim().isEmpty()) continue;
            if (parsed.times.isEmpty()) {
                long time = previous == null ? 0L : previous;
                result.add(new LyricLine(time, parsed.text.trim()));
                previous = time;
            } else {
                List<Long> times = new ArrayList<>(parsed.times);
                Collections.sort(times);
                for (long time : times) {
                    result.add(new LyricLine(time, parsed.text.trim()));
                }
                previous = times.get(times.size() - 1);
            }
        }
        return result;
    }

    private static ParsedLine leadingTimestamps(String raw) {
        String text = raw == null ? "" : raw.trim();
        List<Long> times = new ArrayList<>();
        int offset = 0;
        while (offset < text.length() && text.charAt(offset) == '[') {
            int end = text.indexOf(']', offset);
            if (end < 0) break;
            Matcher matcher = SINGLE_TAG.matcher(text.substring(offset, end + 1));
            if (!matcher.matches()) break;
            times.add(LrcIo.parseTimeMs(matcher));
            offset = end + 1;
            while (offset < text.length() && Character.isWhitespace(text.charAt(offset))) {
                offset++;
            }
        }
        return new ParsedLine(times, times.isEmpty() ? text : text.substring(offset));
    }

    private static final class ParsedLine {
        final List<Long> times;
        final String text;

        ParsedLine(List<Long> times, String text) {
            this.times = times;
            this.text = text == null ? "" : text;
        }
    }
}
