package com.lrceditor;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.input.ScrollEvent;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LrcExportDialog {
    private static final double DEFAULT_PREVIEW_FONT_SIZE = 15;
    private static final double MIN_PREVIEW_FONT_SIZE = 10;
    private static final double MAX_PREVIEW_FONT_SIZE = 30;

    private LrcExportDialog() {
    }

    public static void show(Stage owner, LrcDocument document,
                            boolean withBom, boolean dark) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("导出 LRC 歌词");

        String textColor = dark ? "#e2e8f0" : "#1f2937";
        String mutedColor = dark ? "#94a3b8" : "#64748b";
        String fieldColor = dark ? "#0f172a" : "#ffffff";
        String borderColor = dark ? "#334155" : "#cbd5e1";

        File suggested = defaultExportFile(document);
        TextField pathField = new TextField(suggested.getAbsolutePath());
        pathField.getStyleClass().add("lrc-export-path");
        pathField.setStyle(
                "-fx-control-inner-background:" + fieldColor + ";"
                        + "-fx-background-color:" + fieldColor + ";"
                        + "-fx-text-fill:" + textColor + ";"
                        + "-fx-prompt-text-fill:" + mutedColor + ";"
                        + "-fx-font-size:14px;"
                        + "-fx-border-color:" + borderColor + ";"
                        + "-fx-border-radius:5;"
                        + "-fx-background-radius:5;");
        Button browseButton = new Button("…");
        browseButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("选择导出位置");
            File current = new File(pathField.getText());
            File parent = current.getParentFile();
            if (parent != null && parent.isDirectory()) {
                chooser.setInitialDirectory(parent);
            } else {
                File desktop = desktopDirectory();
                if (desktop != null) chooser.setInitialDirectory(desktop);
            }
            chooser.setInitialFileName(current.getName());
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("LRC 歌词", "*.lrc"));
            File selected = chooser.showSaveDialog(stage);
            if (selected != null) pathField.setText(selected.getAbsolutePath());
        });

        HBox pathRow = new HBox(8, pathField, browseButton);
        pathRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(pathField, Priority.ALWAYS);

        Button mergeButton = new Button();
        final boolean[] merge = {false};
        Button exportButton = new Button("导出");
        Button cancelButton = new Button("取消");
        Label pathLabel = new Label("保存路径");
        pathLabel.setTextFill(Color.web(textColor));
        pathLabel.setStyle("-fx-font-size:15px;-fx-font-weight:600;");
        Label hint = new Label(
                "合并后：相同歌词内容会按首次出现位置排列，"
                        + "并把所有时间戳从小到大写入同一行。");
        hint.setWrapText(true);
        hint.setTextFill(Color.web(mutedColor));
        hint.setStyle("-fx-font-size:13px;");

        TextArea preview = new TextArea();
        preview.getStyleClass().add("lrc-export-preview");
        preview.setEditable(false);
        preview.setWrapText(false);
        preview.setPrefColumnCount(60);
        preview.setPrefRowCount(24);
        final String previewStylePrefix =
                "-fx-control-inner-background:" + fieldColor + ";"
                        + "-fx-background-color:" + fieldColor + ";"
                        + "-fx-text-fill:" + textColor + ";"
                        + "-fx-border-color:" + borderColor + ";"
                        + "-fx-border-radius:5;"
                        + "-fx-background-radius:5;"
                        + "-fx-font-family:'Consolas','Microsoft YaHei',monospace;"
                        + "-fx-font-size:";
        final double[] previewFontSize = {DEFAULT_PREVIEW_FONT_SIZE};
        preview.setStyle(previewStylePrefix + DEFAULT_PREVIEW_FONT_SIZE + "px;");

        Label zoomLabel = new Label("当前字号：" + (int) DEFAULT_PREVIEW_FONT_SIZE + " px");
        zoomLabel.getStyleClass().add("lrc-export-zoom-label");
        zoomLabel.setTextFill(Color.web(mutedColor));
        zoomLabel.setStyle("-fx-font-size:12px;");
        Hyperlink resetZoom = new Hyperlink("重置");
        resetZoom.getStyleClass().add("lrc-export-zoom-reset");
        resetZoom.setTextFill(Color.web(dark ? "#60a5fa" : "#2563eb"));
        resetZoom.setStyle("-fx-font-size:12px;");
        HBox zoomHint = new HBox(8, zoomLabel, resetZoom);
        zoomHint.getStyleClass().add("lrc-export-zoom-hint");
        zoomHint.setAlignment(Pos.CENTER_LEFT);
        zoomHint.setVisible(false);
        zoomHint.setManaged(false);

        preview.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (!event.isControlDown() || Math.abs(event.getDeltaY()) < 0.01) {
                return;
            }
            double oldSize = previewFontSize[0];
            double nextSize = oldSize + (event.getDeltaY() > 0 ? 1 : -1);
            nextSize = Math.max(MIN_PREVIEW_FONT_SIZE,
                    Math.min(MAX_PREVIEW_FONT_SIZE, nextSize));
            if (Math.abs(nextSize - oldSize) < 0.01) {
                event.consume();
                return;
            }
            double scrollTop = preview.getScrollTop() * nextSize / oldSize;
            double scrollLeft = preview.getScrollLeft() * nextSize / oldSize;
            previewFontSize[0] = nextSize;
            preview.setStyle(previewStylePrefix + nextSize + "px;");
            zoomLabel.setText("当前字号：" + (int) nextSize + " px");
            zoomHint.setManaged(true);
            zoomHint.setVisible(true);
            Platform.runLater(() -> {
                preview.layout();
                preview.setScrollTop(scrollTop);
                preview.setScrollLeft(scrollLeft);
            });
            event.consume();
        });
        resetZoom.setOnAction(event -> {
            double oldSize = previewFontSize[0];
            double scrollTop =
                    preview.getScrollTop() * DEFAULT_PREVIEW_FONT_SIZE / oldSize;
            double scrollLeft =
                    preview.getScrollLeft() * DEFAULT_PREVIEW_FONT_SIZE / oldSize;
            previewFontSize[0] = DEFAULT_PREVIEW_FONT_SIZE;
            preview.setStyle(previewStylePrefix + DEFAULT_PREVIEW_FONT_SIZE + "px;");
            zoomHint.setManaged(false);
            zoomHint.setVisible(false);
            Platform.runLater(() -> {
                preview.layout();
                preview.setScrollTop(scrollTop);
                preview.setScrollLeft(scrollLeft);
            });
        });
        mergeButton.setStyle("-fx-font-size:14px;");
        mergeButton.setMaxWidth(Double.MAX_VALUE);
        cancelButton.setStyle("-fx-font-size:14px;");
        exportButton.setStyle("-fx-font-size:14px;");

        Runnable refreshPreview = () -> {
            double previousScrollTop = preview.getScrollTop();
            double previousScrollLeft = preview.getScrollLeft();
            preview.setText(buildExportText(document, merge[0]));
            mergeButton.setText(merge[0]
                    ? "合并/分离相同时间戳：合并"
                    : "合并/分离相同时间戳：分离");
            Platform.runLater(() -> {
                preview.layout();
                preview.setScrollTop(previousScrollTop);
                preview.setScrollLeft(previousScrollLeft);
            });
        };
        mergeButton.setOnAction(event -> {
            merge[0] = !merge[0];
            refreshPreview.run();
        });

        VBox settings = new VBox(12);
        settings.setPadding(new Insets(16));
        settings.setPrefWidth(360);
        VBox.setVgrow(hint, Priority.NEVER);
        settings.getChildren().addAll(
                pathLabel,
                pathRow,
                mergeButton,
                hint);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("video-export-root");
        root.getStyleClass().add("lrc-export-root");
        if (dark) root.getStyleClass().add("theme-dark");
        root.setStyle(dark
                ? "-fx-background-color:#0b1120;"
                : "-fx-background-color:#f3f5f7;");
        root.setLeft(settings);
        VBox previewBox = new VBox(4, preview, zoomHint);
        VBox.setVgrow(preview, Priority.ALWAYS);
        root.setCenter(previewBox);
        BorderPane.setMargin(previewBox, new Insets(16, 16, 16, 0));

        HBox actions = new HBox(8, cancelButton, exportButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(10, 16, 14, 16));
        root.setBottom(actions);

        cancelButton.setOnAction(event -> stage.close());
        exportButton.setOnAction(event -> {
            File target = normalizeTarget(pathField.getText(), suggested.getName());
            if (target == null) {
                alert(stage, "请输入有效的导出路径");
                return;
            }
            if (target.exists()) {
                Alert overwrite = new Alert(Alert.AlertType.CONFIRMATION,
                        "文件已存在，是否覆盖？\n" + target.getAbsolutePath(),
                        ButtonType.YES, ButtonType.NO);
                overwrite.initOwner(stage);
                if (overwrite.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                    return;
                }
            }
            try {
                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IllegalStateException("无法创建保存目录");
                }
                LrcIo.write(target, buildExportText(document, merge[0]), withBom);
                stage.close();
                Alert success = new Alert(Alert.AlertType.INFORMATION,
                        "歌词已导出：\n" + target.getAbsolutePath());
                success.initOwner(owner);
                success.showAndWait();
            } catch (Exception error) {
                alert(stage, "导出失败：" + error.getMessage());
            }
        });

        refreshPreview.run();
        Scene scene = new Scene(root, 980, 700);
        java.net.URL css = LrcExportDialog.class.getResource("/styles/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        if (dark) {
            java.net.URL darkCss =
                    LrcExportDialog.class.getResource("/styles/dark-theme.css");
            if (darkCss != null) scene.getStylesheets().add(darkCss.toExternalForm());
        }
        stage.setScene(scene);
        scene.getRoot().applyCss();
        Node textContent = preview.lookup(".content");
        if (textContent instanceof Region) {
            ((Region) textContent).setStyle(
                    "-fx-background-color:" + fieldColor + ";");
        }
        stage.setMinWidth(760);
        stage.setMinHeight(500);
        stage.showAndWait();
    }

    public static String buildExportText(LrcDocument document, boolean merge) {
        if (!merge) return document.serialize(true);

        Map<String, List<Long>> timestampsByText = new LinkedHashMap<>();
        for (LyricLine line : document.getLines()) {
            if (isBlank(line)) continue;
            if (line.timeMs == null) continue;
            String text = line.text;
            timestampsByText.computeIfAbsent(text, ignored -> new ArrayList<>())
                    .add(line.timeMs);
        }

        Set<String> emitted = new HashSet<>();
        StringBuilder output = new StringBuilder();
        for (LyricLine line : document.getLines()) {
            if (isBlank(line)) continue;
            if (line.timeMs == null) {
                output.append(line.text).append('\n');
                continue;
            }
            String text = line.text;
            if (!emitted.add(text)) continue;
            List<Long> times = timestampsByText.get(text);
            if (times == null) times = Collections.singletonList(line.timeMs);
            List<Long> sorted = new ArrayList<>(times);
            Collections.sort(sorted);
            for (Long time : sorted) {
                output.append(LrcIo.formatTime(time));
            }
            output.append(text).append('\n');
        }
        return output.toString();
    }

    private static boolean isBlank(LyricLine line) {
        return line.timeMs == null && line.text.trim().isEmpty();
    }

    private static File defaultExportFile(LrcDocument document) {
        File desktop = desktopDirectory();
        String name = document.getFile() == null
                ? "lyrics-export.lrc" : document.getFile().getName();
        name = stripLrcExtension(name) + "-export.lrc";
        return new File(desktop, name);
    }

    private static File desktopDirectory() {
        String home = System.getProperty("user.home", ".");
        File desktop = new File(home, "Desktop");
        if (desktop.isDirectory()) return desktop;
        return new File(home);
    }

    private static String stripLrcExtension(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".lrc")
                ? name.substring(0, name.length() - 4) : name;
    }

    private static File normalizeTarget(String raw, String fallbackName) {
        if (raw == null || raw.trim().isEmpty()) return null;
        File target = new File(raw.trim());
        if (target.isDirectory()) target = new File(target, fallbackName);
        if (!target.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".lrc")) {
            target = new File(target.getAbsolutePath() + ".lrc");
        }
        return target;
    }

    private static void alert(Stage owner, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.initOwner(owner);
        alert.showAndWait();
    }
}
