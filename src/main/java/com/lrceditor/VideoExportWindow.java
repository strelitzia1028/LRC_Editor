package com.lrceditor;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.effect.BoxBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoExportWindow {

    private static final double PREVIEW_MAX_WIDTH = 640;
    private static final double PREVIEW_MAX_HEIGHT = 480;
    private double previewWidth = 640;
    private double previewHeight = 360;

    private final Stage owner;
    private final LrcDocument doc;
    private final File audioFile;
    private final double audioDurationSeconds;
    private final Color initialTextColor;
    private final String appearanceTheme;
    private final Image albumCoverImage;

    private Stage stage;
    private Canvas previewCanvas;
    private Canvas previewBackgroundCanvas;
    private Canvas previewGrainCanvas;
    private StackPane previewSurface;
    private Region previewDarkOverlay;
    private ImageView previewImageView;
    private MediaView previewMediaView;
    private MediaPlayer previewMediaPlayer;
    private MediaPlayer previewAudioPlayer;
    private AnimationTimer previewTimer;
    private long previewStartNanos;
    private long previewLastFrameNanos;
    private double previewPositionSeconds;
    private boolean previewScrubbing;
    private boolean updatingPreviewSlider;
    private boolean previewMuted;
    private boolean previewPlaying = true;
    private boolean previewWasPlayingBeforeScrub;
    private boolean previewWasPlayingBeforeTrimDrag;
    private boolean previewMutedBeforeTrimDrag;
    private boolean trimDragging;
    private double lastTrimStartSeconds;
    private double lastTrimEndSeconds;
    private Button previewMuteButton;
    private Button previewPlayButton;
    private Slider previewTimelineSlider;
    private ToggleButton trimToggle;
    private VideoTrimRangeControl trimRangeControl;
    private Label trimRangeLabel;

    private ComboBox<String> mainEffectCombo;
    private ComboBox<String> subEffectCombo;
    private ComboBox<String> backgroundTypeCombo;
    private ComboBox<String> backgroundPresetCombo;
    private ComboBox<String> albumCoverFitCombo;
    private HBox albumCoverFitRow;
    private ComboBox<String> textureModeCombo;
    private Button textureResetButton;
    private Slider blurSlider;
    private Slider grainSlider;
    private Slider darkOpacitySlider;
    private Label blurValueLabel;
    private Label grainValueLabel;
    private Label darkOpacityValueLabel;
    private Label textureHintLabel;
    private VBox blurRow;
    private VBox grainRow;
    private VBox darkOpacityRow;
    private ComboBox<String> resolutionCombo;
    private ComboBox<Integer> fpsCombo;
    private ColorPicker textColorPicker;
    private TextField fontSizeField;
    private TextField lineSpacingField;
    private TextField nonCurrentShrinkField;
    private HBox nonCurrentShrinkRow;
    private TextField scrollSpeedField;
    private HBox scrollSpeedRow;
    private TextField outputField;
    private Label customBackgroundLabel;
    private Button chooseBackgroundButton;
    private Button resetButton;
    private Button exportButton;
    private ProgressBar progressBar;
    private Label statusLabel;

    private File customBackground;
    private boolean customOutput;
    private Task<Void> exportTask;
    private final AtomicBoolean exportCancelRequested = new AtomicBoolean(false);

    public VideoExportWindow(
            Stage owner,
            LrcDocument doc,
            File audioFile,
            double audioDurationSeconds,
            Color initialTextColor) {
        this(owner, doc, audioFile, audioDurationSeconds, initialTextColor, "light", null);
    }

    public VideoExportWindow(
            Stage owner,
            LrcDocument doc,
            File audioFile,
            double audioDurationSeconds,
            Color initialTextColor,
            String appearanceTheme) {
        this(owner, doc, audioFile, audioDurationSeconds, initialTextColor,
                appearanceTheme, null);
    }

    public VideoExportWindow(
            Stage owner,
            LrcDocument doc,
            File audioFile,
            double audioDurationSeconds,
            Color initialTextColor,
            String appearanceTheme,
            Image albumCoverImage) {
        this.owner = owner;
        this.doc = doc;
        this.audioFile = audioFile;
        this.audioDurationSeconds = audioDurationSeconds;
        this.initialTextColor = Color.WHITE;
        this.appearanceTheme = "dark".equals(appearanceTheme) ? "dark" : "light";
        this.albumCoverImage = albumCoverImage;
    }

    public void show() {
        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("一键生成视频");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("video-export-root");
        root.getStyleClass().add("theme-" + appearanceTheme);
        root.setTop(buildHeader());
        root.setLeft(buildSettingsPanel());
        root.setCenter(buildPreviewPanel());

        Scene scene = new Scene(root, 1120, 760);
        var stylesheet = VideoExportWindow.class.getResource("/styles/app.css");
        if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());
        if ("dark".equals(appearanceTheme)) {
            var darkStylesheet = VideoExportWindow.class.getResource("/styles/dark-theme.css");
            if (darkStylesheet != null) {
                scene.getStylesheets().add(darkStylesheet.toExternalForm());
            }
        }

        stage.setScene(scene);
        stage.setMinWidth(960);
        stage.setMinHeight(680);
        stage.setOnCloseRequest(e -> {
            if (exportTask != null && exportTask.isRunning()) {
                exportCancelRequested.set(true);
                exportTask.cancel(true);
            }
            stopPreview();
        });
        stage.show();
        startPreview();
        updateEffectOptions();
        updateBackgroundOptions();
        updateTextureControls();
        updateDefaultOutput();
    }

    private Region buildHeader() {
        Label title = new Label("滚动歌词视频");
        title.getStyleClass().add("video-title");
        Label subtitle = new Label("设置效果、背景与输出位置");
        subtitle.getStyleClass().add("video-subtitle");
        VBox header = new VBox(3, title, subtitle);
        header.setPadding(new Insets(18, 22, 14, 22));
        header.getStyleClass().add("video-header");
        return header;
    }

    private Region buildSettingsPanel() {
        VBox form = new VBox(12);
        form.setPadding(new Insets(18));
        form.setPrefWidth(340);
        form.setMinWidth(340);

        mainEffectCombo = new ComboBox<>(FXCollections.observableArrayList(
                "直接显现模式", "正常滚动模式"));
        mainEffectCombo.getSelectionModel().selectFirst();
        mainEffectCombo.setMaxWidth(Double.MAX_VALUE);
        mainEffectCombo.valueProperty().addListener((o, ov, nv) -> updateEffectOptions());

        subEffectCombo = new ComboBox<>();
        subEffectCombo.setMaxWidth(Double.MAX_VALUE);

        backgroundTypeCombo = new ComboBox<>(FXCollections.observableArrayList(
                "内置静态背景", "内置动态背景", "专辑封面", "自定义图片", "自定义视频"));
        backgroundTypeCombo.getSelectionModel().selectFirst();
        backgroundTypeCombo.setMaxWidth(Double.MAX_VALUE);
        backgroundTypeCombo.valueProperty().addListener((o, ov, nv) -> updateBackgroundOptions());

        backgroundPresetCombo = new ComboBox<>();
        backgroundPresetCombo.setMaxWidth(Double.MAX_VALUE);
        backgroundPresetCombo.valueProperty().addListener((o, ov, nv) -> updatePreviewBackground());

        albumCoverFitCombo = new ComboBox<>(FXCollections.observableArrayList(
                "填充", "合适", "默认"));
        albumCoverFitCombo.getSelectionModel().selectFirst();
        albumCoverFitCombo.setMaxWidth(Double.MAX_VALUE);
        albumCoverFitCombo.valueProperty().addListener(
                (o, ov, nv) -> updatePreviewBackground());
        albumCoverFitRow = formRow("封面模式", albumCoverFitCombo);
        albumCoverFitRow.setVisible(false);
        albumCoverFitRow.setManaged(false);

        customBackgroundLabel = new Label("未选择");
        customBackgroundLabel.getStyleClass().add("video-file-label");
        chooseBackgroundButton = new Button("选择背景…");
        chooseBackgroundButton.setMaxWidth(Double.MAX_VALUE);
        chooseBackgroundButton.setOnAction(e -> chooseCustomBackground());

        textureModeCombo = new ComboBox<>(FXCollections.observableArrayList(
                "无效果", "暗色+玻璃磨砂"));
        textureModeCombo.getSelectionModel().selectFirst();
        textureModeCombo.setMaxWidth(Double.MAX_VALUE);
        textureModeCombo.valueProperty().addListener((o, ov, nv) -> updateTextureControls());

        blurSlider = new Slider(0, 100, 35);
        grainSlider = new Slider(0, 100, 12);
        darkOpacitySlider = new Slider(0, 100, 35);
        blurValueLabel = new Label();
        grainValueLabel = new Label();
        darkOpacityValueLabel = new Label();
        blurRow = sliderRow("模糊程度", blurSlider, blurValueLabel);
        grainRow = sliderRow("锐度颗粒", grainSlider, grainValueLabel);
        darkOpacityRow = sliderRow("蒙版不透明度", darkOpacitySlider, darkOpacityValueLabel);
        textureHintLabel = new Label();
        textureHintLabel.getStyleClass().add("video-texture-hint");
        textureHintLabel.setWrapText(true);

        blurSlider.valueProperty().addListener((o, ov, nv) -> updateTextureControls());
        grainSlider.valueProperty().addListener((o, ov, nv) -> updateTextureControls());
        darkOpacitySlider.valueProperty().addListener((o, ov, nv) -> updateTextureControls());
        textureResetButton = new Button("恢复默认");
        textureResetButton.setTooltip(new Tooltip(
                "恢复为模糊 35%、锐度颗粒 12%、暗色 35%。"));
        textureResetButton.setOnAction(e -> resetTextureDefaults());

        resolutionCombo = new ComboBox<>(FXCollections.observableArrayList(
                "1920 × 1080", "1280 × 720", "1080 × 1920", "720 × 1280"));
        resolutionCombo.getSelectionModel().selectFirst();
        resolutionCombo.setMaxWidth(Double.MAX_VALUE);
        resolutionCombo.valueProperty().addListener(
                (o, ov, nv) -> updatePreviewViewport());

        fpsCombo = new ComboBox<>(FXCollections.observableArrayList(30, 60));
        fpsCombo.getSelectionModel().select(Integer.valueOf(30));
        fpsCombo.setMaxWidth(Double.MAX_VALUE);

        textColorPicker = new ColorPicker(
                initialTextColor == null ? Color.WHITE : initialTextColor);
        textColorPicker.setMaxWidth(Double.MAX_VALUE);
        fontSizeField = new TextField();
        fontSizeField.setPromptText("自动（推荐）");
        lineSpacingField = new TextField();
        lineSpacingField.setPromptText("自动（推荐）");
        nonCurrentShrinkField = new TextField();
        nonCurrentShrinkField.setPromptText("自动（推荐）");
        nonCurrentShrinkField.setTooltip(new Tooltip(
                "非当前行相对当前行缩小多少像素；范围 0 到 当前字号-1，留空自动。"));
        scrollSpeedField = new TextField();
        scrollSpeedField.setPromptText("自动（推荐）");
        scrollSpeedField.setTooltip(new Tooltip(
                "滚动速度倍率：1.0 为标准，2.0 为两倍速，0.5 为半速。"));
        scrollSpeedRow = formRow("滚动速度", scrollSpeedField);
        nonCurrentShrinkRow = formRow("非当前行缩小", nonCurrentShrinkField);

        outputField = new TextField();
        outputField.setPromptText("输出文件路径");
        outputField.setMaxWidth(Double.MAX_VALUE);
        outputField.textProperty().addListener((o, ov, nv) -> {
            if (outputField.isFocused()) customOutput = true;
        });
        Button chooseOutput = new Button("选择…");
        chooseOutput.setOnAction(e -> chooseOutputFile());
        HBox outputRow = new HBox(8, outputField, chooseOutput);
        HBox.setHgrow(outputField, Priority.ALWAYS);

        form.getChildren().addAll(
                sectionLabel("歌词动效"),
                formRow("主效果", mainEffectCombo),
                formRow("细分效果", subEffectCombo),
                scrollSpeedRow,
                new Separator(),
                sectionLabel("视频背景"),
                formRow("背景类型", backgroundTypeCombo),
                formRow("内置效果", backgroundPresetCombo),
                albumCoverFitRow,
                chooseBackgroundButton,
                customBackgroundLabel,
                formRow("质感", new HBox(8, textureModeCombo, textureResetButton)),
                blurRow,
                grainRow,
                darkOpacityRow,
                textureHintLabel,
                new Separator(),
                sectionLabel("画面设置"),
                formRow("分辨率", resolutionCombo),
                formRow("帧率", fpsCombo),
                formRow("歌词颜色", textColorPicker),
                formRow("歌词字号", fontSizeField),
                formRow("歌词行距", lineSpacingField),
                nonCurrentShrinkRow,
                new Separator(),
                sectionLabel("导出位置"),
                outputRow);
        resetButton = new Button("恢复默认设置");
        resetButton.setMaxWidth(Double.MAX_VALUE);
        resetButton.setOnAction(e -> resetToDefaults());
        form.getChildren().addAll(new Separator(), resetButton);

        form.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && e.getTarget() instanceof TextInputControl) {
                e.consume();
            }
        });

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("video-settings-scroll");
        return scroll;
    }

    private Region buildPreviewPanel() {
        previewBackgroundCanvas = new Canvas(previewWidth, previewHeight);
        previewCanvas = new Canvas(previewWidth, previewHeight);
        previewCanvas.setMouseTransparent(true);
        previewGrainCanvas = new Canvas(previewWidth, previewHeight);
        previewGrainCanvas.setMouseTransparent(true);
        previewDarkOverlay = new Region();
        previewDarkOverlay.setPrefSize(previewWidth, previewHeight);
        previewDarkOverlay.setMaxSize(previewWidth, previewHeight);
        previewDarkOverlay.setStyle("-fx-background-color:black;");
        previewDarkOverlay.setMouseTransparent(true);
        previewImageView = new ImageView();
        previewImageView.setFitWidth(previewWidth);
        previewImageView.setFitHeight(previewHeight);
        previewImageView.setPreserveRatio(false);
        previewImageView.setVisible(false);
        previewMediaView = new MediaView();
        previewMediaView.setFitWidth(previewWidth);
        previewMediaView.setFitHeight(previewHeight);
        previewMediaView.setPreserveRatio(false);
        previewMediaView.setVisible(false);

        previewSurface = new StackPane(
                previewBackgroundCanvas,
                previewImageView,
                previewMediaView,
                previewGrainCanvas,
                previewDarkOverlay,
                previewCanvas);
        previewSurface.getStyleClass().add("video-preview-surface");
        previewSurface.setMinSize(previewWidth, previewHeight);
        previewSurface.setPrefSize(previewWidth, previewHeight);
        previewSurface.setMaxSize(previewWidth, previewHeight);

        previewMuteButton = new Button("🔊");
        previewMuteButton.getStyleClass().add("video-mute-button");
        previewMuteButton.setFocusTraversable(false);
        previewMuteButton.setTooltip(new Tooltip("静音预览"));
        previewMuteButton.setOnAction(e -> setPreviewMuted(!previewMuted));
        StackPane.setAlignment(previewMuteButton, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(previewMuteButton, new Insets(0, 10, 10, 0));
        previewPlayButton = new Button("⏸");
        previewPlayButton.getStyleClass().add("video-play-button");
        previewPlayButton.setFocusTraversable(false);
        previewPlayButton.setTooltip(new Tooltip("暂停预览"));
        previewPlayButton.setOnAction(e -> togglePreviewPlayback());
        StackPane.setAlignment(previewPlayButton, Pos.BOTTOM_LEFT);
        StackPane.setMargin(previewPlayButton, new Insets(0, 0, 10, 10));
        previewSurface.getChildren().addAll(previewPlayButton, previewMuteButton);

        previewTimelineSlider = new Slider(0, 1, 0);
        previewTimelineSlider.getStyleClass().add("video-preview-slider");
        previewTimelineSlider.setMaxWidth(Double.MAX_VALUE);
        previewTimelineSlider.setBlockIncrement(0.5);
        previewTimelineSlider.setOnMousePressed(e -> {
            previewWasPlayingBeforeScrub = previewPlaying;
            setPreviewPlaying(false);
            previewScrubbing = true;
        });
        previewTimelineSlider.setOnMouseReleased(e -> {
            previewScrubbing = false;
            seekPreviewPlayers(previewPositionSeconds);
            if (previewWasPlayingBeforeScrub) {
                setPreviewPlaying(true);
            }
        });
        previewTimelineSlider.valueProperty().addListener((o, ov, nv) -> {
            if (updatingPreviewSlider) return;
            previewPositionSeconds = nv.doubleValue();
            drawPreview(previewPositionSeconds);
        });

        trimToggle = new ToggleButton("截取片段");
        trimToggle.getStyleClass().add("video-trim-toggle");
        trimRangeLabel = new Label();
        trimRangeLabel.getStyleClass().add("video-trim-label");
        trimRangeControl = new VideoTrimRangeControl(appearanceTheme);
        trimRangeControl.setVisible(false);
        trimRangeControl.setManaged(false);
        trimRangeControl.setOnRangeChanged((start, end) -> {
            trimRangeLabel.setText(formatTime(start) + "  -  " + formatTime(end));
            if (trimDragging) {
                double tolerance = Math.max(0.08, previewDurationSeconds() * 0.006);
                boolean followsStart = Math.abs(previewPositionSeconds - lastTrimStartSeconds)
                                       <= tolerance;
                boolean followsEnd = Math.abs(previewPositionSeconds - lastTrimEndSeconds)
                                     <= tolerance;
                if (followsStart) {
                    previewPositionSeconds = start;
                } else if (followsEnd) {
                    previewPositionSeconds = end;
                }
                if (followsStart || followsEnd) {
                    updatingPreviewSlider = true;
                    previewTimelineSlider.setValue(previewPositionSeconds);
                    updatingPreviewSlider = false;
                    seekPreviewPlayers(previewPositionSeconds);
                    drawPreview(previewPositionSeconds);
                }
            }
            lastTrimStartSeconds = start;
            lastTrimEndSeconds = end;
        });
        trimRangeControl.setOnInteractionChanged(dragging -> {
            if (dragging) {
                trimDragging = true;
                previewWasPlayingBeforeTrimDrag = previewPlaying;
                previewMutedBeforeTrimDrag = previewMuted;
                lastTrimStartSeconds = trimRangeControl.getStartSeconds();
                lastTrimEndSeconds = trimRangeControl.getEndSeconds();
                setPreviewMuted(true);
                setPreviewPlaying(false);
            } else {
                double start = trimRangeControl.getStartSeconds();
                double end = trimRangeControl.getEndSeconds();
                if (previewPositionSeconds < start || previewPositionSeconds > end) {
                    previewPositionSeconds = Math.max(start, Math.min(end, previewPositionSeconds));
                    updatingPreviewSlider = true;
                    previewTimelineSlider.setValue(previewPositionSeconds);
                    updatingPreviewSlider = false;
                    seekPreviewPlayers(previewPositionSeconds);
                    drawPreview(previewPositionSeconds);
                }
                setPreviewMuted(previewMutedBeforeTrimDrag);
                trimDragging = false;
                if (previewWasPlayingBeforeTrimDrag) {
                    setPreviewPlaying(true);
                }
            }
        });
        trimToggle.selectedProperty().addListener((o, ov, selected) -> {
            trimRangeControl.setVisible(selected);
            trimRangeControl.setManaged(selected);
            if (selected) {
                trimRangeControl.setDuration(previewDurationSeconds());
                trimRangeControl.resetRange();
                lastTrimStartSeconds = trimRangeControl.getStartSeconds();
                lastTrimEndSeconds = trimRangeControl.getEndSeconds();
            } else {
                trimRangeLabel.setText("");
            }
        });
        HBox trimBar = new HBox(10, trimToggle, trimRangeLabel);
        trimBar.setAlignment(Pos.CENTER_LEFT);

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);
        statusLabel = new Label("预览将自动循环播放");
        statusLabel.getStyleClass().add("video-status");
        exportButton = new Button("开始生成视频");
        exportButton.setDefaultButton(false);
        exportButton.setOnAction(e -> startExport());

        HBox actions = new HBox(12, progressBar, exportButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        VBox box = new VBox(8,
                previewSurface,
                trimBar,
                previewTimelineSlider,
                trimRangeControl,
                statusLabel,
                actions);
        box.setPadding(new Insets(20, 24, 22, 8));
        box.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(previewSurface, Priority.NEVER);
        return box;
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("video-section");
        return label;
    }

    private HBox formRow(String title, javafx.scene.Node control) {
        Label label = new Label(title);
        label.setMinWidth(72);
        label.getStyleClass().add("video-form-label");
        HBox row = new HBox(8, label, control);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, Priority.ALWAYS);
        return row;
    }

    private VBox sliderRow(String title, Slider slider, Label valueLabel) {
        Label label = new Label(title);
        label.getStyleClass().add("video-form-label");
        valueLabel.getStyleClass().add("video-slider-value");
        valueLabel.setMaxWidth(Double.MAX_VALUE);
        valueLabel.setAlignment(Pos.CENTER_RIGHT);
        HBox head = new HBox(8, label, valueLabel);
        head.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(valueLabel, Priority.ALWAYS);
        slider.setMaxWidth(Double.MAX_VALUE);
        VBox row = new VBox(3, head, slider);
        row.setFillWidth(true);
        return row;
    }

    private void updateTextureControls() {
        if (textureModeCombo == null) return;
        String mode = textureModeCombo.getValue();
        boolean glass = "暗色+玻璃磨砂".equals(mode);
        boolean dark = glass;

        blurRow.setVisible(glass);
        blurRow.setManaged(glass);
        grainRow.setVisible(glass);
        grainRow.setManaged(glass);
        darkOpacityRow.setVisible(dark);
        darkOpacityRow.setManaged(dark);

        int blur = (int) Math.round(blurSlider.getValue());
        int grain = (int) Math.round(grainSlider.getValue());
        int darkOpacity = (int) Math.round(darkOpacitySlider.getValue());
        blurValueLabel.setText(blur + "%");
        grainValueLabel.setText(grain == 0 ? "0%（平滑模糊）" : grain + "%");
        darkOpacityValueLabel.setText(darkOpacity + "%");

        if (glass && blur == 0) {
            textureHintLabel.setText(grain == 0
                    ? "模糊程度为 0%，且颗粒感为 0，等同于无效果。"
                    : "模糊程度为 0%，不会产生磨砂模糊，仅保留颗粒效果。");
        } else if (glass) {
            textureHintLabel.setText(grain == 0
                    ? "颗粒感为 0，显示平滑模糊。"
                    : "质感效果会叠加在背景图层之上。");
        } else if (dark && darkOpacity == 0) {
            textureHintLabel.setText("蒙版不透明度为 0%，等同于无效果。");
        } else if (dark && darkOpacity == 100) {
            textureHintLabel.setText("蒙版不透明度为 100%，背景将被完全遮挡。");
        } else if (dark) {
            textureHintLabel.setText("质感效果会叠加在背景图层之上。");
        } else {
            textureHintLabel.setText("当前不添加背景蒙版效果。");
        }
        textureHintLabel.setVisible(true);
        textureHintLabel.setManaged(true);
        updatePreviewTexture();
    }

    private void updatePreviewTexture() {
        if (previewBackgroundCanvas == null) return;
        boolean glass = "暗色+玻璃磨砂".equals(textureModeCombo.getValue());
        boolean dark = glass;
        int blurPercent = glass ? (int) Math.round(blurSlider.getValue()) : 0;
        int grainPercent = glass ? (int) Math.round(grainSlider.getValue()) : 0;
        int darkPercent = dark ? (int) Math.round(darkOpacitySlider.getValue()) : 0;

        BoxBlur blur = blurPercent > 0
                ? new BoxBlur(blurPercent / 100.0 * 25.0,
                              blurPercent / 100.0 * 25.0, 3)
                : null;
        previewBackgroundCanvas.setEffect(blur);
        previewImageView.setEffect(blur);
        previewMediaView.setEffect(blur);
        previewDarkOverlay.setOpacity(darkPercent / 100.0);
        previewGrainCanvas.getGraphicsContext2D().clearRect(
                0, 0, previewWidth, previewHeight);
    }

    private void drawPreviewGrain(int grainPercent) {
        GraphicsContext gc = previewGrainCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, previewWidth, previewHeight);
        if (grainPercent <= 0) return;

        Random random = new Random(20261009L);
        int dots = Math.round(grainPercent / 100f * 900);
        double alpha = 0.08 + grainPercent / 100.0 * 0.42;
        gc.setFill(Color.rgb(255, 255, 255, alpha));
        for (int i = 0; i < dots; i++) {
            double x = random.nextDouble() * previewWidth;
            double y = random.nextDouble() * previewHeight;
            double size = random.nextDouble() < 0.92 ? 1 : 2;
            gc.fillRect(x, y, size, size);
        }
    }

    private void updateEffectOptions() {
        if (mainEffectCombo == null || subEffectCombo == null) return;
        boolean direct = "直接显现模式".equals(mainEffectCombo.getValue());
        if (direct) {
            subEffectCombo.setItems(FXCollections.observableArrayList(
                    "直接显示模式", "单句渐显渐隐模式"));
        } else {
            subEffectCombo.setItems(FXCollections.observableArrayList(
                    "上下滚动模式"));
        }
        subEffectCombo.getSelectionModel().selectFirst();
        nonCurrentShrinkRow.setVisible(!direct);
        nonCurrentShrinkRow.setManaged(!direct);
        scrollSpeedRow.setVisible(!direct);
        scrollSpeedRow.setManaged(!direct);
    }

    private void updateBackgroundOptions() {
        if (backgroundTypeCombo == null || backgroundPresetCombo == null) return;
        String mode = backgroundTypeCombo.getValue();
        boolean custom = "自定义图片".equals(mode) || "自定义视频".equals(mode);
        boolean album = "专辑封面".equals(mode);
        chooseBackgroundButton.setDisable(!custom);
        backgroundPresetCombo.setDisable(custom || album);
        albumCoverFitRow.setVisible(album);
        albumCoverFitRow.setManaged(album);
        if (album) {
            backgroundPresetCombo.getItems().clear();
        } else if (!custom) {
            if ("内置动态背景".equals(mode)) {
                backgroundPresetCombo.setItems(FXCollections.observableArrayList(
                        "缓慢缩放", "蓝色呼吸", "粒子漂浮"));
            } else {
                backgroundPresetCombo.setItems(FXCollections.observableArrayList(
                        "深海蓝", "暗夜", "暖阳", "纯黑"));
            }
            backgroundPresetCombo.getSelectionModel().selectFirst();
        } else {
            backgroundPresetCombo.getItems().clear();
            if (customBackground != null) {
                boolean image = "自定义图片".equals(mode);
                boolean valid = image
                        ? isImage(customBackground)
                        : isVideo(customBackground);
                if (!valid) customBackground = null;
            }
        }
        updatePreviewBackground();
    }

    private void chooseCustomBackground() {
        String mode = backgroundTypeCombo.getValue();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择自定义背景");
        if ("自定义图片".equals(mode)) {
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("图片", "*.jpg", "*.jpeg", "*.png"));
        } else {
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("视频", "*.mp4", "*.mkv"));
        }
        if (customBackground != null && customBackground.getParentFile() != null) {
            chooser.setInitialDirectory(customBackground.getParentFile());
        }
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        customBackground = file;
        customBackgroundLabel.setText(file.getName());
        updatePreviewBackground();
    }

    private void chooseOutputFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("保存滚动歌词视频");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("MP4 视频", "*.mp4"));
        chooser.setInitialFileName(defaultOutputFile().getName());
        File current = currentOutputFile();
        if (current != null && current.getParentFile() != null
                && current.getParentFile().isDirectory()) {
            chooser.setInitialDirectory(current.getParentFile());
        }
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".mp4")) {
            file = new File(file.getParentFile(), file.getName() + ".mp4");
        }
        customOutput = true;
        outputField.setText(file.getAbsolutePath());
    }

    private void updateDefaultOutput() {
        if (customOutput) return;
        outputField.setText(defaultOutputFile().getAbsolutePath());
    }

    private void resetToDefaults() {
        mainEffectCombo.getSelectionModel().selectFirst();
        updateEffectOptions();
        backgroundTypeCombo.getSelectionModel().selectFirst();
        albumCoverFitCombo.getSelectionModel().selectFirst();
        textureModeCombo.getSelectionModel().selectFirst();
        resolutionCombo.getSelectionModel().selectFirst();
        fpsCombo.getSelectionModel().select(Integer.valueOf(30));
        textColorPicker.setValue(initialTextColor == null ? Color.WHITE : initialTextColor);
        fontSizeField.clear();
        lineSpacingField.clear();
        nonCurrentShrinkField.clear();
        scrollSpeedField.clear();
        resetTextureDefaults();
        trimToggle.setSelected(false);
        trimRangeControl.resetRange();
        customBackground = null;
        customBackgroundLabel.setText("未选择");
        customOutput = false;
        updateBackgroundOptions();
        updateTextureControls();
        updateDefaultOutput();
    }

    private void resetTextureDefaults() {
        blurSlider.setValue(35);
        grainSlider.setValue(12);
        darkOpacitySlider.setValue(35);
        updateTextureControls();
    }

    private File currentOutputFile() {
        String path = outputField.getText();
        return path == null || path.isBlank() ? null : new File(path.trim());
    }

    private File defaultOutputFile() {
        File base = audioFile != null ? audioFile : doc.getFile();
        File directory = null;
        if (base != null) {
            directory = base.isDirectory() ? base : base.getParentFile();
        }
        if (directory == null || !directory.isDirectory()) {
            directory = new File(System.getProperty("user.home"));
        }

        String stem = base == null ? "滚动歌词"
                : base.getName().replaceAll("\\.[^.]+$", "") + "-滚动歌词";
        return new File(directory, stem + ".mp4");
    }

    private void updatePreviewViewport() {
        if (previewSurface == null || resolutionCombo == null) return;
        int[] resolution = parseResolution(resolutionCombo.getValue());
        double scale = Math.min(
                PREVIEW_MAX_WIDTH / resolution[0],
                PREVIEW_MAX_HEIGHT / resolution[1]);
        previewWidth = Math.max(180, Math.round(resolution[0] * scale));
        previewHeight = Math.max(120, Math.round(resolution[1] * scale));

        previewBackgroundCanvas.setWidth(previewWidth);
        previewBackgroundCanvas.setHeight(previewHeight);
        previewCanvas.setWidth(previewWidth);
        previewCanvas.setHeight(previewHeight);
        previewGrainCanvas.setWidth(previewWidth);
        previewGrainCanvas.setHeight(previewHeight);
        previewDarkOverlay.setPrefSize(previewWidth, previewHeight);
        previewDarkOverlay.setMaxSize(previewWidth, previewHeight);
        previewImageView.setFitWidth(previewWidth);
        previewImageView.setFitHeight(previewHeight);
        previewMediaView.setFitWidth(previewWidth);
        previewMediaView.setFitHeight(previewHeight);
        previewSurface.setMinSize(previewWidth, previewHeight);
        previewSurface.setPrefSize(previewWidth, previewHeight);
        previewSurface.setMaxSize(previewWidth, previewHeight);
        updatePreviewBackground();
        drawPreview(previewPositionSeconds);
    }

    private void updatePreviewBackground() {
        if (previewSurface == null) return;
        stopPreviewMedia();
        String mode = backgroundTypeCombo.getValue();
        boolean customImage = "自定义图片".equals(mode)
                              && customBackground != null && isImage(customBackground);
        boolean customVideo = "自定义视频".equals(mode)
                              && customBackground != null && isVideo(customBackground);
        boolean albumCover = "专辑封面".equals(mode) && albumCoverImage != null;

        previewImageView.setVisible(customImage || albumCover);
        previewMediaView.setVisible(customVideo);

        if (customImage) {
            previewImageView.setViewport(null);
            previewImageView.setPreserveRatio(false);
            previewImageView.setFitWidth(previewWidth);
            previewImageView.setFitHeight(previewHeight);
            previewImageView.setImage(new Image(
                    customBackground.toURI().toString(), previewWidth, previewHeight,
                    false, true, true));
        } else if (albumCover) {
            String fitMode = albumCoverFitCombo.getValue();
            if ("填充".equals(fitMode)) {
                previewImageView.setViewport(null);
                previewImageView.setPreserveRatio(false);
                previewImageView.setFitWidth(previewWidth);
                previewImageView.setFitHeight(previewHeight);
            } else if ("合适".equals(fitMode)) {
                previewImageView.setViewport(null);
                previewImageView.setPreserveRatio(true);
                previewImageView.setFitWidth(previewWidth);
                previewImageView.setFitHeight(previewHeight);
            } else {
                applyAlbumCoverFillViewport();
            }
            previewImageView.setImage(albumCoverImage);
        } else {
            previewImageView.setImage(null);
            previewImageView.setViewport(null);
        }

        if (customVideo) {
            try {
                Media media = new Media(customBackground.toURI().toString());
                previewMediaPlayer = new MediaPlayer(media);
                previewMediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                previewMediaPlayer.setMute(previewMuted);
                if (previewPlaying) previewMediaPlayer.play();
                previewMediaView.setMediaPlayer(previewMediaPlayer);
            } catch (Exception ex) {
                previewMediaView.setVisible(false);
            }
        }
        updatePreviewTexture();
    }

    private void applyAlbumCoverFillViewport() {
        double imageWidth = albumCoverImage.getWidth();
        double imageHeight = albumCoverImage.getHeight();
        double targetAspect = previewWidth / previewHeight;
        double imageAspect = imageWidth / imageHeight;
        double cropWidth = imageWidth;
        double cropHeight = imageHeight;
        if (imageAspect > targetAspect) {
            cropWidth = imageHeight * targetAspect;
        } else {
            cropHeight = imageWidth / targetAspect;
        }
        double cropX = (imageWidth - cropWidth) / 2.0;
        double cropY = (imageHeight - cropHeight) / 2.0;
        previewImageView.setViewport(new javafx.geometry.Rectangle2D(
                cropX, cropY, cropWidth, cropHeight));
        previewImageView.setPreserveRatio(false);
        previewImageView.setFitWidth(previewWidth);
        previewImageView.setFitHeight(previewHeight);
    }

    private void startPreview() {
        previewStartNanos = System.nanoTime();
        previewLastFrameNanos = previewStartNanos;
        previewPositionSeconds = 0;
        previewTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double dt = (now - previewLastFrameNanos) / 1_000_000_000.0;
                previewLastFrameNanos = now;
                double total = previewDurationSeconds();
                if (Math.abs(previewTimelineSlider.getMax() - total) > 0.01) {
                    updatingPreviewSlider = true;
                    previewTimelineSlider.setMax(total);
                    updatingPreviewSlider = false;
                    if (trimToggle.isSelected()) {
                        trimRangeControl.setDuration(total);
                    }
                }
                if (!previewScrubbing && previewPlaying) {
                    double stopAt = trimToggle.isSelected()
                            ? trimRangeControl.getEndSeconds()
                            : total;
                    previewPositionSeconds += Math.max(0, Math.min(dt, 0.1));
                    if (trimToggle.isSelected() && previewPositionSeconds >= stopAt) {
                        previewPositionSeconds = stopAt;
                        setPreviewPlaying(false);
                    } else if (!trimToggle.isSelected() && previewPositionSeconds >= total) {
                        previewPositionSeconds = 0;
                        seekPreviewPlayers(0);
                    }
                    updatingPreviewSlider = true;
                    previewTimelineSlider.setValue(previewPositionSeconds);
                    updatingPreviewSlider = false;
                }
                drawPreview(previewPositionSeconds);
            }
        };
        previewTimer.start();
        updatePreviewPlayButton();
        startPreviewAudio();
    }

    private void stopPreview() {
        if (previewTimer != null) {
            previewTimer.stop();
            previewTimer = null;
        }
        stopPreviewMedia();
        stopPreviewAudio();
    }

    private void stopPreviewMedia() {
        if (previewMediaPlayer != null) {
            previewMediaPlayer.stop();
            previewMediaPlayer.dispose();
            previewMediaPlayer = null;
        }
        if (previewMediaView != null) {
            previewMediaView.setMediaPlayer(null);
        }
    }

    private void stopPreviewAudio() {
        if (previewAudioPlayer != null) {
            previewAudioPlayer.stop();
            previewAudioPlayer.dispose();
            previewAudioPlayer = null;
        }
    }

    private void startPreviewAudio() {
        if (audioFile == null || !audioFile.isFile()) return;
        Thread prepare = new Thread(() -> {
            try {
                File playable = AudioIo.preparePlayable(audioFile);
                Platform.runLater(() -> {
                    if (stage == null || !stage.isShowing()) return;
                    stopPreviewAudio();
                    try {
                        Media media = new Media(playable.toURI().toString());
                        previewAudioPlayer = new MediaPlayer(media);
                        previewAudioPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                        previewAudioPlayer.setMute(previewMuted);
                        previewAudioPlayer.setOnReady(() -> {
                            seekPreviewPlayers(previewPositionSeconds);
                            if (previewPlaying) previewAudioPlayer.play();
                        });
                    } catch (Exception ignored) {
                        // Audio preview is optional; export still uses the original file.
                    }
                });
            } catch (Exception ignored) {
                // Audio preview is optional; export still uses the original file.
            }
        }, "lrc-video-preview-audio");
        prepare.setDaemon(true);
        prepare.start();
    }

    private void togglePreviewPlayback() {
        setPreviewPlaying(!previewPlaying);
    }

    private void setPreviewPlaying(boolean playing) {
        previewPlaying = playing;
        if (playing) {
            if (trimToggle.isSelected()) {
                double start = trimRangeControl.getStartSeconds();
                double end = trimRangeControl.getEndSeconds();
                if (previewPositionSeconds < start || previewPositionSeconds >= end - 0.02) {
                    previewPositionSeconds = start;
                }
            } else if (previewPositionSeconds >= previewDurationSeconds() - 0.02) {
                previewPositionSeconds = 0;
            }
            updatingPreviewSlider = true;
            previewTimelineSlider.setValue(previewPositionSeconds);
            updatingPreviewSlider = false;
            seekPreviewPlayers(previewPositionSeconds);
        }
        if (previewAudioPlayer != null) {
            if (playing) previewAudioPlayer.play();
            else previewAudioPlayer.pause();
        }
        if (previewMediaPlayer != null) {
            if (playing) previewMediaPlayer.play();
            else previewMediaPlayer.pause();
        }
        updatePreviewPlayButton();
    }

    private void updatePreviewPlayButton() {
        if (previewPlayButton == null) return;
        previewPlayButton.setText(previewPlaying ? "⏸" : "▶");
        previewPlayButton.setTooltip(new Tooltip(previewPlaying ? "暂停预览" : "播放预览"));
    }

    private void setPreviewMuted(boolean muted) {
        previewMuted = muted;
        previewMuteButton.setText(muted ? "🔇" : "🔊");
        previewMuteButton.setTooltip(new Tooltip(muted ? "开启预览声音" : "静音预览"));
        if (previewMediaPlayer != null) {
            previewMediaPlayer.setMute(muted);
        }
        if (previewAudioPlayer != null) {
            previewAudioPlayer.setMute(muted);
        }
    }

    private void seekPreviewPlayers(double seconds) {
        if (previewAudioPlayer != null) {
            Duration total = previewAudioPlayer.getTotalDuration();
            if (total != null && !total.isUnknown() && total.greaterThan(Duration.ZERO)) {
                previewAudioPlayer.seek(Duration.seconds(seconds % total.toSeconds()));
            } else {
                previewAudioPlayer.seek(Duration.seconds(Math.max(0, seconds)));
            }
        }
        if (previewMediaPlayer == null) return;
        Duration total = previewMediaPlayer.getTotalDuration();
        if (total == null || total.isUnknown() || total.lessThanOrEqualTo(Duration.ZERO)) {
            return;
        }
        double target = seconds % total.toSeconds();
        previewMediaPlayer.seek(Duration.seconds(target));
    }

    private double previewDurationSeconds() {
        double last = 0;
        for (LyricLine line : doc.getLines()) {
            if (line.timeMs != null && !line.text.isBlank()) {
                last = Math.max(last, line.timeMs / 1000.0 + 3.0);
            }
        }
        return Math.max(audioDurationSeconds, Math.max(6.0, last));
    }

    private void drawPreview(double elapsedSeconds) {
        GraphicsContext gc = previewCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, previewWidth, previewHeight);
        GraphicsContext bg = previewBackgroundCanvas.getGraphicsContext2D();
        bg.clearRect(0, 0, previewWidth, previewHeight);

        String mode = backgroundTypeCombo.getValue();
        boolean customVisual = ("自定义图片".equals(mode) && previewImageView.isVisible())
                               || ("自定义视频".equals(mode) && previewMediaView.isVisible())
                               || ("专辑封面".equals(mode) && previewImageView.isVisible());
        if (!customVisual) {
            drawPreviewBackground(bg, mode);
        }

        List<Cue> cues = timedCues();
        if (cues.isEmpty()) {
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Microsoft YaHei", FontWeight.NORMAL, 18));
            gc.fillText("没有带时间戳的歌词", 220, 184);
            return;
        }

        double total = Math.max(6.0, cues.get(cues.size() - 1).timeMs / 1000.0 + 3.0);
        double time = elapsedSeconds % total;
        Cue cue = currentCue(cues, time);
        if (cue == null) return;

        double start = cue.timeMs / 1000.0;
        int index = cues.indexOf(cue);
        double end = index + 1 < cues.size()
                ? cues.get(index + 1).timeMs / 1000.0
                : total;
        double progress = Math.max(0, Math.min(1, (time - start) / Math.max(0.2, end - start)));

        String sub = subEffectCombo.getValue();
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        gc.setTextBaseline(javafx.geometry.VPos.CENTER);
        if ("上下滚动模式".equals(sub)) {
            drawVerticalThreeLinePreview(gc, cues, index, time, start, end);
            return;
        }
        if ("左右滚动模式".equals(sub)) {
            drawHorizontalThreeLinePreview(gc, cues, index, time, start, end);
            return;
        }

        gc.setFill(textColorPicker.getValue());
        double x = previewWidth / 2.0;
        PreviewMetrics metrics = previewMetrics(cues, index);
        double y = previewHeight / 2.0;
        double alpha = 1.0;
        if ("单句渐显渐隐模式".equals(sub)) {
            alpha = Math.sin(Math.PI * progress);
        }
        if (alpha <= 0.001) {
            gc.setGlobalAlpha(1);
            return;
        }
        gc.setGlobalAlpha(alpha);
        drawPreviewText(gc, cue.text, x, y, textColorPicker.getValue(),
                metrics.currentFontSize, metrics.lineSpacing);
        gc.setGlobalAlpha(1);
    }

    private void drawVerticalThreeLinePreview(
            GraphicsContext gc,
            List<Cue> cues,
            int index,
            double time,
            double start,
            double end) {
        double duration = Math.max(0.35, end - start);
        double moveSeconds = Math.max(0.22, Math.min(0.60, duration * 0.30));
        double speed = parseOptionalDouble(scrollSpeedField, 0);
        if (speed > 0) {
            moveSeconds = Math.max(0.08, Math.min(2.0, moveSeconds / speed));
        }
        double transitionStart = Math.max(start, end - moveSeconds);
        double progress = Math.max(0, Math.min(1,
                (time - transitionStart) / moveSeconds));
        PreviewMetrics from = previewMetrics(cues, index);
        PreviewMetrics to = index + 1 < cues.size()
                ? previewMetrics(cues, index + 1)
                : from;
        Color muted = Color.web("#94A3B8");
        Color currentColor = textColorPicker.getValue();
        boolean transitioning = time >= transitionStart && index + 1 < cues.size();
        boolean firstHalf = transitioning && progress < 0.5;
        double halfProgress = transitioning
                ? (progress < 0.5 ? progress * 2 : (progress - 0.5) * 2)
                : 0;
        double currentMid = (from.currentCenter + to.previousCenter) / 2.0;
        double nextMid = (from.nextCenter + to.currentCenter) / 2.0;

        if (index > 0 && (!transitioning || firstHalf)) {
            double previousY = firstHalf
                    ? lerp(from.previousCenter,
                           from.previousCenter - from.nonCurrentLineHeight * 0.75,
                           halfProgress)
                    : from.previousCenter;
            drawPreviewText(gc, cues.get(index - 1).text,
                    previewWidth / 2, previousY, muted,
                    from.nonCurrentFontSize, from.lineSpacing);
        }

        double currentY;
        if (!transitioning) {
            currentY = from.currentCenter;
        } else if (firstHalf) {
            currentY = lerp(from.currentCenter, currentMid, halfProgress);
        } else {
            currentY = lerp(currentMid, to.previousCenter, halfProgress);
        }
        double currentFont = transitioning
                ? lerp(from.currentFontSize, to.nonCurrentFontSize, progress)
                : from.currentFontSize;
        Color currentFill = transitioning
                ? currentColor.interpolate(muted, progress)
                : currentColor;
        drawPreviewText(gc, cues.get(index).text,
                previewWidth / 2, currentY, currentFill,
                currentFont, from.lineSpacing);

        if (index + 1 < cues.size()) {
            double y;
            if (!transitioning) {
                y = from.nextCenter;
            } else if (firstHalf) {
                y = lerp(from.nextCenter, nextMid, halfProgress);
            } else {
                y = lerp(nextMid, to.currentCenter, halfProgress);
            }
            double fontSize = transitioning
                    ? lerp(from.nonCurrentFontSize, to.currentFontSize, progress)
                    : from.nonCurrentFontSize;
            Color fill = transitioning
                    ? muted.interpolate(currentColor, progress)
                    : muted;
            drawPreviewText(gc, cues.get(index + 1).text,
                    previewWidth / 2, y, fill, fontSize, from.lineSpacing);
        }
        if (transitioning && !firstHalf && index + 2 < cues.size()) {
            int lines = Math.max(1, lineCount(cues.get(index + 1).text));
            double enterY = from.nextCenter
                            + lines * from.nonCurrentLineHeight + from.gap;
            double y = lerp(enterY, to.nextCenter, halfProgress);
            drawPreviewText(gc, cues.get(index + 2).text,
                    previewWidth / 2, y, muted,
                    to.nonCurrentFontSize, from.lineSpacing);
        }
    }

    private double lerp(double from, double to, double progress) {
        return from + (to - from) * progress;
    }

    private void drawHorizontalThreeLinePreview(
            GraphicsContext gc,
            List<Cue> cues,
            int index,
            double time,
            double start,
            double end) {
        PreviewMetrics metrics = previewMetrics(cues, index);
        double centerY = metrics.currentCenter;
        double leftX = previewWidth * 0.18;
        double rightX = previewWidth * 0.82;
        double rollSeconds = Math.max(0.14, Math.min(0.42, (end - start) * 0.38));
        double progress = Math.max(0, Math.min(1, (time - start) / rollSeconds));
        Color muted = Color.web("#94A3B8");

        if (index > 0) {
            double fontSize = fitPreviewHorizontalFont(
                    cues.get(index - 1).text, metrics.nonCurrentFontSize);
            drawPreviewText(gc, cues.get(index - 1).text,
                    leftX, centerY, muted, fontSize, metrics.lineSpacing);
        }
        double x = previewWidth * 1.05
                   + (previewWidth / 2 - previewWidth * 1.05) * progress;
        double currentFont = fitPreviewHorizontalFont(
                cues.get(index).text, metrics.currentFontSize);
        drawPreviewText(gc, cues.get(index).text,
                x, centerY, textColorPicker.getValue(),
                currentFont, metrics.lineSpacing);
        if (index + 1 < cues.size() && progress >= 1) {
            double fontSize = fitPreviewHorizontalFont(
                    cues.get(index + 1).text, metrics.nonCurrentFontSize);
            drawPreviewText(gc, cues.get(index + 1).text,
                    rightX, centerY, muted, fontSize, metrics.lineSpacing);
        }
    }

    private double fitPreviewHorizontalFont(String text, double baseFont) {
        if (text == null || text.isBlank()) return baseFont;
        double units = visualUnits(text.replace("\\N", " "));
        if (units <= 0) return baseFont;
        double fitted = previewWidth * 0.30 / units;
        return Math.max(8, Math.min(baseFont, fitted));
    }

    private double visualUnits(String text) {
        double units = 0;
        for (int i = 0; i < text.length(); i++) {
            units += text.charAt(i) < 128 ? 0.58 : 1.0;
        }
        return units;
    }

    private void drawPreviewText(
            GraphicsContext gc,
            String text,
            double x,
            double y,
            Color color,
            double fontSize,
            double extraSpacing) {
        String[] lines = text.replace("\\N", "\n").split("\\n", -1);
        gc.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, fontSize));
        double lineHeight = fontSize * 1.15 + extraSpacing;
        double firstY = y - (lines.length - 1) * lineHeight / 2.0;
        gc.setFill(color);
        for (int i = 0; i < lines.length; i++) {
            gc.fillText(lines[i], x, firstY + i * lineHeight);
        }
    }

    private PreviewMetrics previewMetrics(List<Cue> cues, int index) {
        int previousLines = index > 0 ? lineCount(cues.get(index - 1).text) : 0;
        int currentLines = lineCount(cues.get(index).text);
        int nextLines = index + 1 < cues.size() ? lineCount(cues.get(index + 1).text) : 0;
        int videoHeight = parseResolution(resolutionCombo.getValue())[1];
        double scale = previewHeight / (double) videoHeight;
        int manualFont = parseOptionalInt(fontSizeField, 0);
        int manualSpacing = parseOptionalInt(lineSpacingField, -1);
        int manualReduction = parseOptionalInt(nonCurrentShrinkField, -1);

        double font = manualFont > 0
                ? manualFont * scale
                : previewHeight * 0.052;
        double spacing = manualSpacing >= 0
                ? manualSpacing * scale
                : Math.max(1, font * 0.12);
        double nonCurrentFont = reducedFont(font, manualReduction);
        double available = previewHeight * 0.78;
        double total;
        do {
            double currentLineHeight = font * 1.35 + spacing;
            double nonCurrentLineHeight = nonCurrentFont * 1.35 + spacing;
            double gap = Math.max(7, font * 0.55);
            total = previousLines * nonCurrentLineHeight
                    + currentLines * currentLineHeight
                    + nextLines * nonCurrentLineHeight
                    + gap * 2;
            if (total <= available) break;
            font = Math.max(8, font - 1);
            nonCurrentFont = reducedFont(font, manualReduction);
            if (manualSpacing < 0) spacing = Math.max(1, font * 0.12);
        } while (font > 8);

        double currentLineHeight = font * 1.35 + spacing;
        double nonCurrentLineHeight = nonCurrentFont * 1.35 + spacing;
        double gap = Math.max(7, font * 0.55);
        double previousHeight = previousLines * nonCurrentLineHeight;
        double currentHeight = currentLines * currentLineHeight;
        double nextHeight = nextLines * nonCurrentLineHeight;
        double totalHeight = previousHeight + currentHeight + nextHeight + gap * 2;
        double top = Math.max(previewHeight * 0.06,
                (previewHeight - totalHeight) / 2.0);
        return new PreviewMetrics(
                font,
                nonCurrentFont,
                spacing,
                currentLineHeight,
                nonCurrentLineHeight,
                gap,
                top + previousHeight / 2.0,
                top + previousHeight + gap + currentHeight / 2.0,
                top + previousHeight + gap + currentHeight + gap + nextHeight / 2.0);
    }

    private double reducedFont(double currentFont, int manualReduction) {
        int maxReduction = Math.max(0, (int) Math.floor(currentFont) - 1);
        int reduction = manualReduction >= 0
                ? Math.min(manualReduction, maxReduction)
                : Math.max(1, Math.min(maxReduction,
                        (int) Math.round(currentFont * 0.18)));
        return Math.max(1, currentFont - reduction);
    }

    private double nextCenterBelow(List<Cue> cues, int index, PreviewMetrics metrics) {
        if (index + 1 >= cues.size()) {
            return metrics.nextCenter + metrics.nonCurrentLineHeight;
        }
        int lines = lineCount(cues.get(index + 1).text);
        return metrics.nextCenter + lines * metrics.nonCurrentLineHeight + metrics.gap;
    }

    private int lineCount(String text) {
        if (text == null || text.isBlank()) return 0;
        int count = 1;
        int index = -1;
        while ((index = text.indexOf("\\N", index + 1)) >= 0) count++;
        return count;
    }

    private void drawPreviewBackground(GraphicsContext gc, String mode) {
        String preset = backgroundPresetCombo.getValue();
        if (preset == null) preset = "深海蓝";
        Color top;
        Color bottom;
        if ("暗夜".equals(preset) || "粒子漂浮".equals(preset)) {
            top = Color.web("#050A17");
            bottom = Color.web("#0A1230");
        } else if ("暖阳".equals(preset)) {
            top = Color.web("#2A120C");
            bottom = Color.web("#B95A28");
        } else if ("纯黑".equals(preset)) {
            top = Color.BLACK;
            bottom = Color.BLACK;
        } else {
            top = Color.web("#04122C");
            bottom = Color.web("#0B4AD8");
        }
        gc.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, top), new Stop(1, bottom)));
        gc.fillRect(0, 0, previewWidth, previewHeight);

        if ("粒子漂浮".equals(preset)) {
            gc.setFill(Color.rgb(180, 220, 255, 0.45));
            for (int i = 0; i < 55; i++) {
                double x = (i * 73 % previewWidth);
                double y = (i * 47 % previewHeight);
                gc.fillOval(x, y, 1.5, 1.5);
            }
        }
    }

    private List<Cue> timedCues() {
        List<Cue> cues = new ArrayList<>();
        for (LyricLine line : doc.getLines()) {
            if (line.timeMs == null || line.text.isBlank()) continue;
            cues.add(new Cue(line.timeMs, line.text.trim()));
        }
        cues.sort(Comparator.comparingLong(Cue::timeMs));
        List<Cue> merged = new ArrayList<>();
        for (Cue cue : cues) {
            if (!merged.isEmpty() && merged.get(merged.size() - 1).timeMs == cue.timeMs) {
                Cue old = merged.remove(merged.size() - 1);
                merged.add(new Cue(old.timeMs, old.text + "\\N" + cue.text));
            } else {
                merged.add(cue);
            }
        }
        return merged;
    }

    private Cue currentCue(List<Cue> cues, double seconds) {
        Cue current = null;
        for (Cue cue : cues) {
            if (cue.timeMs / 1000.0 <= seconds) current = cue;
            else break;
        }
        return current;
    }

    private void startExport() {
        if (exportTask != null && exportTask.isRunning()) {
            exportCancelRequested.set(true);
            exportTask.cancel(true);
            return;
        }
        File output = currentOutputFile();
        if (output == null || output.getPath().isBlank()) {
            alert("请选择视频输出路径。");
            return;
        }
        String mode = backgroundTypeCombo.getValue();
        if (("自定义图片".equals(mode) || "自定义视频".equals(mode))
                && customBackground == null) {
            alert("请先选择自定义背景文件。");
            return;
        }
        if ("专辑封面".equals(mode) && albumCoverImage == null) {
            alert("当前歌曲没有可用的专辑封面。");
            return;
        }
        File exportBackground = customBackground;
        if ("专辑封面".equals(mode)) {
            try {
                exportBackground = writeAlbumCoverFile(albumCoverImage);
            } catch (Exception ex) {
                alert("专辑封面导出失败：" + ex.getMessage());
                return;
            }
        }
        List<LyricLine> lyrics = new ArrayList<>();
        for (LyricLine line : doc.getLines()) lyrics.add(line.copy());

        LyricVideoExporter.BackgroundMode bgMode = switch (mode) {
            case "自定义图片" -> LyricVideoExporter.BackgroundMode.CUSTOM_IMAGE;
            case "自定义视频" -> LyricVideoExporter.BackgroundMode.CUSTOM_VIDEO;
            case "专辑封面" -> LyricVideoExporter.BackgroundMode.ALBUM_COVER;
            case "内置动态背景" -> LyricVideoExporter.BackgroundMode.BUILTIN_DYNAMIC;
            default -> LyricVideoExporter.BackgroundMode.BUILTIN_STATIC;
        };
        LyricVideoExporter.LyricEffect effect = switch (subEffectCombo.getValue()) {
            case "直接显示模式" -> LyricVideoExporter.LyricEffect.DIRECT_FLICKER;
            case "单句渐显渐隐模式" -> LyricVideoExporter.LyricEffect.DIRECT_FADE;
            case "左右滚动模式" -> LyricVideoExporter.LyricEffect.SCROLL_HORIZONTAL;
            default -> LyricVideoExporter.LyricEffect.SCROLL_VERTICAL;
        };
        int[] size = parseResolution(resolutionCombo.getValue());
        double duration = LyricVideoExporter.estimateDurationSeconds(
                lyrics, audioDurationSeconds);
        java.awt.Color awtColor = toAwt(textColorPicker.getValue());
        int fontSize = parseOptionalInt(fontSizeField, 0);
        int lineSpacing = parseOptionalInt(lineSpacingField, -1);
        int nonCurrentReduction = parseOptionalInt(nonCurrentShrinkField, -1);
        double scrollSpeed = parseOptionalDouble(scrollSpeedField, 0);
        LyricVideoExporter.TextureMode textureMode = switch (textureModeCombo.getValue()) {
            case "暗色+玻璃磨砂" -> LyricVideoExporter.TextureMode.GLASS_DARK;
            default -> LyricVideoExporter.TextureMode.NONE;
        };
        int blurPercent = (int) Math.round(blurSlider.getValue());
        int grainPercent = (int) Math.round(grainSlider.getValue());
        int darkOpacityPercent = (int) Math.round(darkOpacitySlider.getValue());
        double trimStart = trimToggle.isSelected()
                ? trimRangeControl.getStartSeconds() : 0;
        double trimEnd = trimToggle.isSelected()
                ? trimRangeControl.getEndSeconds() : duration;

        LyricVideoExporter.Settings settings = new LyricVideoExporter.Settings(
                output, audioFile, lyrics, size[0], size[1], fpsCombo.getValue(),
                effect, bgMode, backgroundPresetCombo.getValue(),
                exportBackground, duration, awtColor, fontSize, lineSpacing,
                nonCurrentReduction, textureMode, blurPercent,
                grainPercent, darkOpacityPercent, trimStart, trimEnd, scrollSpeed,
                albumCoverFitCombo.getValue());

        exportCancelRequested.set(false);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        exportButton.setText("取消导出");
        statusLabel.setText("正在生成视频…");
        statusLabel.setStyle("");
        setControlsDisabled(true);

        exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                LyricVideoExporter.export(
                        settings,
                        value -> updateProgress(
                                Math.max(0, Math.min(1, value)), 1.0),
                        exportCancelRequested::get);
                return null;
            }
        };
        exportTask.setOnSucceeded(e -> {
            progressBar.setProgress(1);
            exportButton.setText("开始生成视频");
            statusLabel.setText("导出完成：" + output.getName());
            setControlsDisabled(false);
        });
        exportTask.setOnCancelled(e -> {
            exportButton.setText("开始生成视频");
            progressBar.setVisible(false);
            statusLabel.setText("--已取消--");
            statusLabel.setStyle("-fx-text-fill:#ff3b30;-fx-font-weight:700;");
            setControlsDisabled(false);
        });
        exportTask.setOnFailed(e -> {
            exportButton.setText("开始生成视频");
            statusLabel.setText("导出失败");
            setControlsDisabled(false);
            Throwable ex = exportTask.getException();
            alert(ex == null ? "视频导出失败。" : ex.getMessage());
        });
        Thread thread = new Thread(exportTask, "lrc-video-export");
        thread.setDaemon(true);
        thread.start();
    }

    private void setControlsDisabled(boolean disabled) {
        mainEffectCombo.setDisable(disabled);
        subEffectCombo.setDisable(disabled);
        backgroundTypeCombo.setDisable(disabled);
        backgroundPresetCombo.setDisable(disabled);
        albumCoverFitCombo.setDisable(disabled);
        chooseBackgroundButton.setDisable(disabled);
        resolutionCombo.setDisable(disabled);
        fpsCombo.setDisable(disabled);
        textColorPicker.setDisable(disabled);
        fontSizeField.setDisable(disabled);
        lineSpacingField.setDisable(disabled);
        nonCurrentShrinkField.setDisable(disabled);
        scrollSpeedField.setDisable(disabled);
        resetButton.setDisable(disabled);
        textureModeCombo.setDisable(disabled);
        textureResetButton.setDisable(disabled);
        blurSlider.setDisable(disabled);
        grainSlider.setDisable(disabled);
        darkOpacitySlider.setDisable(disabled);
        outputField.setDisable(disabled);
        if (!disabled) {
            updateBackgroundOptions();
        }
    }

    private int[] parseResolution(String text) {
        String[] parts = text.replace("×", "x").split("x");
        return new int[]{
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim())
        };
    }

    private int parseOptionalInt(TextField field, int defaultValue) {
        String text = field.getText();
        if (text == null || text.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private double parseOptionalDouble(TextField field, double defaultValue) {
        String text = field.getText();
        if (text == null || text.isBlank()) return defaultValue;
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String formatTime(double seconds) {
        long totalMs = Math.max(0, Math.round(seconds * 1000));
        long ms = totalMs % 1000;
        long totalSeconds = totalMs / 1000;
        long s = totalSeconds % 60;
        long totalMinutes = totalSeconds / 60;
        long m = totalMinutes % 60;
        long h = totalMinutes / 60;
        return String.format(Locale.ROOT, "%d:%02d:%02d.%03d", h, m, s, ms);
    }

    private java.awt.Color toAwt(Color color) {
        return new java.awt.Color(
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    private File writeAlbumCoverFile(Image image) throws Exception {
        int width = (int) Math.round(image.getWidth());
        int height = (int) Math.round(image.getHeight());
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("封面尺寸无效");
        }
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(
                0, 0, width, height,
                javafx.scene.image.PixelFormat.getIntArgbInstance(),
                pixels, 0, width);
        BufferedImage output = new BufferedImage(
                width, height, BufferedImage.TYPE_INT_ARGB);
        output.setRGB(0, 0, width, height, pixels, 0, width);
        File file = Files.createTempFile("lrc-album-cover-", ".png").toFile();
        file.deleteOnExit();
        ImageIO.write(output, "png", file);
        return file;
    }

    private boolean isImage(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
    }

    private boolean isVideo(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".mp4") || name.endsWith(".mkv");
    }

    private void alert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.initOwner(stage == null ? owner : stage);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private record Cue(long timeMs, String text) {
    }

    private record PreviewMetrics(
            double currentFontSize,
            double nonCurrentFontSize,
            double lineSpacing,
            double currentLineHeight,
            double nonCurrentLineHeight,
            double gap,
            double previousCenter,
            double currentCenter,
            double nextCenter) {
    }
}
