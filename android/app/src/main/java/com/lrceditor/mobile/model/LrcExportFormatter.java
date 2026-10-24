package com.lrceditor.mobile.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LrcExportFormatter {
    private LrcExportFormatter() {
    }

    public static String buildText(LrcDocument document, boolean merge) {
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
            List<Long> times = timestampsByText.get(line.text);
            if (times == null) times = Collections.singletonList(line.timeMs);
            List<Long> sorted = new ArrayList<>(times);
            Collections.sort(sorted);
            for (Long time : sorted) {
                output.append(LrcIo.formatTime(time));
            }
            output.append(line.text).append('\n');
        }
        return output.toString();
    }

    private static boolean isBlank(LyricLine line) {
        return line.timeMs == null && line.text.trim().isEmpty();
    }
}
