package com.lrceditor.mobile.model;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LyricFormatTest {

    @Test
    public void importsNetEaseSrtAndAss() {
        List<LyricLine> netease = LyricImportParser.parse(
                "[00:01.000]中文\u2009English\n", "song.lrc");
        assertEquals(2, netease.size());
        assertEquals(Long.valueOf(1000), netease.get(0).timeMs);
        assertEquals("English", netease.get(1).text);

        List<LyricLine> srt = LyricImportParser.parse(
                "1\n00:00:01,250 --> 00:00:03,000\n"
                        + "<i>中文字幕</i>\nEnglish\n",
                "song.srt");
        assertEquals(2, srt.size());
        assertEquals(Long.valueOf(1250), srt.get(0).timeMs);
        assertEquals("中文字幕", srt.get(0).text);

        List<LyricLine> ass = LyricImportParser.parse(
                "[Events]\n"
                        + "Format: Layer, Start, End, Style, Name, MarginL, "
                        + "MarginR, MarginV, Effect, Text\n"
                        + "Dialogue: 0,0:00:04.50,0:00:07.00,Default,,0,0,0,,"
                        + "{\\i1}中文字幕\\NEnglish\n",
                "song.ass");
        assertEquals(2, ass.size());
        assertEquals(Long.valueOf(4500), ass.get(0).timeMs);
        assertEquals("中文字幕", ass.get(0).text);
    }

    @Test
    public void exportsAllRequestedFormats() {
        LrcDocument document = new LrcDocument();
        document.load(
                "[00:01.000]中文歌词\n"
                        + "[00:01.000]English line\n"
                        + "[00:03.250]下一句\n");
        LyricExportFormatter.Options options = new LyricExportFormatter.Options();

        options.format = LyricExportFormat.NETEASE_LRC;
        String netease = LyricExportFormatter.build(document, options);
        assertTrue(netease.contains("[00:01.000]中文歌词\u2009English line"));

        options.format = LyricExportFormat.SRT;
        String srt = LyricExportFormatter.build(document, options);
        assertTrue(srt.startsWith("1\n00:00:01,000 --> 00:00:03,250\n"));

        options.format = LyricExportFormat.ASS;
        String ass = LyricExportFormatter.build(document, options);
        assertTrue(ass.contains("Dialogue: 0,0:00:01.00,0:00:03.25"));
        assertTrue(ass.contains("中文歌词\\NEnglish line"));
    }

    @Test
    public void generalFileNamesKeepSingleExtension() {
        assertEquals("song.lrc", LrcFileNames.withExtension("song.lrc.txt", "lrc"));
        assertEquals("song.srt", LrcFileNames.withExtension("song.lrc", "srt"));
        assertEquals("song.ass", LrcFileNames.withExtension("song.lrc", "ass"));
        assertTrue(LrcFileNames.hasExtension("song.srt", "srt"));
        assertFalse(LrcFileNames.hasExtension("song.srt", "ass"));
    }
}
