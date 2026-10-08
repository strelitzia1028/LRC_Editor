package com.lrceditor;

/** 一行歌词。timeMs == null 表示未打时间戳。 */
public class LyricLine {
    public Long   timeMs;
    public String text;

    public LyricLine(Long timeMs, String text) {
        this.timeMs = timeMs;
        this.text = text == null ? "" : text;
    }

    public LyricLine copy() { return new LyricLine(timeMs, text); }

    /** 序列化为一行 LRC 文本 */
    public String toLrc() {
        return (timeMs == null ? "" : LrcIo.formatTime(timeMs)) + text;
    }
}
