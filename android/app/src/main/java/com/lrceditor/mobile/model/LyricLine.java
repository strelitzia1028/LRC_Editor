package com.lrceditor.mobile.model;

import java.util.Objects;

public final class LyricLine {
    public Long timeMs;
    public String text;

    public LyricLine(Long timeMs, String text) {
        this.timeMs = timeMs;
        this.text = text == null ? "" : text;
    }

    public LyricLine copy() {
        return new LyricLine(timeMs, text);
    }

    public boolean isBlankSeparator() {
        return timeMs == null && text.trim().isEmpty();
    }

    public String toLrc() {
        if (timeMs == null) return text;
        return String.format(java.util.Locale.ROOT, "[%02d:%02d.%03d]%s",
                timeMs / 60_000, (timeMs / 1000) % 60, timeMs % 1000, text);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof LyricLine)) return false;
        LyricLine line = (LyricLine) other;
        return Objects.equals(timeMs, line.timeMs) && Objects.equals(text, line.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeMs, text);
    }
}
