package com.lrceditor.mobile.model;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LrcDocumentTest {

    @Test
    public void parsesFormatsAndSerializes() {
        List<LyricLine> lines = LrcIo.parse(
                "[00:01.20]第一句\n"
                        + "[00:02.345]第二句\n"
                        + "[00:03]第三句\n");
        assertEquals(3, lines.size());
        assertEquals(Long.valueOf(1200), lines.get(0).timeMs);
        assertEquals(Long.valueOf(2345), lines.get(1).timeMs);
        assertEquals(Long.valueOf(3000), lines.get(2).timeMs);
        assertEquals("第一句", lines.get(0).text);
        assertTrue(LrcIo.formatTime(2345).equals("[00:02.345]"));
    }

    @Test
    public void supportsUndoRedoAndEditing() {
        LrcDocument document = new LrcDocument();
        document.load("[00:01.000]A\n[00:02.000]B\n");
        document.editLine(0, 1500L, "A2");
        assertEquals(Long.valueOf(1500), document.getLines().get(0).timeMs);
        assertTrue(document.canUndo());
        document.undo();
        assertEquals(Long.valueOf(1000), document.getLines().get(0).timeMs);
        document.redo();
        assertEquals(Long.valueOf(1500), document.getLines().get(0).timeMs);
    }

    @Test
    public void movesSelectedContentUpWithoutMovingTimestamps() {
        LrcDocument document = new LrcDocument();
        document.load("[00:01.000]A\n[00:02.000]B\n[00:03.000]C\n");

        List<Integer> moved = document.moveContentMany(
                Arrays.asList(0, 1, 2), -1);

        assertEquals(Arrays.asList(0, 1, 2), moved);
        assertEquals(4, document.getLines().size());
        assertEquals(Long.valueOf(0), document.getLines().get(0).timeMs);
        assertEquals("A", document.getLines().get(0).text);
        assertEquals(Long.valueOf(1000), document.getLines().get(1).timeMs);
        assertEquals("B", document.getLines().get(1).text);
        assertEquals(Long.valueOf(2000), document.getLines().get(2).timeMs);
        assertEquals("C", document.getLines().get(2).text);
        assertEquals(Long.valueOf(3000), document.getLines().get(3).timeMs);
        assertEquals("", document.getLines().get(3).text);

        document.undo();
        assertEquals(3, document.getLines().size());
        assertEquals("A", document.getLines().get(0).text);
    }

    @Test
    public void movesSelectedContentDownAndAppendsOneSecondBoundary() {
        LrcDocument document = new LrcDocument();
        document.load("[00:01.000]A\n[00:02.000]B\n[00:03.000]C\n");

        List<Integer> moved = document.moveContentMany(
                Arrays.asList(0, 1, 2), 1);

        assertEquals(Arrays.asList(1, 2, 3), moved);
        assertEquals(4, document.getLines().size());
        assertEquals(Long.valueOf(1000), document.getLines().get(0).timeMs);
        assertEquals("", document.getLines().get(0).text);
        assertEquals(Long.valueOf(2000), document.getLines().get(1).timeMs);
        assertEquals("A", document.getLines().get(1).text);
        assertEquals(Long.valueOf(3000), document.getLines().get(2).timeMs);
        assertEquals("B", document.getLines().get(2).text);
        assertEquals(Long.valueOf(4000), document.getLines().get(3).timeMs);
        assertEquals("C", document.getLines().get(3).text);
    }

    @Test
    public void swapModePushesMiddleRowsInsteadOfOverwriting() {
        LrcDocument document = new LrcDocument();
        document.load("[00:01.000]A\n[00:02.000]B\n[00:03.000]C\n[00:04.000]D\n");

        document.moveContentMany(
                Arrays.asList(1, 2), -1, LrcDocument.MOVE_MODE_SWAP);

        assertEquals("B", document.getLines().get(0).text);
        assertEquals("C", document.getLines().get(1).text);
        assertEquals("A", document.getLines().get(2).text);
        assertEquals("D", document.getLines().get(3).text);
        assertEquals(Long.valueOf(1000), document.getLines().get(0).timeMs);
        assertEquals(Long.valueOf(4000), document.getLines().get(3).timeMs);

        document.undo();
        document.moveContentMany(
                Arrays.asList(1, 2), 1, LrcDocument.MOVE_MODE_SWAP);

        assertEquals("A", document.getLines().get(0).text);
        assertEquals("D", document.getLines().get(1).text);
        assertEquals("B", document.getLines().get(2).text);
        assertEquals("C", document.getLines().get(3).text);
    }

    @Test
    public void swapModeKeepsBoundaryTimestampRules() {
        LrcDocument document = new LrcDocument();
        document.load("[00:01.000]A\n[00:02.000]B\n[00:03.000]C\n");

        document.moveContentMany(
                Arrays.asList(0, 1, 2), -1, LrcDocument.MOVE_MODE_SWAP);
        assertEquals(Long.valueOf(0), document.getLines().get(0).timeMs);
        assertEquals("A", document.getLines().get(0).text);
        assertEquals("B", document.getLines().get(1).text);
        assertEquals("C", document.getLines().get(2).text);
        assertEquals("", document.getLines().get(3).text);

        document.undo();
        document.moveContentMany(
                Arrays.asList(0, 1, 2), 1, LrcDocument.MOVE_MODE_SWAP);
        assertEquals("", document.getLines().get(0).text);
        assertEquals("A", document.getLines().get(1).text);
        assertEquals("B", document.getLines().get(2).text);
        assertEquals("C", document.getLines().get(3).text);
        assertEquals(Long.valueOf(4000), document.getLines().get(3).timeMs);
    }

    @Test
    public void transferModeLeavesZeroTimestampBlankAtSource() {
        LrcDocument document = new LrcDocument();
        document.load("[00:01.000]A\n[00:02.000]B\n[00:03.000]C\n");

        document.moveContentMany(
                Arrays.asList(1), -1, LrcDocument.MOVE_MODE_TRANSFER);

        assertEquals(4, document.getLines().size());
        assertEquals(Long.valueOf(0), document.getLines().get(0).timeMs);
        assertEquals("A", document.getLines().get(0).text);
        assertEquals(Long.valueOf(1000), document.getLines().get(1).timeMs);
        assertEquals("B", document.getLines().get(1).text);
        assertEquals(Long.valueOf(0), document.getLines().get(2).timeMs);
        assertEquals("", document.getLines().get(2).text);
        assertEquals(Long.valueOf(3000), document.getLines().get(3).timeMs);
        assertEquals("C", document.getLines().get(3).text);
    }

    @Test
    public void transferModePushesFollowingRowsDown() {
        LrcDocument document = new LrcDocument();
        document.load("[00:01.000]A\n[00:02.000]B\n[00:03.000]C\n[00:04.000]D\n");

        document.moveContentMany(
                Arrays.asList(1), 1, LrcDocument.MOVE_MODE_TRANSFER);

        assertEquals(5, document.getLines().size());
        assertEquals("A", document.getLines().get(0).text);
        assertEquals(Long.valueOf(0), document.getLines().get(1).timeMs);
        assertEquals("", document.getLines().get(1).text);
        assertEquals(Long.valueOf(3000), document.getLines().get(2).timeMs);
        assertEquals("B", document.getLines().get(2).text);
        assertEquals("C", document.getLines().get(3).text);
        assertEquals(Long.valueOf(5000), document.getLines().get(4).timeMs);
        assertEquals("D", document.getLines().get(4).text);
    }

    @Test
    public void transferModeToEmptyTargetBehavesLikeOverwrite() {
        LrcDocument document = new LrcDocument();
        document.load("[00:01.000]A\n[00:02.000]\n[00:03.000]C\n");

        document.moveContentMany(
                Arrays.asList(2), -1, LrcDocument.MOVE_MODE_TRANSFER);

        assertEquals(3, document.getLines().size());
        assertEquals("A", document.getLines().get(0).text);
        assertEquals(Long.valueOf(2000), document.getLines().get(1).timeMs);
        assertEquals("C", document.getLines().get(1).text);
        assertEquals(Long.valueOf(3000), document.getLines().get(2).timeMs);
        assertEquals("", document.getLines().get(2).text);
    }

    @Test
    public void insertsDeletesAndFindsGroups() {
        LrcDocument document = new LrcDocument();
        document.load("[00:01.000]A\n[00:01.000]B\n[00:02.000]C\n");
        assertEquals(Arrays.asList(0, 1), document.collectSameTimestampGroup(1));
        document.addLine(1, new LyricLine(1000L, "A2"));
        assertEquals(4, document.getLines().size());
        document.removeMany(Arrays.asList(1, 2));
        assertEquals(2, document.getLines().size());
        assertEquals("C", document.getLines().get(1).text);
        assertEquals(Arrays.asList(1), document.findPlayingIndices(2500));
    }

    @Test
    public void readsBomAndGbkViaIo() throws Exception {
        byte[] utf8 = "\uFEFF[00:01.000]测试".getBytes(StandardCharsets.UTF_8);
        LrcIo.ReadResult result = LrcIo.read(new ByteArrayInputStream(utf8));
        assertEquals("UTF-8", result.encoding);
        assertTrue(result.hasBom);
        assertTrue(result.text.contains("测试"));

        byte[] gbk = "[00:01.000]测试".getBytes(Charset.forName("GBK"));
        result = LrcIo.read(new ByteArrayInputStream(gbk));
        assertEquals("GBK", result.encoding);
        assertFalse(result.hasBom);
        assertTrue(result.text.contains("测试"));
    }

    @Test
    public void writesUtf8WithOptionalBom() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        LrcIo.write(output, "[00:01.000]歌词", false);
        assertTrue(output.toString(StandardCharsets.UTF_8.name()).contains("歌词"));

        output.reset();
        LrcIo.write(output, "[00:01.000]歌词", true);
        byte[] bytes = output.toByteArray();
        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);
    }

    @Test
    public void parsesMultilinePasteWithInheritedTimestamps() {
        List<LyricLine> parsed = LrcPasteParser.parse(
                "[00:02.000]第一句\n"
                        + "第二句\n"
                        + "[00:03.000][00:01.000]重复句\n"
                        + "第四句",
                500L);
        assertEquals(5, parsed.size());
        assertEquals(Long.valueOf(2000), parsed.get(0).timeMs);
        assertEquals(Long.valueOf(2000), parsed.get(1).timeMs);
        assertEquals(Long.valueOf(1000), parsed.get(2).timeMs);
        assertEquals(Long.valueOf(3000), parsed.get(3).timeMs);
        assertEquals(Long.valueOf(3000), parsed.get(4).timeMs);
    }

    @Test
    public void fullyUntimedPasteDefaultsToZero() {
        List<LyricLine> parsed = LrcPasteParser.parse("A\nB\nC", 9999L);
        assertEquals(3, parsed.size());
        for (LyricLine line : parsed) {
            assertEquals(Long.valueOf(0), line.timeMs);
        }
    }

    @Test
    public void mergesSameTextAtFirstPositionAndSortsTimestamps() {
        LrcDocument document = new LrcDocument();
        document.load(
                "[00:02.000]重复\n"
                        + "[00:05.000]中间\n"
                        + "[00:01.000]重复\n");
        String merged = LrcExportFormatter.buildText(document, true);
        assertEquals("[00:01.000][00:02.000]重复\n[00:05.000]中间\n", merged);
    }
}
