package com.lrceditor;

import java.awt.Color;
import java.io.File;
import java.util.Arrays;
import java.util.List;

public final class Win7VideoExportSmoke {

    private Win7VideoExportSmoke() {
    }

    public static void main(String[] args) throws Exception {
        File audio = args.length > 1 ? new File(args[1]) : null;
        List<LyricLine> lyrics = Arrays.asList(
                new LyricLine(500L, "第一句滚动歌词"),
                new LyricLine(2200L, "第二句保持精确同步"),
                new LyricLine(4200L, "第三句完成导出测试"));

        LyricVideoExporter.Settings settings = new LyricVideoExporter.Settings(
                new File(args[0]),
                audio,
                lyrics,
                640,
                360,
                30,
                LyricVideoExporter.LyricEffect.SCROLL_VERTICAL,
                LyricVideoExporter.BackgroundMode.BUILTIN_STATIC,
                "深海蓝",
                null,
                6.5,
                Color.WHITE,
                0,
                -1,
                -1,
                LyricVideoExporter.TextureMode.GLASS_DARK,
                35,
                12,
                35,
                audio == null ? 0 : 0.5,
                audio == null ? 0 : 5.0,
                1.0,
                null);
        LyricVideoExporter.export(settings, value -> { }, () -> false);
        File output = new File(args[0]);
        if (!output.isFile() || output.length() == 0) {
            throw new IllegalStateException("video was not generated");
        }
        System.out.println("videoExport=" + output.getAbsolutePath()
                + " bytes=" + output.length());
    }
}
