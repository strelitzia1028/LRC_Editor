package com.lrceditor;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LrcExportDialog {
    private static final double DEFAULT_PREVIEW_FONT_SIZE = 15;
    private static final double MIN_PREVIEW_FONT_SIZE = 10;
    private static final double MAX_PREVIEW_FONT_SIZE = 30;
    private static final Pattern RESOLUTION_PATTERN =
            Pattern.compile("(\\d+)\\s*[x×]\\s*(\\d+)");

    private LrcExportDialog() {
    }

    public static void show(Stage owner, LrcDocument document,
                            boolean withBom, boolean dark) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("导出歌词与字幕");

        String textColor = dark ? "#e2e8f0" : "#1f2937";
        String mutedColor = dark ? "#94a3b8" : "#64748b";
        String fieldColor = dark ? "#0f172a" : "#ffffff";
        String borderColor = dark ? "#334155" : "#cbd5e1";
        String inlineStyle =
                "-fx-control-inner-background:" + fieldColor + ";"
                        + "-fx-background-color:" + fieldColor + ";"
                        + "-fx-text-fill:" + textColor + ";"
                        + "-fx-prompt-text-fill:" + mutedColor + ";"
                        + "-fx-font-size:14px;"
                        + "-fx-border-color:" + borderColor + ";"
                        + "-fx-border-radius:5;"
                        + "-fx-background-radius:5;";

        ComboBox<LyricExportFormatter.Format> formatCombo =
                new ComboBox<>(FXCollections.observableArrayList(
                        LyricExportFormatter.Format.values()));
        formatCombo.setValue(LyricExportFormatter.Format.LRC);
        formatCombo.setMaxWidth(Double.MAX_VALUE);
        formatCombo.setStyle(inlineStyle);

        File suggested = defaultExportFile(document,
                LyricExportFormatter.Format.LRC);
        TextField pathField = new TextField(suggested.getAbsolutePath());
        pathField.setStyle(inlineStyle);
        HBox.setHgrow(pathField, Priority.ALWAYS);
        Button browseButton = new Button("…");

        Button mergeButton = new Button("合并/分离相同时间戳：分离");
        mergeButton.setMaxWidth(Double.MAX_VALUE);
        mergeButton.setStyle("-fx-font-size:16px;-fx-font-weight:600;"
                + "-fx-padding:9 12 9 12;");
        final boolean[] merge = {false};

        ComboBox<String> separatorCombo = new ComboBox<>(
                FXCollections.observableArrayList(
                        "窄空格 U+2009", "普通空格 U+0020", "斜杠 /"));
        separatorCombo.setValue("窄空格 U+2009");
        separatorCombo.setMaxWidth(Double.MAX_VALUE);
        separatorCombo.setStyle(inlineStyle);
        HBox separatorRow = formRow("多语分隔", separatorCombo, textColor);
        Label separatorDetail = new Label();
        separatorDetail.setTextFill(Color.web(mutedColor));
        separatorDetail.setStyle("-fx-font-size:12px;");

        ComboBox<String> resolutionCombo = new ComboBox<>(
                FXCollections.observableArrayList(
                        "1920 × 1080", "1280 × 720", "1080 × 1920", "720 × 1280"));
        resolutionCombo.setValue("1920 × 1080");
        resolutionCombo.setMaxWidth(Double.MAX_VALUE);
        resolutionCombo.setStyle(inlineStyle);
        HBox resolutionRow = formRow("ASS 分辨率", resolutionCombo, textColor);

        TextField fontField = new TextField("Microsoft YaHei");
        fontField.setStyle(inlineStyle);
        HBox fontRow = formRow("字幕字体", fontField, textColor);
        TextField fontSizeField = new TextField("48");
        fontSizeField.setStyle(inlineStyle);
        HBox fontRow2 = formRow("字幕字号", fontSizeField, textColor);
        ColorPicker subtitleColor = new ColorPicker(Color.WHITE);
        subtitleColor.setMaxWidth(Double.MAX_VALUE);
        HBox colorRow = formRow("字幕颜色", subtitleColor, textColor);

        HBox pathRow = new HBox(8, pathField, browseButton);
        pathRow.setAlignment(Pos.CENTER_LEFT);

        HBox formatRow = formRow("导出格式", formatCombo, textColor);
        Label pathLabel = formLabel("保存路径", textColor);
        Label hint = new Label();
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
        HBox zoomHint = new HBox(8, zoomLabel, resetZoom);
        zoomHint.setAlignment(Pos.CENTER_LEFT);
        zoomHint.getStyleClass().add("lrc-export-zoom-hint");
        zoomHint.setVisible(false);
        zoomHint.setManaged(false);

        preview.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (!event.isControlDown() || Math.abs(event.getDeltaY()) < 0.01) return;
            double oldSize = previewFontSize[0];
            double nextSize = Math.max(MIN_PREVIEW_FONT_SIZE,
                    Math.min(MAX_PREVIEW_FONT_SIZE,
                            oldSize + (event.getDeltaY() > 0 ? 1 : -1)));
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

        final Runnable[] refreshPreview = new Runnable[1];
        refreshPreview[0] = () -> {
            LyricExportFormatter.Options options = readOptions(
                    formatCombo, merge[0], separatorCombo, resolutionCombo,
                    fontField, fontSizeField, subtitleColor);
            double previousScrollTop = preview.getScrollTop();
            double previousScrollLeft = preview.getScrollLeft();
            preview.setText(LyricExportFormatter.build(document, options));
            Platform.runLater(() -> {
                preview.layout();
                preview.setScrollTop(previousScrollTop);
                preview.setScrollLeft(previousScrollLeft);
            });
        };

        Runnable updateFormatControls = () -> {
            LyricExportFormatter.Format format = formatCombo.getValue();
            boolean lrc = format == LyricExportFormatter.Format.LRC
                    || format == LyricExportFormatter.Format.NETEASE_LRC;
            boolean netease = format == LyricExportFormatter.Format.NETEASE_LRC;
            boolean ass = format == LyricExportFormatter.Format.ASS;
            mergeButton.setVisible(lrc);
            mergeButton.setManaged(lrc);
            separatorRow.setVisible(netease);
            separatorRow.setManaged(netease);
            separatorDetail.setVisible(netease);
            separatorDetail.setManaged(netease);
            resolutionRow.setVisible(ass);
            resolutionRow.setManaged(ass);
            fontRow.setVisible(ass);
            fontRow.setManaged(ass);
            fontRow2.setVisible(ass);
            fontRow2.setManaged(ass);
            colorRow.setVisible(ass);
            colorRow.setManaged(ass);
            hint.setText(formatHint(format));
            pathField.setText(replaceExtension(
                    pathField.getText(), format.extension));
            refreshPreview[0].run();
        };

        formatCombo.valueProperty().addListener(
                (o, oldValue, newValue) -> updateFormatControls.run());
        separatorCombo.valueProperty().addListener(
                (o, oldValue, newValue) -> {
                    updateSeparatorDetail(separatorDetail, newValue);
                    refreshPreview[0].run();
                });
        resolutionCombo.valueProperty().addListener(
                (o, oldValue, newValue) -> refreshPreview[0].run());
        fontField.textProperty().addListener(
                (o, oldValue, newValue) -> refreshPreview[0].run());
        fontSizeField.textProperty().addListener(
                (o, oldValue, newValue) -> refreshPreview[0].run());
        subtitleColor.valueProperty().addListener(
                (o, oldValue, newValue) -> refreshPreview[0].run());
        mergeButton.setOnAction(event -> {
            merge[0] = !merge[0];
            mergeButton.setText(merge[0]
                    ? "合并/分离相同时间戳：合并"
                    : "合并/分离相同时间戳：分离");
            refreshPreview[0].run();
        });

        browseButton.setOnAction(event -> {
            LyricExportFormatter.Format format = formatCombo.getValue();
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
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    format.label, "*." + format.extension));
            File selected = chooser.showSaveDialog(stage);
            if (selected != null) {
                pathField.setText(replaceExtension(
                        selected.getAbsolutePath(), format.extension));
            }
        });

        VBox settings = new VBox(11,
                formatRow,
                pathLabel,
                pathRow,
                mergeButton,
                separatorRow,
                separatorDetail,
                resolutionRow,
                fontRow,
                fontRow2,
                colorRow,
                new Separator(),
                hint);
        settings.setPadding(new Insets(16));
        settings.setPrefWidth(390);

        BorderPane root = new BorderPane();
        root.getStyleClass().addAll("video-export-root", "lrc-export-root");
        if (dark) root.getStyleClass().add("theme-dark");
        root.setStyle(dark
                ? "-fx-background-color:#0b1120;"
                : "-fx-background-color:#f3f5f7;");
        root.setLeft(settings);
        VBox previewBox = new VBox(4, preview, zoomHint);
        VBox.setVgrow(preview, Priority.ALWAYS);
        root.setCenter(previewBox);
        BorderPane.setMargin(previewBox, new Insets(16, 16, 16, 0));

        Button cancelButton = new Button("取消");
        Button exportButton = new Button("导出");
        HBox actions = new HBox(8, cancelButton, exportButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(10, 16, 14, 16));
        root.setBottom(actions);
        cancelButton.setOnAction(event -> stage.close());

        exportButton.setOnAction(event -> {
            LyricExportFormatter.Format format = formatCombo.getValue();
            File target = normalizeTarget(
                    pathField.getText(), suggested.getName(), format.extension);
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
                LyricExportFormatter.Options options = readOptions(
                        formatCombo, merge[0], separatorCombo, resolutionCombo,
                        fontField, fontSizeField, subtitleColor);
                LrcIo.write(target,
                        LyricExportFormatter.build(document, options), withBom);
                stage.close();
                Alert success = new Alert(Alert.AlertType.INFORMATION,
                        format.label + " 已导出：\n" + target.getAbsolutePath());
                success.initOwner(owner);
                success.showAndWait();
            } catch (Exception error) {
                alert(stage, "导出失败：" + error.getMessage());
            }
        });

        updateSeparatorDetail(separatorDetail, separatorCombo.getValue());
        updateFormatControls.run();
        Scene scene = new Scene(root, 1080, 720);
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
        stage.setMinWidth(800);
        stage.setMinHeight(520);
        stage.showAndWait();
    }

    public static String buildExportText(LrcDocument document, boolean merge) {
        return LyricExportFormatter.buildLrc(document, merge);
    }

    private static LyricExportFormatter.Options readOptions(
            ComboBox<LyricExportFormatter.Format> formatCombo,
            boolean merge,
            ComboBox<String> separatorCombo,
            ComboBox<String> resolutionCombo,
            TextField fontField,
            TextField fontSizeField,
            ColorPicker colorPicker) {
        LyricExportFormatter.Options options = new LyricExportFormatter.Options();
        options.format = formatCombo.getValue();
        options.mergeDuplicateText = merge;
        options.multiLanguageSeparator = separatorCombo.getValue();
        options.fontFamily = fontField.getText();
        options.fontSize = parseInt(fontSizeField.getText(), 48);
        options.textColor = colorPicker.getValue();
        Matcher matcher = RESOLUTION_PATTERN.matcher(resolutionCombo.getValue());
        if (matcher.matches()) {
            options.subtitleWidth = Integer.parseInt(matcher.group(1));
            options.subtitleHeight = Integer.parseInt(matcher.group(2));
        }
        return options;
    }

    private static String formatHint(LyricExportFormatter.Format format) {
        return switch (format) {
            case NETEASE_LRC ->
                    "相同时间戳的多语歌词会合并为一行，默认使用窄空格 U+2009；"
                            + "可直接用于支持该格式的播放器。";
            case SRT ->
                    "保持时间顺序；同时间戳歌词合并为一条，多语内容分行显示。"
                            + "每条结束于下一句开始，最后一条默认持续 4 秒。";
            case ASS ->
                    "包含分辨率、字体、字号、颜色和描边样式；同时间戳歌词以 \\N 分行。"
                            + "每条结束于下一句开始，最后一条默认持续 4 秒。";
            default ->
                    "合并模式会把内容相同但时间戳不同的歌词合并到首次出现位置，"
                            + "并从小到大写入多个时间戳。";
        };
    }

    private static HBox formRow(String text, Node control, String textColor) {
        Label label = formLabel(text, textColor);
        HBox row = new HBox(10, label, control);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, Priority.ALWAYS);
        label.setMinWidth(86);
        label.setPrefWidth(86);
        return row;
    }

    private static Label formLabel(String text, String textColor) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size:13px;");
        label.setTextFill(Color.web(textColor));
        return label;
    }

    private static void updateSeparatorDetail(Label label, String separator) {
        if (separator != null && separator.startsWith("普通空格")) {
            label.setText("当前为 U+0020 SPACE（普通空格），不是窄空格。");
        } else if (separator != null && separator.startsWith("斜杠")) {
            label.setText("当前分隔符为“ / ”。");
        } else {
            label.setText(
                    "当前为 U+2009 THIN SPACE（窄空格），宽度明显小于普通空格。");
        }
    }

    private static File defaultExportFile(
            LrcDocument document, LyricExportFormatter.Format format) {
        File desktop = desktopDirectory();
        String baseName = document.getFile() == null
                ? "lyrics" : stripKnownExtension(document.getFile().getName());
        if (baseName == null || baseName.isBlank()) baseName = "lyrics";
        return new File(desktop, baseName + "." + format.extension);
    }

    private static File desktopDirectory() {
        String home = System.getProperty("user.home", ".");
        File desktop = new File(home, "Desktop");
        return desktop.isDirectory() ? desktop : new File(home);
    }

    private static String stripKnownExtension(String name) {
        String result = name == null ? "" : name;
        boolean stripped;
        do {
            stripped = false;
            String lower = result.toLowerCase(Locale.ROOT);
            for (String ext : new String[]{".lrc", ".srt", ".ass", ".txt"}) {
                if (lower.endsWith(ext)) {
                    result = result.substring(0, result.length() - ext.length());
                    stripped = true;
                    break;
                }
            }
        } while (stripped);
        if (!result.isBlank()) return result;
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String replaceExtension(String raw, String extension) {
        if (raw == null || raw.isBlank()) return raw;
        String normalized = raw.trim();
        int slash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        int dot = normalized.lastIndexOf('.');
        String base = dot > slash ? normalized.substring(0, dot) : normalized;
        return base + "." + extension;
    }

    private static File normalizeTarget(
            String raw, String fallbackName, String extension) {
        if (raw == null || raw.trim().isEmpty()) return null;
        File target = new File(raw.trim());
        if (target.isDirectory()) target = new File(target, fallbackName);
        String lower = target.getName().toLowerCase(Locale.ROOT);
        if (!lower.endsWith("." + extension)) {
            target = new File(replaceExtension(target.getAbsolutePath(), extension));
        }
        return target;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private static void alert(Stage owner, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.initOwner(owner);
        alert.showAndWait();
    }
}
