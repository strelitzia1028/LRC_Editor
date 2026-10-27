package com.lrceditor;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public final class LrcContentMoveSmoke {

    private LrcContentMoveSmoke() {
    }

    public static void main(String[] args) {
        LrcDocument document = new LrcDocument();
        document.load("[00:01.000]A\n[00:02.000]B\n[00:03.000]C\n",
                new File("content-move-test.lrc"));

        List<Integer> up = document.moveContentMany(
                Arrays.asList(0, 1, 2), -1);
        check(up.equals(Arrays.asList(0, 1, 2)), "up selection targets");
        check(document.getLines().size() == 4, "top boundary row");
        checkTimeText(document, 0, 0L, "A");
        checkTimeText(document, 1, 1000L, "B");
        checkTimeText(document, 2, 2000L, "C");
        checkTimeText(document, 3, 3000L, "");

        document.undo();
        List<Integer> down = document.moveContentMany(
                Arrays.asList(0, 1, 2), 1);
        check(down.equals(Arrays.asList(1, 2, 3)), "down selection targets");
        check(document.getLines().size() == 4, "bottom boundary row");
        checkTimeText(document, 0, 1000L, "");
        checkTimeText(document, 1, 2000L, "A");
        checkTimeText(document, 2, 3000L, "B");
        checkTimeText(document, 3, 4000L, "C");

        document.undo();
        checkTimeText(document, 0, 1000L, "A");
        checkTimeText(document, 2, 3000L, "C");

        document.load("[00:01.000]A\n[00:02.000]B\n[00:03.000]C\n[00:04.000]D\n",
                new File("content-transfer-test.lrc"));
        document.moveContentMany(Arrays.asList(1, 2), -1, true);
        checkTimeText(document, 0, 1000L, "B");
        checkTimeText(document, 1, 2000L, "C");
        checkTimeText(document, 2, 3000L, "A");
        checkTimeText(document, 3, 4000L, "D");

        document.undo();
        document.moveContentMany(Arrays.asList(1, 2), 1, true);
        checkTimeText(document, 0, 1000L, "A");
        checkTimeText(document, 1, 2000L, "D");
        checkTimeText(document, 2, 3000L, "B");
        checkTimeText(document, 3, 4000L, "C");

        document.undo();
        document.moveContentMany(Arrays.asList(0, 1, 2, 3), -1, true);
        check(document.getLines().size() == 5, "transfer top boundary row");
        checkTimeText(document, 0, 0L, "A");
        checkTimeText(document, 4, 4000L, "");
        document.undo();
        document.moveContentMany(Arrays.asList(0, 1, 2, 3), 1, true);
        check(document.getLines().size() == 5, "transfer bottom boundary row");
        checkTimeText(document, 0, 1000L, "");
        checkTimeText(document, 4, 5000L, "D");

        document.undo();
        document.moveContentMany(
                Arrays.asList(1), -1, LrcDocument.MOVE_MODE_TRANSFER);
        check(document.getLines().size() == 5, "transfer push row count");
        checkTimeText(document, 0, 0L, "A");
        checkTimeText(document, 1, 1000L, "B");
        checkTimeText(document, 2, 0L, "");
        checkTimeText(document, 3, 3000L, "C");
        checkTimeText(document, 4, 4000L, "D");

        document.undo();
        document.moveContentMany(
                Arrays.asList(1), 1, LrcDocument.MOVE_MODE_TRANSFER);
        check(document.getLines().size() == 5, "transfer push down row count");
        checkTimeText(document, 0, 1000L, "A");
        checkTimeText(document, 1, 0L, "");
        checkTimeText(document, 2, 3000L, "B");
        checkTimeText(document, 3, 4000L, "C");
        checkTimeText(document, 4, 5000L, "D");

        document.load("[00:01.000]A\n[00:02.000]\n[00:03.000]C\n",
                new File("content-empty-target-test.lrc"));
        document.moveContentMany(
                Arrays.asList(2), -1, LrcDocument.MOVE_MODE_TRANSFER);
        check(document.getLines().size() == 3, "empty target no added row");
        checkTimeText(document, 0, 1000L, "A");
        checkTimeText(document, 1, 2000L, "C");
        checkTimeText(document, 2, 3000L, "");
        System.out.println("contentMoveSmoke=passed");
    }

    private static void checkTimeText(
            LrcDocument document, int index, long timeMs, String text) {
        LyricLine line = document.getLines().get(index);
        check(line.timeMs != null && line.timeMs == timeMs,
                "timestamp at " + index);
        check(text.equals(line.text), "text at " + index);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
