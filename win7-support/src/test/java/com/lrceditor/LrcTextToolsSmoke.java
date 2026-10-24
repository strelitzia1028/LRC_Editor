package com.lrceditor;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public final class LrcTextToolsSmoke {
    private LrcTextToolsSmoke() {
    }

    public static void main(String[] args) {
        List<LyricLine> parsed = LrcPasteParser.parse(
                "[00:02.000]第一句\n"
                        + "第二句\n"
                        + "[00:03.000][00:01.000]重复句\n"
                        + "第四句",
                500L);
        check(parsed.size() == 5, "paste line count");
        check(parsed.get(0).timeMs == 2000L, "first timestamp");
        check(parsed.get(1).timeMs == 2000L, "inherit previous timestamp");
        check(parsed.get(2).timeMs == 1000L, "multiple timestamps sorted first");
        check(parsed.get(3).timeMs == 3000L, "multiple timestamps sorted second");
        check(parsed.get(4).timeMs == 3000L, "inherit last timestamp");

        List<LyricLine> zero = LrcPasteParser.parse("A\nB\nC", 9999L);
        check(zero.size() == 3, "zero timestamp line count");
        for (LyricLine line : zero) {
            check(line.timeMs == 0L, "no timestamp input defaults to zero");
        }

        LrcDocument document = new LrcDocument();
        document.load(
                "[00:02.000]重复\n"
                        + "[00:05.000]中间\n"
                        + "[00:01.000]重复\n", new File("merge-test.lrc"));
        String merged = LrcExportDialog.buildExportText(document, true);
        check(merged.equals("[00:01.000][00:02.000]重复\n[00:05.000]中间\n"),
                "merge keeps first position and sorts timestamps");

        LrcDocument noTimestamps = new LrcDocument();
        noTimestamps.load("A\nB\n", new File("untimed-test.lrc"));
        check(noTimestamps.serialize(false).equals("A\nB\n"),
                "untimed lines remain intact");

        System.out.println("textToolsSmoke=passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
