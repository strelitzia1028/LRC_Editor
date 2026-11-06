package com.lrceditor.mobile.model;

public enum LyricExportFormat {
    LRC("LRC 歌词", "lrc", "text/x-lrc"),
    NETEASE_LRC("网易云多语 LRC", "lrc", "text/x-lrc"),
    SRT("SRT 字幕", "srt", "application/x-subrip"),
    ASS("ASS 字幕", "ass", "text/x-ssa");

    public final String label;
    public final String extension;
    public final String mimeType;

    LyricExportFormat(String label, String extension, String mimeType) {
        this.label = label;
        this.extension = extension;
        this.mimeType = mimeType;
    }

    @Override
    public String toString() {
        return label;
    }
}
