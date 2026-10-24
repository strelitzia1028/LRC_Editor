package com.lrceditor;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ScrollEvent;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class LrcFeatureUiSmoke extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage owner) {
        owner.setOpacity(0);
        try {
            LrcEditorApp app = new LrcEditorApp();
            app.start(owner);

            Field docField = LrcEditorApp.class.getDeclaredField("doc");
            docField.setAccessible(true);
            LrcDocument document = (LrcDocument) docField.get(app);
            document.load(
                    "[00:01.000]重复内容\n"
                            + "[00:05.000]中间内容\n"
                            + "[00:02.000]重复内容\n",
                    new File("feature-ui-smoke.lrc"));

            Method refreshAll = LrcEditorApp.class.getDeclaredMethod("refreshAll");
            refreshAll.setAccessible(true);
            refreshAll.invoke(app);

            Field advanceField =
                    LrcEditorApp.class.getDeclaredField("lyricAdvanceMs");
            advanceField.setAccessible(true);
            advanceField.setInt(app, 500);
            Method displayPlayback = LrcEditorApp.class.getDeclaredMethod(
                    "displayPlaybackSec", double.class);
            displayPlayback.setAccessible(true);
            double displayed = (double) displayPlayback.invoke(app, 1.0);
            check(Math.abs(displayed - 1.5) < 0.001, "advance display time");

            Method insertLines = LrcEditorApp.class.getDeclaredMethod(
                    "insertNewLines", int.class);
            insertLines.setAccessible(true);
            insertLines.invoke(app, 3);
            check(document.getLines().size() == 6, "custom insert line count");

            ClipboardContent clipboard = new ClipboardContent();
            clipboard.putString("[00:03.000]粘贴一\n粘贴二\n[00:04.000][00:02.500]粘贴三");
            Clipboard.getSystemClipboard().setContent(clipboard);
            Method paste = LrcEditorApp.class.getDeclaredMethod("pasteMultilineLyrics");
            paste.setAccessible(true);
            paste.invoke(app);
            List<LyricLine> lines = document.getLines();
            check(containsText(lines, "粘贴一"), "paste first line");
            check(containsText(lines, "粘贴二"), "paste inherited line");
            check(findTime(lines, "粘贴二") == 3000L,
                    "untimed paste line inherits previous timestamp");
            check(findTime(lines, "粘贴三") == 2500L,
                    "paste multiple timestamps sorted");

            List<LyricLine> padding = new ArrayList<>();
            for (int i = 0; i < 80; i++) {
                padding.add(new LyricLine(10_000L + i * 1000L, "填充行 " + i));
            }
            document.addLines(document.getLines().size(), padding);

            PauseTransition exportWait = new PauseTransition(Duration.millis(700));
            exportWait.setOnFinished(event -> {
                try {
                    Stage export = null;
                    for (Window window : allWindows()) {
                        if (window instanceof Stage
                                && "导出 LRC 歌词".equals(((Stage) window).getTitle())) {
                            export = (Stage) window;
                            break;
                        }
                    }
                    check(export != null, "export window opened");
                    Scene scene = export.getScene();
                    TextArea preview = null;
                    TextField path = null;
                    Button merge = null;
                    for (javafx.scene.Node node : scene.getRoot().lookupAll(".text-area")) {
                        if (node instanceof TextArea) {
                            preview = (TextArea) node;
                            break;
                        }
                    }
                    for (javafx.scene.Node node : scene.getRoot().lookupAll(".text-field")) {
                        if (node instanceof TextField) {
                            path = (TextField) node;
                            break;
                        }
                    }
                    for (javafx.scene.Node node : scene.getRoot().lookupAll(".button")) {
                        if (node instanceof Button
                                && ((Button) node).getText() != null
                                && ((Button) node).getText().startsWith(
                                        "合并/分离相同时间戳")) {
                            merge = (Button) node;
                            break;
                        }
                    }
                    check(preview != null, "export preview exists");
                    check(path != null, "export path field exists");
                    check(merge != null, "merge button exists");
                    check(path.getStyle().contains("#0f172a"),
                            "export path dark background");
                    check(preview.getStyle().contains("#0f172a"),
                            "export preview dark background");
                    Node previewContent = preview.lookup(".content");
                    check(previewContent instanceof javafx.scene.layout.Region,
                            "export preview content layer exists");
                    javafx.scene.layout.Background contentBackground =
                            ((javafx.scene.layout.Region) previewContent).getBackground();
                    check(contentBackground != null
                                    && !contentBackground.getFills().isEmpty()
                                    && String.valueOf(
                                            contentBackground.getFills().get(0).getFill())
                                            .contains("0f172a"),
                            "export preview content layer dark");
                    Label pathLabel = null;
                    Label hintLabel = null;
                    for (javafx.scene.Node node : scene.getRoot().lookupAll(".label")) {
                        if (!(node instanceof Label)) continue;
                        Label label = (Label) node;
                        if ("保存路径".equals(label.getText())) pathLabel = label;
                        if (label.getText() != null
                                && label.getText().startsWith("合并后")) hintLabel = label;
                    }
                    check(pathLabel != null
                                    && ((javafx.scene.paint.Color) pathLabel.getTextFill())
                                            .getBrightness() > 0.7,
                            "export path label readable");
                    check(hintLabel != null
                                    && ((javafx.scene.paint.Color) hintLabel.getTextFill())
                                            .getBrightness() > 0.4,
                            "export hint label readable");
                    check(pathLabel.getFont().getSize() >= 15,
                            "export path label font size");
                    check(hintLabel.getFont().getSize() >= 13,
                            "export hint font size");
                    check(preview.getStyle().contains("15.0px"),
                            "export preview default font size");
                    ScrollEvent zoomEvent = new ScrollEvent(
                            ScrollEvent.SCROLL, 100, 100, 100, 100,
                            false, true, false, false, false, false,
                            0, 40, 0, 40,
                            ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                            ScrollEvent.VerticalTextScrollUnits.NONE, 0,
                            0, null);
                    Event.fireEvent(preview, zoomEvent);
                    scene.getRoot().applyCss();
                    scene.getRoot().layout();
                    check(preview.getStyle().contains("16.0px"),
                            "ctrl wheel zooms preview");
                    javafx.scene.Node zoomHintNode =
                            scene.getRoot().lookup(".lrc-export-zoom-hint");
                    javafx.scene.Node resetZoomNode =
                            scene.getRoot().lookup(".lrc-export-zoom-reset");
                    check(zoomHintNode != null && zoomHintNode.isVisible(),
                            "zoom hint appears");
                    check(resetZoomNode instanceof javafx.scene.control.Hyperlink,
                            "zoom reset link exists");
                    Bounds previewBounds = preview.localToScene(preview.getBoundsInLocal());
                    Bounds zoomBounds = zoomHintNode.localToScene(
                            zoomHintNode.getBoundsInLocal());
                    check(zoomBounds.getMinY() >= previewBounds.getMaxY() - 1,
                            "zoom hint below preview without overlap");
                    ((javafx.scene.control.Hyperlink) resetZoomNode).fire();
                    scene.getRoot().applyCss();
                    scene.getRoot().layout();
                    check(preview.getStyle().contains("15.0px"),
                            "zoom reset restores default font size");
                    check(!zoomHintNode.isVisible(),
                            "zoom hint hides after reset");
                    preview.setScrollTop(120);
                    preview.layout();
                    double scrollBefore = preview.getScrollTop();
                    check(scrollBefore > 0, "export preview is scrollable");
                    final TextArea previewFinal = preview;
                    final Stage exportFinal = export;
                    final double scrollBeforeFinal = scrollBefore;
                    merge.fire();
                    check(preview.getText().contains(
                            "[00:01.000][00:02.000]重复内容"),
                            "export preview merged and sorted");
                    check(!preview.isEditable(), "export preview read-only");
                    PauseTransition scrollWait =
                            new PauseTransition(Duration.millis(250));
                    scrollWait.setOnFinished(ignored -> {
                        try {
                            check(Math.abs(previewFinal.getScrollTop()
                                            - scrollBeforeFinal) < 1.0,
                                    "merge preserves preview scroll position");
                            exportFinal.close();
                        } catch (Throwable failure) {
                            failure.printStackTrace();
                            System.exit(6);
                        }
                    });
                    scrollWait.play();
                } catch (Throwable failure) {
                    failure.printStackTrace();
                    System.exit(2);
                }
            });
            exportWait.play();

            Method export = LrcEditorApp.class.getDeclaredMethod(
                    "exportLrc", Stage.class);
            export.setAccessible(true);
            export.invoke(app, owner);

            // The export window closes from the nested event loop above.
            Platform.runLater(() -> {
                System.out.println("featureUiSmoke=passed");
                owner.close();
                Platform.exit();
            });
        } catch (Throwable failure) {
            failure.printStackTrace();
            owner.close();
            Platform.exit();
            System.exit(3);
        }
    }

    private static boolean containsText(List<LyricLine> lines, String text) {
        for (LyricLine line : lines) if (text.equals(line.text)) return true;
        return false;
    }

    private static List<Window> allWindows() {
        List<Window> result = new ArrayList<>();
        try {
            Method method = Window.class.getMethod("getWindows");
            Object value = method.invoke(null);
            if (value instanceof Iterable) {
                for (Object item : (Iterable<?>) value) {
                    if (item instanceof Window) result.add((Window) item);
                }
            }
        } catch (Throwable ignored) {
            try {
                Class<?> helper = Class.forName("com.sun.javafx.stage.StageHelper");
                Object value = helper.getMethod("getStages").invoke(null);
                if (value instanceof Iterable) {
                    for (Object item : (Iterable<?>) value) {
                        if (item instanceof Window) result.add((Window) item);
                    }
                }
            } catch (Throwable ignoredAgain) {
            }
        }
        return result;
    }

    private static Long findTime(List<LyricLine> lines, String text) {
        for (LyricLine line : lines) if (text.equals(line.text)) return line.timeMs;
        return null;
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
