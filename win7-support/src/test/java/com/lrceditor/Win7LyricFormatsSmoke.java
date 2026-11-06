package com.lrceditor;

import java.util.List;

public final class Win7LyricFormatsSmoke {

    private Win7LyricFormatsSmoke() {
    }

    public static void main(String[] args) {
        List<LyricLine> lrc = LyricImportParser.parse(
                "[00:01.000]中文\u2009English\n", "song.lrc");
        check(lrc.size() == 2, "NetEase import");
        check(lrc.get(0).timeMs == 1000L, "NetEase timestamp");

        List<LyricLine> srt = LyricImportParser.parse(
                "1\n00:00:01,250 --> 00:00:03,000\n中文字幕\nEnglish\n",
                "song.srt");
        check(srt.size() == 2, "SRT import");
        check(srt.get(0).timeMs == 1250L, "SRT timestamp");

        LrcDocument document = new LrcDocument();
        document.load(LyricImportParser.toLrcText(srt), null);
        LyricExportFormatter.Options options = new LyricExportFormatter.Options();
        options.format = LyricExportFormatter.Format.NETEASE_LRC;
        String netease = LyricExportFormatter.buildNetEaseLrc(document, options);
        check(netease.contains("中文字幕\u2009English"), "NetEase export");
        options.format = LyricExportFormatter.Format.SRT;
        String output = LyricExportFormatter.buildSrt(document);
        check(output.startsWith("1\n00:00:01,250 --> 00:00:05,250\n"),
                "SRT export");
        System.out.println("win7LyricFormatsSmoke=passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
