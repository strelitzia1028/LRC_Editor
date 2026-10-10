package com.lrceditor;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

public final class LyricVideoExporter {

    public enum LyricEffect {
        DIRECT_FLICKER("直接显示模式"),
        DIRECT_FADE("单句渐显渐隐模式"),
        SCROLL_VERTICAL("上下滚动模式"),
        SCROLL_HORIZONTAL("左右滚动模式");

        private final String label;

        LyricEffect(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum BackgroundMode {
        BUILTIN_STATIC,
        BUILTIN_DYNAMIC,
        CUSTOM_IMAGE,
        CUSTOM_VIDEO,
        ALBUM_COVER
    }

    public enum TextureMode {
        NONE,
        GLASS,
        DARK_OVERLAY,
        GLASS_DARK
    }

    public record Settings(
            File output,
            File audio,
            List<LyricLine> lyrics,
            int width,
            int height,
            int fps,
            LyricEffect lyricEffect,
            BackgroundMode backgroundMode,
            String backgroundPreset,
            File customBackground,
            double durationSeconds,
            Color textColor,
            int fontSize,
            int lineSpacing,
            int nonCurrentFontReduction,
            TextureMode textureMode,
            int blurPercent,
            int grainPercent,
            int darkOpacityPercent,
            double trimStartSeconds,
            double trimEndSeconds,
            double scrollSpeedMultiplier,
            String backgroundFitMode) {

        public Settings(
                File output,
                File audio,
                List<LyricLine> lyrics,
                int width,
                int height,
                int fps,
                LyricEffect lyricEffect,
                BackgroundMode backgroundMode,
                String backgroundPreset,
                File customBackground,
                double durationSeconds,
                Color textColor) {
            this(output, audio, lyrics, width, height, fps, lyricEffect,
                    backgroundMode, backgroundPreset, customBackground,
                    durationSeconds, textColor, 0, -1, -1,
                    TextureMode.NONE, 0, 0, 0, 0, 0, 0, null);
        }

        public Settings(
                File output,
                File audio,
                List<LyricLine> lyrics,
                int width,
                int height,
                int fps,
                LyricEffect lyricEffect,
                BackgroundMode backgroundMode,
                String backgroundPreset,
                File customBackground,
                double durationSeconds,
                Color textColor,
                int fontSize,
                int lineSpacing) {
            this(output, audio, lyrics, width, height, fps, lyricEffect,
                    backgroundMode, backgroundPreset, customBackground,
                    durationSeconds, textColor, fontSize, lineSpacing, -1,
                    TextureMode.NONE, 0, 0, 0, 0, 0, 0, null);
        }

        public Settings(
                File output,
                File audio,
                List<LyricLine> lyrics,
                int width,
                int height,
                int fps,
                LyricEffect lyricEffect,
                BackgroundMode backgroundMode,
                String backgroundPreset,
                File customBackground,
                double durationSeconds,
                Color textColor,
                int fontSize,
                int lineSpacing,
                int nonCurrentFontReduction) {
            this(output, audio, lyrics, width, height, fps, lyricEffect,
                    backgroundMode, backgroundPreset, customBackground,
                    durationSeconds, textColor, fontSize, lineSpacing,
                    nonCurrentFontReduction, TextureMode.NONE, 0, 0, 0, 0, 0, 0, null);
        }

        public Settings(
                File output,
                File audio,
                List<LyricLine> lyrics,
                int width,
                int height,
                int fps,
                LyricEffect lyricEffect,
                BackgroundMode backgroundMode,
                String backgroundPreset,
                File customBackground,
                double durationSeconds,
                Color textColor,
                int fontSize,
                int lineSpacing,
                int nonCurrentFontReduction,
                TextureMode textureMode,
                int blurPercent,
                int grainPercent,
                int darkOpacityPercent) {
            this(output, audio, lyrics, width, height, fps, lyricEffect,
                    backgroundMode, backgroundPreset, customBackground,
                    durationSeconds, textColor, fontSize, lineSpacing,
                    nonCurrentFontReduction, textureMode, blurPercent,
                    grainPercent, darkOpacityPercent, 0, 0, 0, null);
        }

        public Settings(
                File output,
                File audio,
                List<LyricLine> lyrics,
                int width,
                int height,
                int fps,
                LyricEffect lyricEffect,
                BackgroundMode backgroundMode,
                String backgroundPreset,
                File customBackground,
                double durationSeconds,
                Color textColor,
                int fontSize,
                int lineSpacing,
                int nonCurrentFontReduction,
                TextureMode textureMode,
                int blurPercent,
                int grainPercent,
                int darkOpacityPercent,
                double trimStartSeconds,
                double trimEndSeconds) {
            this(output, audio, lyrics, width, height, fps, lyricEffect,
                    backgroundMode, backgroundPreset, customBackground,
                    durationSeconds, textColor, fontSize, lineSpacing,
                    nonCurrentFontReduction, textureMode, blurPercent,
                    grainPercent, darkOpacityPercent, trimStartSeconds,
                    trimEndSeconds, 0, null);
        }

        public Settings(
                File output,
                File audio,
                List<LyricLine> lyrics,
                int width,
                int height,
                int fps,
                LyricEffect lyricEffect,
                BackgroundMode backgroundMode,
                String backgroundPreset,
                File customBackground,
                double durationSeconds,
                Color textColor,
                int fontSize,
                int lineSpacing,
                int nonCurrentFontReduction,
                TextureMode textureMode,
                int blurPercent,
                int grainPercent,
                int darkOpacityPercent,
                double trimStartSeconds,
                double trimEndSeconds,
                double scrollSpeedMultiplier) {
            this(output, audio, lyrics, width, height, fps, lyricEffect,
                    backgroundMode, backgroundPreset, customBackground,
                    durationSeconds, textColor, fontSize, lineSpacing,
                    nonCurrentFontReduction, textureMode, blurPercent,
                    grainPercent, darkOpacityPercent, trimStartSeconds,
                    trimEndSeconds, scrollSpeedMultiplier, null);
        }
    }

    private LyricVideoExporter() {
    }

    public static Path findFfmpeg() {
        String explicit = System.getProperty("lrceditor.ffmpeg", "");
        if (!explicit.isBlank()) {
            Path path = Path.of(explicit);
            if (Files.isRegularFile(path)) return path;
        }

        String appPath = System.getProperty("jpackage.app-path", "");
        if (!appPath.isBlank()) {
            Path app = Path.of(appPath).toAbsolutePath().getParent();
            Path packaged = app.resolve("app").resolve("ffmpeg").resolve("ffmpeg.exe");
            if (Files.isRegularFile(packaged)) return packaged;
            Path root = app.resolve("ffmpeg.exe");
            if (Files.isRegularFile(root)) return root;
        }

        Path cwd = Path.of("").toAbsolutePath();
        List<Path> candidates = List.of(
                cwd.resolve("ffmpeg").resolve("ffmpeg.exe"),
                cwd.resolve("target").resolve("package-build")
                        .resolve("ffmpeg").resolve("ffmpeg.exe"),
                cwd.resolve("target").resolve("ffmpeg-dist")
                        .resolve("ffmpeg-master-latest-win64-gpl")
                        .resolve("bin").resolve("ffmpeg.exe"));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }

    public static double estimateDurationSeconds(
            List<LyricLine> lyrics, double audioDurationSeconds) {
        if (audioDurationSeconds > 0) return audioDurationSeconds;

        long lastMs = 0;
        for (LyricLine line : lyrics) {
            if (line.timeMs != null && !line.text.isBlank()) {
                lastMs = Math.max(lastMs, line.timeMs);
            }
        }
        return Math.max(3.0, lastMs / 1000.0 + 3.0);
    }

    public static void export(
            Settings settings,
            DoubleConsumer progress,
            BooleanSupplier cancelled) throws Exception {
        Path ffmpeg = findFfmpeg();
        if (ffmpeg == null) {
            throw new IOException("未找到 FFmpeg。请重新安装完整视频版本。");
        }

        double fullDuration = estimateDurationSeconds(
                settings.lyrics(), settings.durationSeconds());
        if (fullDuration <= 0.1) {
            throw new IOException("视频时长无效。");
        }
        double trimStart = Math.max(0, Math.min(settings.trimStartSeconds(), fullDuration));
        double trimEnd = settings.trimEndSeconds() > trimStart
                ? Math.min(settings.trimEndSeconds(), fullDuration)
                : fullDuration;
        double duration = trimEnd - trimStart;
        if (duration <= 0.1) {
            throw new IOException("截取区间无效。");
        }

        File output = settings.output().getAbsoluteFile();
        File parent = output.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());

        Path tempDir = Files.createTempDirectory("lrc-video-");
        Path tempOutput = null;
        try {
            Path assFile = tempDir.resolve("lyrics.ass");
            generateAss(settings, trimStart, duration, assFile);

            Path backgroundImage = null;
            if (settings.backgroundMode() == BackgroundMode.BUILTIN_STATIC
                    || settings.backgroundMode() == BackgroundMode.BUILTIN_DYNAMIC) {
                backgroundImage = tempDir.resolve("background.png");
                BufferedImage image = createBuiltinBackground(
                        settings.backgroundPreset(), settings.width(), settings.height());
                ImageIO.write(image, "png", backgroundImage.toFile());
            }

            Path outputParent = output.toPath().toAbsolutePath().getParent();
            if (outputParent == null) outputParent = tempDir;
            tempOutput = Files.createTempFile(
                    outputParent, ".lrc-video-", ".partial.mp4");
            Files.deleteIfExists(tempOutput);
            List<String> command = buildCommand(
                    ffmpeg, settings, tempOutput.toFile(), duration,
                    trimStart, backgroundImage);
            runFfmpeg(command, tempDir, duration, progress, cancelled);
            Files.move(tempOutput, output.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            tempOutput = null;
        } finally {
            if (tempOutput != null) {
                Files.deleteIfExists(tempOutput);
            }
            deleteRecursively(tempDir);
        }
    }

    private static List<String> buildCommand(
            Path ffmpeg,
            Settings settings,
            File output,
            double duration,
            double trimStart,
            Path backgroundImage) {
        List<String> command = new ArrayList<>();
        command.add(ffmpeg.toString());
        command.add("-y");
        command.add("-hide_banner");
        command.add("-nostdin");
        command.add("-loglevel");
        command.add("warning");
        command.add("-progress");
        command.add("pipe:1");
        command.add("-nostats");

        boolean customVideo = settings.backgroundMode() == BackgroundMode.CUSTOM_VIDEO;
        boolean customImage = settings.backgroundMode() == BackgroundMode.CUSTOM_IMAGE;
        boolean albumCover = settings.backgroundMode() == BackgroundMode.ALBUM_COVER;
        if (customVideo) {
            command.add("-stream_loop");
            command.add("-1");
            command.add("-i");
            command.add(settings.customBackground().getAbsolutePath());
        } else if (customImage || albumCover) {
            command.add("-loop");
            command.add("1");
            command.add("-framerate");
            command.add(Integer.toString(settings.fps()));
            command.add("-i");
            command.add(settings.customBackground().getAbsolutePath());
        } else {
            command.add("-loop");
            command.add("1");
            command.add("-framerate");
            command.add(Integer.toString(settings.fps()));
            command.add("-i");
            command.add(backgroundImage.toString());
        }

        if (settings.audio() != null && settings.audio().isFile()) {
            if (trimStart > 0) {
                command.add("-ss");
                command.add(formatSeconds(trimStart));
            }
            command.add("-i");
            command.add(settings.audio().getAbsolutePath());
        } else {
            command.add("-f");
            command.add("lavfi");
            command.add("-t");
            command.add(formatSeconds(duration));
            command.add("-i");
            command.add("anullsrc=channel_layout=stereo:sample_rate=48000");
        }

        String videoFilter = buildVideoFilter(settings, customVideo);
        command.add("-vf");
        command.add(videoFilter);
        command.add("-map");
        command.add("0:v:0");
        command.add("-map");
        command.add("1:a:0");
        command.add("-t");
        command.add(formatSeconds(duration));
        command.add("-r");
        command.add(Integer.toString(settings.fps()));
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("medium");
        command.add("-crf");
        command.add("19");
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("192k");
        command.add("-movflags");
        command.add("+faststart");
        command.add("-shortest");
        command.add(output.getAbsolutePath());
        return command;
    }

    private static String buildVideoFilter(Settings settings, boolean customVideo) {
        int width = settings.width();
        int height = settings.height();
        int fps = settings.fps();
        String base;
        if (settings.backgroundMode() == BackgroundMode.ALBUM_COVER) {
            String fitMode = settings.backgroundFitMode();
            if ("合适".equals(fitMode)) {
                base = "scale=" + width + ":" + height
                       + ":force_original_aspect_ratio=decrease,"
                       + "pad=" + width + ":" + height
                       + ":(ow-iw)/2:(oh-ih)/2:black";
            } else if ("默认".equals(fitMode)) {
                base = "scale=" + width + ":" + height
                       + ":force_original_aspect_ratio=increase,"
                       + "crop=" + width + ":" + height;
            } else {
                base = "scale=" + width + ":" + height
                       + ":force_original_aspect_ratio=increase,"
                       + "crop=" + width + ":" + height;
            }
        } else {
            base = "scale=" + width + ":" + height
                   + ":force_original_aspect_ratio=increase,"
                   + "crop=" + width + ":" + height;
        }
        base += ",setsar=1,fps=" + fps;

        String effect = "";
        if (!customVideo && settings.backgroundMode() == BackgroundMode.BUILTIN_DYNAMIC) {
            if ("缓慢缩放".equals(settings.backgroundPreset())) {
                effect = ",zoompan=z='min(zoom+0.0004,1.10)'"
                         + ":x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'"
                         + ":d=1:s=" + width + "x" + height + ":fps=" + fps;
            } else if ("蓝色呼吸".equals(settings.backgroundPreset())) {
                effect = ",hue=H='2*PI*t/9':s='1.02+0.05*sin(2*PI*t/7)'";
            } else {
                effect = ",zoompan=z='1.02+0.025*sin(2*PI*t/8)'"
                         + ":x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'"
                         + ":d=1:s=" + width + "x" + height + ":fps=" + fps;
            }
        }
        return base + effect + buildTextureFilter(settings)
               + ",ass=lyrics.ass,format=yuv420p";
    }

    private static String buildTextureFilter(Settings settings) {
        StringBuilder filter = new StringBuilder();
        if (settings.textureMode() == TextureMode.GLASS
                || settings.textureMode() == TextureMode.GLASS_DARK) {
            if (settings.blurPercent() > 0) {
                double sigma = settings.blurPercent() / 100.0 * 25.0;
                filter.append(",gblur=sigma=")
                        .append(String.format(Locale.ROOT, "%.2f", sigma));
            }
            if (settings.grainPercent() > 0) {
                double amount = settings.grainPercent() / 100.0 * 1.4;
                filter.append(",unsharp=5:5:")
                        .append(String.format(Locale.ROOT, "%.3f", amount))
                        .append(":5:5:0");
            }
        }
        if ((settings.textureMode() == TextureMode.DARK_OVERLAY
                || settings.textureMode() == TextureMode.GLASS_DARK)
                && settings.darkOpacityPercent() > 0) {
            double factor = 1.0 - settings.darkOpacityPercent() / 100.0;
            String level = String.format(Locale.ROOT, "%.4f", factor);
            filter.append(",format=gbrp,colorlevels=romax=").append(level)
                    .append(":gomax=").append(level)
                    .append(":bomax=").append(level)
                    .append(",format=yuv420p");
        }
        return filter.toString();
    }

    private static void runFfmpeg(
            List<String> command,
            Path workingDirectory,
            double duration,
            DoubleConsumer progress,
            BooleanSupplier cancelled) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();

        List<String> tail = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (cancelled.getAsBoolean()) {
                    process.destroyForcibly();
                    throw new InterruptedException("视频导出已取消");
                }
                if (line.startsWith("out_time_us=") || line.startsWith("out_time_ms=")) {
                    try {
                        long micros = Long.parseLong(line.substring(line.indexOf('=') + 1));
                        double seconds = micros / 1_000_000.0;
                        progress.accept(Math.max(0, Math.min(0.99, seconds / duration)));
                    } catch (NumberFormatException ignored) {
                        // Ignore malformed progress events.
                    }
                }
                tail.add(line);
                if (tail.size() > 24) tail.remove(0);
            }
        }

        int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException("FFmpeg 导出失败：\n" + String.join("\n", tail));
        }
        progress.accept(1.0);
    }

    private static void generateAss(
            Settings settings,
            double trimStartSeconds,
            double durationSeconds,
            Path output) throws IOException {
        long trimStartMs = Math.round(trimStartSeconds * 1000);
        long trimEndMs = Math.round((trimStartSeconds + durationSeconds) * 1000);
        List<Entry> entries = new ArrayList<>();
        for (Entry entry : buildEntries(settings.lyrics())) {
            if (entry.timeMs >= trimStartMs && entry.timeMs <= trimEndMs) {
                entries.add(new Entry(entry.timeMs - trimStartMs, entry.text));
            }
        }
        if (entries.isEmpty()) {
            throw new IOException("没有可导出的带时间戳歌词。");
        }

        int width = settings.width();
        int height = settings.height();
        int fontSize = Math.max(24, (int) Math.round(height * 0.052));
        int outline = Math.max(2, (int) Math.round(height / 360.0));
        int margin = Math.max(40, (int) Math.round(width * 0.06));

        StringBuilder ass = new StringBuilder();
        ass.append("[Script Info]\n");
        ass.append("ScriptType: v4.00+\n");
        ass.append("PlayResX: ").append(width).append('\n');
        ass.append("PlayResY: ").append(height).append('\n');
        ass.append("WrapStyle: 2\n");
        ass.append("ScaledBorderAndShadow: yes\n");
        ass.append("YCbCr Matrix: TV.709\n\n");
        ass.append("[V4+ Styles]\n");
        ass.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, ")
                .append("OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ")
                .append("ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, ")
                .append("Alignment, MarginL, MarginR, MarginV, Encoding\n");
        ass.append("Style: Lyric,Microsoft YaHei,").append(fontSize)
                .append(',').append(toAssColor(settings.textColor()))
                .append(',').append(toAssColor(settings.textColor()))
                .append(",&H00101010,&H80000000,")
                .append("-1,0,0,0,100,100,0,0,1,").append(outline).append(",1,5,")
                .append(margin).append(',').append(margin).append(',')
                .append(Math.max(30, margin / 2)).append(",1\n\n");
        ass.append("[Events]\n");
        ass.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, ")
                .append("Effect, Text\n");

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            double start = entry.timeMs / 1000.0;
            double next = i + 1 < entries.size()
                    ? entries.get(i + 1).timeMs / 1000.0
                    : durationSeconds;
            double end = Math.max(start + 0.35, Math.min(durationSeconds, next));
            String text = escapeAss(entry.text);
            if (settings.lyricEffect() == LyricEffect.DIRECT_FLICKER) {
                appendDirectEvent(ass, start, end, text, settings, false);
            } else if (settings.lyricEffect() == LyricEffect.SCROLL_VERTICAL) {
                appendVerticalThreeLineEvents(
                        ass, entries, i, start, end, durationSeconds, settings);
            } else if (settings.lyricEffect() == LyricEffect.SCROLL_HORIZONTAL) {
                appendHorizontalThreeLineEvents(
                        ass, entries, i, start, end, durationSeconds, settings);
            } else {
                appendDirectEvent(ass, start, end, text, settings, true);
            }
        }

        Files.writeString(output, ass.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void appendDirectEvent(
            StringBuilder ass,
            double start,
            double end,
            String text,
            Settings settings,
            boolean fade) {
        LayoutMetrics metrics = layoutMetrics(settings, "", text, "");
        String tags = "{\\an5\\pos(" + (settings.width() / 2) + ","
                      + Math.round(metrics.currentCenter) + ")\\fs" + metrics.currentFontSize
                      + "\\1c" + assColorTag(settings.textColor())
                      + (fade ? "\\fad(260,260)" : "") + "}";
        appendDialogue(ass, start, end, text, tags);
    }

    private static LayoutMetrics layoutMetrics(
            Settings settings, String previous, String current, String next) {
        int height = settings.height();
        int previousLines = lineCount(previous);
        int currentLines = lineCount(current);
        int nextLines = lineCount(next);
        int maxLines = Math.max(1, Math.max(previousLines, Math.max(currentLines, nextLines)));

        int currentFont = settings.fontSize() > 0
                ? settings.fontSize()
                : Math.max(22, (int) Math.round(height * 0.052));
        int spacing = settings.lineSpacing() >= 0
                ? settings.lineSpacing()
                : Math.max(2, Math.round(currentFont * 0.12f));
        int nonCurrentFont = reducedFont(currentFont, settings.nonCurrentFontReduction());

        double availableHeight = height * 0.78;
        double totalHeight;
        do {
            double currentLineHeight = currentFont * 1.35 + spacing;
            double nonCurrentLineHeight = nonCurrentFont * 1.35 + spacing;
            double gap = Math.max(18, currentFont * 0.55);
            totalHeight = previousLines * nonCurrentLineHeight
                          + currentLines * currentLineHeight
                          + nextLines * nonCurrentLineHeight
                          + gap * 2;
            if (totalHeight <= availableHeight) break;
            currentFont = Math.max(12, currentFont - 2);
            nonCurrentFont = reducedFont(currentFont, settings.nonCurrentFontReduction());
            if (settings.lineSpacing() < 0) {
                spacing = Math.max(1, Math.round(currentFont * 0.12f));
            }
        } while (currentFont > 12 && maxLines > 0);

        double currentLineHeight = currentFont * 1.35 + spacing;
        double nonCurrentLineHeight = nonCurrentFont * 1.35 + spacing;
        double gap = Math.max(18, currentFont * 0.55);
        double previousHeight = previousLines * nonCurrentLineHeight;
        double currentHeight = currentLines * currentLineHeight;
        double nextHeight = nextLines * nonCurrentLineHeight;
        double total = previousHeight + currentHeight + nextHeight + gap * 2;
        double top = Math.max(height * 0.08, (height - total) / 2.0);
        double previousCenter = top + previousHeight / 2.0;
        double currentCenter = top + previousHeight + gap + currentHeight / 2.0;
        double nextCenter = top + previousHeight + gap + currentHeight + gap
                            + nextHeight / 2.0;
        return new LayoutMetrics(
                currentFont, nonCurrentFont, spacing,
                currentLineHeight, nonCurrentLineHeight, gap,
                previousCenter, currentCenter, nextCenter,
                currentCenter + currentHeight + gap);
    }

    private static int reducedFont(int currentFont, int manualReduction) {
        int maxReduction = Math.max(0, currentFont - 1);
        int reduction = manualReduction >= 0
                ? Math.min(manualReduction, maxReduction)
                : Math.max(1, Math.min(maxReduction,
                        (int) Math.round(currentFont * 0.18)));
        return Math.max(1, currentFont - reduction);
    }

    private static int lineCount(String text) {
        if (text == null || text.isBlank()) return 0;
        int count = 1;
        int index = -1;
        while ((index = text.indexOf("\\N", index + 1)) >= 0) count++;
        return count;
    }

    private static void appendVerticalThreeLineEvents(
            StringBuilder ass,
            List<Entry> entries,
            int index,
            double start,
            double end,
            double totalDuration,
            Settings settings) {
        int width = settings.width();
        String previous = adjacentText(entries, index - 1);
        String current = escapeAss(entries.get(index).text);
        String next = adjacentText(entries, index + 1);
        String afterNext = adjacentText(entries, index + 2);
        LayoutMetrics from = layoutMetrics(settings, previous, current, next);
        LayoutMetrics to = layoutMetrics(settings, current, next, afterNext);
        double available = Math.max(0.35, end - start);
        double baseMoveMs = Math.max(220, Math.min(600,
                available * 1000 * 0.30));
        double speed = settings.scrollSpeedMultiplier() > 0
                ? settings.scrollSpeedMultiplier() : 1.0;
        long moveMs = Math.max(100, Math.min(1600,
                Math.round(baseMoveMs / speed)));
        double transitionStart = Math.max(start, end - moveMs / 1000.0);

        String currentColor = assColorTag(settings.textColor());
        String mutedColor = "&HB8A394&";
        if (!previous.isEmpty()) {
            appendDialogue(ass, start, transitionStart, previous,
                    "{\\an5\\pos(" + (width / 2) + ","
                            + Math.round(from.previousCenter) + ")\\fs"
                            + from.nonCurrentFontSize + "\\1c" + mutedColor + "}");
        }

        appendDialogue(ass, start, transitionStart, current,
                "{\\an5\\pos(" + (width / 2) + ","
                        + Math.round(from.currentCenter) + ")\\fs"
                        + from.currentFontSize + "\\1c" + currentColor + "}");

        if (!next.isEmpty()) {
            appendDialogue(ass, start, transitionStart, next,
                    "{\\an5\\pos(" + (width / 2) + ","
                            + Math.round(from.nextCenter) + ")\\fs"
                            + from.nonCurrentFontSize + "\\1c" + mutedColor + "}");

            double midTime = transitionStart + moveMs / 2000.0;
            long halfMs = Math.max(100, moveMs / 2);
            double currentMid = (from.currentCenter + to.previousCenter) / 2.0;
            double nextMid = (from.nextCenter + to.currentCenter) / 2.0;
            double previousExit = from.previousCenter
                                  - from.nonCurrentLineHeight * 0.75;

            if (!previous.isEmpty()) {
                appendDialogue(ass, transitionStart, midTime, previous,
                        "{\\move(" + (width / 2) + ","
                                + Math.round(from.previousCenter)
                                + "," + (width / 2) + "," + Math.round(previousExit)
                                + ",0," + halfMs + ")\\fs" + from.nonCurrentFontSize
                                + "\\1c" + mutedColor + "}");
            }
            appendDialogue(ass, transitionStart, midTime, current,
                    "{\\move(" + (width / 2) + "," + Math.round(from.currentCenter)
                            + "," + (width / 2) + "," + Math.round(currentMid)
                            + ",0," + halfMs + ")\\fs" + from.currentFontSize
                            + "\\1c" + currentColor + "}");
            appendDialogue(ass, transitionStart, midTime, next,
                    "{\\move(" + (width / 2) + "," + Math.round(from.nextCenter)
                            + "," + (width / 2) + "," + Math.round(nextMid)
                            + ",0," + halfMs + ")\\fs" + from.nonCurrentFontSize
                            + "\\1c" + mutedColor + "}");

            appendDialogue(ass, midTime, end, current,
                    "{\\move(" + (width / 2) + "," + Math.round(currentMid)
                            + "," + (width / 2) + "," + Math.round(to.previousCenter)
                            + ",0," + halfMs + ")\\fs" + from.currentFontSize
                            + "\\1c" + currentColor
                            + "\\t(0," + halfMs + ",\\fs" + to.nonCurrentFontSize
                            + ",\\1c" + mutedColor + ")}");
            appendDialogue(ass, midTime, end, next,
                    "{\\move(" + (width / 2) + "," + Math.round(nextMid)
                            + "," + (width / 2) + "," + Math.round(to.currentCenter)
                            + ",0," + halfMs + ")\\fs" + from.nonCurrentFontSize
                            + "\\1c" + mutedColor
                            + "\\t(0," + halfMs + ",\\fs" + to.currentFontSize
                            + ",\\1c" + currentColor + ")}");
            if (!afterNext.isEmpty()) {
                double enterY = from.nextCenter
                                + from.nonCurrentLineHeight * Math.max(1, lineCount(next))
                                + from.gap;
                appendDialogue(ass, midTime, end, afterNext,
                        "{\\move(" + (width / 2) + "," + Math.round(enterY)
                                + "," + (width / 2) + "," + Math.round(to.nextCenter)
                                + ",0," + halfMs + ")\\fs" + to.nonCurrentFontSize
                                + "\\1c" + mutedColor + "}");
            }
        }
    }

    private static void appendHorizontalThreeLineEvents(
            StringBuilder ass,
            List<Entry> entries,
            int index,
            double start,
            double end,
            double totalDuration,
            Settings settings) {
        int width = settings.width();
        int height = settings.height();
        String previous = adjacentText(entries, index - 1);
        String current = escapeAss(entries.get(index).text);
        String next = adjacentText(entries, index + 1);

        LayoutMetrics metrics = layoutMetrics(settings, previous, current, next);
        double centerY = height / 2.0;
        double leftX = width * 0.18;
        double rightX = width * 0.82;
        double available = Math.max(0.35, end - start);
        long rollMs = Math.max(140, Math.min(420, Math.round(available * 1000 * 0.38)));
        double rollSeconds = rollMs / 1000.0;
        double rollEnd = Math.min(end, start + rollSeconds);
        String currentColor = assColorTag(settings.textColor());
        String mutedColor = "&HB8A394&";
        int currentFont = fitHorizontalFont(
                current, metrics.currentFontSize, width * 0.30);
        int previousFont = fitHorizontalFont(
                previous, metrics.nonCurrentFontSize, width * 0.30);
        int nextFont = fitHorizontalFont(
                next, metrics.nonCurrentFontSize, width * 0.30);
        String currentFontTag = "\\fs" + currentFont;
        String previousFontTag = "\\fs" + previousFont;
        String nextFontTag = "\\fs" + nextFont;

        if (!previous.isEmpty()) {
            appendDialogue(ass, start, end, previous,
                    "{\\an5\\pos(" + Math.round(leftX) + "," + Math.round(centerY)
                            + ")" + previousFontTag + "\\1c" + mutedColor + "}");
        }
        appendDialogue(ass, start, rollEnd, current,
                "{\\move(" + Math.round(width * 1.05) + "," + Math.round(centerY)
                        + "," + (width / 2) + "," + Math.round(centerY)
                        + ",0," + rollMs + ")" + currentFontTag + "\\1c" + mutedColor
                        + "\\t(0," + rollMs + ",\\1c" + currentColor + ")}");
        appendDialogue(ass, rollEnd, end, current,
                "{\\an5\\pos(" + (width / 2) + "," + Math.round(centerY)
                        + ")" + currentFontTag + "\\1c" + currentColor + "}");
        if (!next.isEmpty()) {
            appendDialogue(ass, rollEnd, end, next,
                    "{\\an5\\pos(" + Math.round(rightX) + "," + Math.round(centerY)
                            + ")" + nextFontTag + "\\1c" + mutedColor + "}");
        }
    }

    private static int fitHorizontalFont(String text, int baseFont, double maxWidth) {
        if (text == null || text.isBlank()) return baseFont;
        double units = visualUnits(text.replace("\\N", " "));
        if (units <= 0) return baseFont;
        int fitted = (int) Math.floor(maxWidth / units);
        return Math.max(10, Math.min(baseFont, fitted));
    }

    private static double visualUnits(String text) {
        double units = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            units += c < 128 ? 0.58 : 1.0;
        }
        return units;
    }

    private static String adjacentText(List<Entry> entries, int index) {
        if (index < 0 || index >= entries.size()) return "";
        return escapeAss(entries.get(index).text);
    }

    private static void appendDialogue(
            StringBuilder ass, double start, double end, String text, String tags) {
        if (text == null || text.isBlank() || end <= start) return;
        ass.append("Dialogue: 0,")
                .append(formatAssTime(start)).append(',')
                .append(formatAssTime(end)).append(",Lyric,,0,0,0,,")
                .append(tags).append(text).append('\n');
    }

    private static String assColorTag(Color color) {
        return String.format(Locale.ROOT, "&H%02X%02X%02X&",
                color.getBlue(), color.getGreen(), color.getRed());
    }

    private static List<Entry> buildEntries(List<LyricLine> lines) {
        List<Entry> entries = new ArrayList<>();
        for (LyricLine line : lines) {
            if (line.timeMs == null || line.text.isBlank()) continue;
            entries.add(new Entry(line.timeMs, line.text.trim()));
        }
        entries.sort(Comparator.comparingLong(Entry::timeMs));

        List<Entry> merged = new ArrayList<>();
        for (Entry entry : entries) {
            if (!merged.isEmpty() && merged.get(merged.size() - 1).timeMs == entry.timeMs) {
                Entry previous = merged.remove(merged.size() - 1);
                merged.add(new Entry(previous.timeMs,
                        previous.text + "\\N" + entry.text));
            } else {
                merged.add(entry);
            }
        }
        return merged;
    }

    private static String escapeAss(String text) {
        String[] lines = text.split("\\\\N", -1);
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) escaped.append("\\N");
            escaped.append(lines[i]
                    .replace("\\", "\\\\")
                    .replace("{", "\\{")
                    .replace("}", "\\}"));
        }
        return escaped.toString();
    }

    private static String formatAssTime(double seconds) {
        long totalCentis = Math.max(0, Math.round(seconds * 100));
        long cs = totalCentis % 100;
        long totalSeconds = totalCentis / 100;
        long s = totalSeconds % 60;
        long totalMinutes = totalSeconds / 60;
        long m = totalMinutes % 60;
        long h = totalMinutes / 60;
        return String.format(Locale.ROOT, "%d:%02d:%02d.%02d", h, m, s, cs);
    }

    private static String formatSeconds(double seconds) {
        return String.format(Locale.ROOT, "%.3f", seconds);
    }

    private static String toAssColor(Color color) {
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        return String.format(Locale.ROOT, "&H00%02X%02X%02X", b, g, r);
    }

    public static BufferedImage createBuiltinBackground(
            String preset, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        Color top;
        Color bottom;
        if ("暗夜".equals(preset) || "粒子漂浮".equals(preset)) {
            top = new Color(0x05, 0x0A, 0x17);
            bottom = new Color(0x0A, 0x12, 0x30);
        } else if ("暖阳".equals(preset)) {
            top = new Color(0x2A, 0x12, 0x0C);
            bottom = new Color(0xB9, 0x5A, 0x28);
        } else if ("纯黑".equals(preset)) {
            top = Color.BLACK;
            bottom = Color.BLACK;
        } else {
            top = new Color(0x04, 0x12, 0x2C);
            bottom = new Color(0x0B, 0x4A, 0xD8);
        }

        g.setPaint(new GradientPaint(0, 0, top, width, height, bottom));
        g.fillRect(0, 0, width, height);

        if (!"纯黑".equals(preset)) {
            float radius = Math.max(width, height) * 0.48f;
            g.setPaint(new RadialGradientPaint(
                    new Point2D.Double(width * 0.24, height * 0.20),
                    radius,
                    new float[]{0f, 1f},
                    new Color[]{new Color(90, 160, 255, 72), new Color(0, 0, 0, 0)}));
            g.fillRect(0, 0, width, height);
        }

        if ("粒子漂浮".equals(preset)) {
            Random random = new Random(20261009L);
            for (int i = 0; i < 90; i++) {
                int size = Math.max(1, (int) (random.nextDouble() * width * 0.0035));
                int x = random.nextInt(width);
                int y = random.nextInt(height);
                int alpha = 35 + random.nextInt(95);
                g.setColor(new Color(170, 215, 255, alpha));
                g.fill(new Ellipse2D.Double(x, y, size, size));
            }
        }

        g.setColor(new Color(255, 255, 255, 20));
        g.setStroke(new BasicStroke(Math.max(1f, width / 960f)));
        g.drawRect(width / 28, height / 28, width - width / 14, height - height / 14);
        g.dispose();
        return image;
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temporary cleanup failure is non-fatal.
                }
            });
        } catch (IOException ignored) {
            // Temporary cleanup failure is non-fatal.
        }
    }

    private record Entry(long timeMs, String text) {
    }

    private record LayoutMetrics(
            int currentFontSize,
            int nonCurrentFontSize,
            int lineSpacing,
            double currentLineHeight,
            double nonCurrentLineHeight,
            double gap,
            double previousCenter,
            double currentCenter,
            double nextCenter,
            double nextEntryStartY) {
    }
}
