package com.lrceditor;

import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;

public class LrcEditorApp extends Application {

    private static final double WAVE_WINDOW_SEC = 10.0;
    private static final double PLAYHEAD_LERP  = 0.25;
    private static final double CENTER_LERP    = 0.15;
    private static final double PLAY_LERP      = 0.30;
    private static final double ANIM_EPSILON   = 0.005;

    private static final double END_REWIND_GAP_SEC    = WAVE_WINDOW_SEC / 2.0;
    private static final double END_REWIND_OMEGA      = 4.0;
    private static final double END_REWIND_DAMPING    = 0.68;
    private static final double END_REWIND_MIN_TIME_S = 0.75;

    private static final double DEFAULT_BORDER_OPACITY = 0.5;

    private static final double TOP_BAR_HEIGHT = 104;
    private static final double COVER_SIZE = 84;
    private static final double WAVE_HEIGHT = 150;
    private static final double SIDEBAR_WIDTH = 238;
    private static final double PREVIEW_ROW_HEIGHT = 20;
    private static final double PREVIEW_CURRENT_SCALE = 1.24;
    private static final double PREVIEW_TEXT_INSET = 6;
    private static final String PREF_LAST_DIRECTORY = "lastDirectory";
    private static final String PREF_PREVIEW_PLAYING_COLOR = "previewPlayingColor";

    private static final double SPEED_STOP  = 0.0;
    private static final double SPEED_SLOW  = 45.0;
    private static final double SPEED_MED   = 72.0;
    private static final double SPEED_FAST  = 144.0;

    // ============ 全局状态 ============
    private final LrcDocument doc = new LrcDocument();
    private final Preferences preferences = Preferences.userNodeForPackage(LrcEditorApp.class);
    private Color previewPlayingColor = loadPreviewPlayingColor();
    private File audioFile;
    private MediaPlayer mediaPlayer;
    private Waveform waveform;
    private ExecutorService bgExecutor;
    private boolean bomOnExport = false;
    private double  borderOpacity = DEFAULT_BORDER_OPACITY;

    // ============ 波形视图动画状态 ============
    private double  viewCenterSec      = 0;
    private double  displayPlayheadSec = 0;
    private boolean clickAnimating     = false;
    private int     clickPhase         = 0;
    private double  clickTargetSec     = 0;
    private boolean endRewindAnimating = false;
    private double  endRewindVelocity  = 0;
    private double  endRewindTargetSec = 0;
    private double  endRewindPeriodSec = 0;
    private double  endRewindAudioEnd  = 0;
    private long    endRewindStartNanos = 0;

    // ============ 唱片旋转状态 ============
    private StackPane recordPane;
    private Rotate    recordRotation;
    private double    recordAngle = 0;
    private long      lastFrameNanos = 0;

    // ============ 封面设置 ============
    private boolean coverShapeCircular = true;
    private boolean highlightEnabled   = true;
    private double  recordDegPerSec    = SPEED_MED;

    // ============ UI 组件 ============
    private Label songTitleLabel, timeLabel, offsetLabel, saveStatusLabel, fileNamesLabel, encodeLabel;
    private Label previewPrevLabel, previewCurrentLabel, previewNextLabel, previewAfterNextLabel;
    private StackPane lyricPreviewViewport;
    private VBox lyricPreviewBox;
    private Timeline lyricPreviewTimeline;
    private String pendingPreviewPrev;
    private String pendingPreviewCurrent;
    private String pendingPreviewNext;
    private String pendingPreviewAfter;
    private Long previewTimestampMs;
    private boolean previewInitialized = false;
    private ImageView coverView;
    private LyricEditorView lyricView;
    private Canvas waveCanvas;
    private Button playPauseBtn;
    private Button chunkToggleBtn;
    private Button syncJumpBtn;
    private Button autoSelectBtn;
    private ProgressBar waveProgress;

    private Menu          rotationSpeedMenu;
    private CheckMenuItem recordHighlightItem;

    // =====================================================================
    // 生命周期
    // =====================================================================
    @Override
    public void start(Stage stage) {
        bgExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "lrceditor-bg");
            t.setDaemon(true);
            return t;
        });

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(buildTop(stage));
        root.setLeft(buildLeft(stage));
        root.setCenter(buildCenter());
        root.setBottom(buildBottom());

        if (lyricView != null) lyricView.setBorderOpacity(borderOpacity);

        Rectangle2D vb = Screen.getPrimary().getVisualBounds();
        double sceneW = Math.min(1320, Math.max(900, vb.getWidth()  - 48));
        double sceneH = Math.min(980,  Math.max(620, vb.getHeight() - 72));

        Scene scene = new Scene(root, sceneW, sceneH);
        var stylesheet = LrcEditorApp.class.getResource("/styles/app.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }
        installAccelerators(scene, stage);

        stage.setTitle("LRC 歌词编辑器");
        stage.setScene(scene);
        var windowIcon = LrcEditorApp.class.getResource("/icons/lrc-editor-256.png");
        if (windowIcon != null) {
            stage.getIcons().add(new Image(windowIcon.toExternalForm()));
        }
        stage.setResizable(true);
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.centerOnScreen();
        stage.show();

        updateCoverMenuState();
        updateLyricPreview(0, true);

        new AnimationTimer() {
            @Override public void handle(long now) {
                if (lastFrameNanos == 0) lastFrameNanos = now;
                double dtSec = (now - lastFrameNanos) / 1_000_000_000.0;
                lastFrameNanos = now;

                if (coverShapeCircular
                        && recordDegPerSec > 0
                        && mediaPlayer != null
                        && mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING
                        && recordRotation != null) {
                    recordAngle = (recordAngle + recordDegPerSec * dtSec) % 360;
                    recordRotation.setAngle(recordAngle);
                }

                if (mediaPlayer == null
                        || mediaPlayer.getStatus() == MediaPlayer.Status.DISPOSED) {
                    drawWaveform(viewCenterSec, displayPlayheadSec);
                    return;
                }

                if (endRewindAnimating) {
                    tickEndRewindAnimation(dtSec);
                    drawWaveform(viewCenterSec, displayPlayheadSec);
                    return;
                }

                Duration cur;
                Duration tot;
                try {
                    cur = mediaPlayer.getCurrentTime();
                    tot = mediaPlayer.getTotalDuration();
                } catch (RuntimeException ex) {
                    return;
                }
                if (tot != null && tot.greaterThan(Duration.ZERO)) {
                    timeLabel.setText(LrcIo.formatDuration(cur.toSeconds())
                                              + " / " + LrcIo.formatDuration(tot.toSeconds()));
                }

                double realPlaySec = cur.toSeconds();
                updateLyricPreview(realPlaySec, false);
                tickWaveAnimation(realPlaySec);
                drawWaveform(viewCenterSec, displayPlayheadSec);

                if (lyricView.isAutoScrollToPlaying()) {
                    Set<Integer> newSet = new HashSet<>(doc.findPlayingLineIndices(realPlaySec));
                    lyricView.setPlayingIndices(newSet);
                }
            }
        }.start();

        stage.setOnCloseRequest(e -> {
            if (!confirmDiscard()) { e.consume(); return; }
            if (mediaPlayer != null) mediaPlayer.dispose();
            bgExecutor.shutdownNow();
            Platform.exit();
        });
    }

    public static void main(String[] args) { launch(args); }

    // =====================================================================
    // 波形视图动画状态机
    // =====================================================================
    private void tickWaveAnimation(double realPlaySec) {
        if (clickAnimating) {
            if (clickPhase == 0) {
                double diff = clickTargetSec - displayPlayheadSec;
                if (Math.abs(diff) < ANIM_EPSILON) {
                    displayPlayheadSec = clickTargetSec;
                    clickPhase = 1;
                } else {
                    displayPlayheadSec += diff * PLAYHEAD_LERP;
                }
            } else {
                displayPlayheadSec = clickTargetSec;
                double diff = clickTargetSec - viewCenterSec;
                if (Math.abs(diff) < ANIM_EPSILON) {
                    viewCenterSec = clickTargetSec;
                    clickAnimating = false;
                } else {
                    viewCenterSec += diff * CENTER_LERP;
                }
            }
            return;
        }

        viewCenterSec     += (realPlaySec - viewCenterSec) * PLAY_LERP;
        displayPlayheadSec = realPlaySec;
    }

    // =====================================================================
    // 播放结束后的循环波形回弹
    // =====================================================================
    private void startEndRewindAnimation(double totalSec) {
        if (totalSec <= 0 || Double.isNaN(totalSec) || Double.isInfinite(totalSec)) {
            return;
        }

        clickAnimating = false;
        endRewindAnimating = true;
        endRewindAudioEnd  = totalSec;
        // A half-window tail separates the end from the next loop, so the first
        // waveform enters from the right edge instead of popping into view.
        endRewindTargetSec = totalSec + END_REWIND_GAP_SEC;
        endRewindPeriodSec = endRewindTargetSec;
        endRewindVelocity  = 0.9;
        endRewindStartNanos = System.nanoTime();

        viewCenterSec = Math.max(0, Math.min(viewCenterSec, totalSec));
        displayPlayheadSec = viewCenterSec;
        timeLabel.setText(LrcIo.formatDuration(totalSec)
                                  + " / " + LrcIo.formatDuration(totalSec));
    }

    private void tickEndRewindAnimation(double dtSec) {
        double dt = Math.max(0, Math.min(dtSec, 0.05));
        double displacement = viewCenterSec - endRewindTargetSec;
        double acceleration = -(END_REWIND_OMEGA * END_REWIND_OMEGA) * displacement
                              - (2.0 * END_REWIND_DAMPING * END_REWIND_OMEGA)
                                * endRewindVelocity;

        endRewindVelocity += acceleration * dt;
        viewCenterSec += endRewindVelocity * dt;
        displayPlayheadSec = viewCenterSec;

        long elapsedNanos = System.nanoTime() - endRewindStartNanos;
        double remaining = viewCenterSec - endRewindTargetSec;
        boolean settled = Math.abs(remaining) < 0.02
                          && Math.abs(endRewindVelocity) < 0.08;
        if ((settled && elapsedNanos > END_REWIND_MIN_TIME_S * 1_000_000_000L)
                || elapsedNanos > 4_000_000_000L) {
            finishEndRewindAnimation();
        }
    }

    private void cancelEndRewindAnimation() {
        endRewindAnimating = false;
        endRewindVelocity  = 0;
        endRewindTargetSec = 0;
        endRewindPeriodSec = 0;
        endRewindAudioEnd  = 0;
        endRewindStartNanos = 0;
    }

    private void finishEndRewindAnimation() {
        cancelEndRewindAnimation();
        viewCenterSec = 0;
        displayPlayheadSec = 0;

        if (mediaPlayer != null) {
            mediaPlayer.seek(Duration.ZERO);
            Duration total = mediaPlayer.getTotalDuration();
            if (total != null && total.greaterThan(Duration.ZERO)) {
                timeLabel.setText("00:00.000 / " + LrcIo.formatDuration(total.toSeconds()));
            }
        }
    }

    private double mapWaveformTime(double timelineSec) {
        if (!endRewindAnimating || endRewindPeriodSec <= 0) {
            return timelineSec;
        }

        // The virtual loop is [audio end + silent tail], then wraps back to 0.
        double wrapped = timelineSec % endRewindPeriodSec;
        if (wrapped < 0) wrapped += endRewindPeriodSec;
        return wrapped < endRewindAudioEnd ? wrapped : -1;
    }

    // =====================================================================
    // 快捷键
    // =====================================================================
    private void installAccelerators(Scene scene, Stage stage) {
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN),
                () -> saveLrc(false, stage));
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                () -> saveLrc(true, stage));
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN),
                () -> importLrc(stage));
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                () -> importAudio(stage));
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN),
                () -> { doc.undo(); refreshAll(); });
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN),
                () -> { doc.redo(); refreshAll(); });
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.Z, KeyCombination.META_DOWN),
                () -> { doc.undo(); refreshAll(); });
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.Y, KeyCombination.META_DOWN),
                () -> { doc.redo(); refreshAll(); });

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN),
                () -> lyricView.selectAllLyricLines());

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN),
                this::stampSelectedLines);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                this::shiftSelectedTimestamps);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN),
                this::insertNewLine);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.D, KeyCombination.CONTROL_DOWN),
                this::deleteSelectedLines);
    }

    // =====================================================================
    // 顶部
    // =====================================================================
    private VBox buildTop(Stage stage) {
        recordPane = buildRecordCover();

        songTitleLabel = new Label("未载入歌曲");
        songTitleLabel.getStyleClass().add("song-title");
        songTitleLabel.setMaxWidth(Double.MAX_VALUE);

        timeLabel = new Label("00:00.000 / 00:00.000");
        timeLabel.getStyleClass().add("song-time");

        VBox info = new VBox(6, songTitleLabel, timeLabel);
        info.setAlignment(Pos.CENTER_LEFT);
        info.setMinWidth(250);
        info.setPrefWidth(270);
        info.setMaxWidth(270);
        HBox.setHgrow(info, Priority.ALWAYS);

        Region preview = buildLyricPreview();
        HBox.setHgrow(preview, Priority.ALWAYS);

        Separator divider = new Separator(Orientation.VERTICAL);
        divider.getStyleClass().add("header-divider");
        divider.setMinHeight(54);
        divider.setMaxHeight(64);

        HBox songInfo = new HBox(18, recordPane, info, divider, preview);
        songInfo.getStyleClass().add("app-header");
        songInfo.setAlignment(Pos.CENTER_LEFT);
        songInfo.setPadding(new Insets(10, 24, 10, 24));
        songInfo.setPrefHeight(TOP_BAR_HEIGHT);
        songInfo.setMinHeight(TOP_BAR_HEIGHT);
        songInfo.setMaxHeight(TOP_BAR_HEIGHT);

        return new VBox(buildMenuBar(stage), songInfo);
    }

    private Region buildLyricPreview() {
        lyricPreviewBox = createLyricPreviewContent("", "暂无歌词", "", "");
        StackPane.setAlignment(lyricPreviewBox, Pos.TOP_LEFT);
        lyricPreviewViewport = new StackPane(lyricPreviewBox);
        lyricPreviewViewport.getStyleClass().add("lyric-preview-area");
        lyricPreviewViewport.setAlignment(Pos.CENTER_LEFT);
        lyricPreviewViewport.setMinHeight(70);
        lyricPreviewViewport.setPrefHeight(70);
        lyricPreviewViewport.setMaxHeight(70);
        lyricPreviewViewport.setMaxWidth(Double.MAX_VALUE);

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(lyricPreviewViewport.widthProperty());
        clip.heightProperty().bind(lyricPreviewViewport.heightProperty());
        lyricPreviewViewport.setClip(clip);
        return lyricPreviewViewport;
    }

    private VBox createLyricPreviewContent(
            String previousText, String currentText, String nextText, String afterText) {
        previewPrevLabel = previewLine(false);
        previewCurrentLabel = previewLine(true);
        previewNextLabel = previewLine(false);
        previewAfterNextLabel = previewLine(false);
        previewPrevLabel.setText(previousText);
        previewCurrentLabel.setText(currentText);
        previewNextLabel.setText(nextText);
        previewAfterNextLabel.setText(afterText);

        VBox content = new VBox(0,
                previewPrevLabel, previewCurrentLabel,
                previewNextLabel, previewAfterNextLabel);
        content.setAlignment(Pos.TOP_LEFT);
        content.setMaxWidth(Double.MAX_VALUE);
        return content;
    }

    private Label previewLine(boolean current) {
        Label label = new Label("");
        label.getStyleClass().add("lyric-preview-line");
        label.setMinHeight(PREVIEW_ROW_HEIGHT);
        label.setPrefHeight(PREVIEW_ROW_HEIGHT);
        label.setMaxHeight(PREVIEW_ROW_HEIGHT);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setPadding(new Insets(0, PREVIEW_TEXT_INSET, 0, PREVIEW_TEXT_INSET));
        label.setTextOverrun(OverrunStyle.ELLIPSIS);
        label.setAlignment(Pos.CENTER_LEFT);
        label.setTextFill(current ? previewPlayingColor : Color.web("#94a3b8"));
        double scale = current ? PREVIEW_CURRENT_SCALE : 1.0;
        Scale scaleTransform = new Scale(
                scale, scale, PREVIEW_TEXT_INSET, PREVIEW_ROW_HEIGHT / 2.0);
        label.getTransforms().add(scaleTransform);
        label.setUserData(scaleTransform);
        return label;
    }

    private void updateLyricPreview(double sec, boolean force) {
        if (lyricPreviewBox == null) return;

        Long active = activeLyricTimestamp(sec);
        if (!force && previewInitialized && java.util.Objects.equals(active, previewTimestampMs)) {
            return;
        }

        finishLyricPreviewTransition();

        boolean animate = previewInitialized;
        previewInitialized = true;
        previewTimestampMs = active;

        Long previous = adjacentLyricTimestamp(active, false);
        Long next = adjacentLyricTimestamp(active, true);
        Long afterNext = next == null ? null : adjacentLyricTimestamp(next, true);

        String currentText;
        if (active != null) {
            currentText = lyricTextAt(active);
        } else if (adjacentLyricTimestamp(null, true) != null && sec <= 0.001) {
            currentText = "前奏";
        } else {
            currentText = "暂无歌词";
        }

        String previousText = previous == null ? "" : lyricTextAt(previous);
        String nextText = next == null ? "" : lyricTextAt(next);
        String afterText = afterNext == null ? "" : lyricTextAt(afterNext);

        if (previewInitialized
                && java.util.Objects.equals(previousText, previewPrevLabel.getText())
                && java.util.Objects.equals(currentText, previewCurrentLabel.getText())
                && java.util.Objects.equals(nextText, previewNextLabel.getText())
                && java.util.Objects.equals(afterText, previewAfterNextLabel.getText())) {
            return;
        }

        if (!animate) {
            replaceLyricPreviewContent(previousText, currentText, nextText, afterText);
            return;
        }

        boolean sequential = java.util.Objects.equals(previousText, previewCurrentLabel.getText())
                             && java.util.Objects.equals(currentText, previewNextLabel.getText());
        if (sequential) {
            animateLyricPreviewRoll(previousText, currentText, nextText, afterText);
        } else {
            settleLyricPreviewContent(previousText, currentText, nextText, afterText);
        }
    }

    private void animateLyricPreviewRoll(
            String previousText, String currentText, String nextText, String afterText) {
        pendingPreviewPrev = previousText;
        pendingPreviewCurrent = currentText;
        pendingPreviewNext = nextText;
        pendingPreviewAfter = afterText;
        lyricPreviewBox.setTranslateY(0);

        Label outgoingCurrent = previewCurrentLabel;
        Label incomingCurrent = previewNextLabel;
        Scale outgoingScale = (Scale) outgoingCurrent.getUserData();
        Scale incomingScale = (Scale) incomingCurrent.getUserData();
        Color dark = previewPlayingColor;
        Color muted = Color.web("#94a3b8");

        lyricPreviewTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(lyricPreviewBox.translateYProperty(), 0),
                        new KeyValue(outgoingScale.xProperty(), PREVIEW_CURRENT_SCALE),
                        new KeyValue(outgoingScale.yProperty(), PREVIEW_CURRENT_SCALE),
                        new KeyValue(outgoingCurrent.textFillProperty(), dark),
                        new KeyValue(incomingScale.xProperty(), 1.0),
                        new KeyValue(incomingScale.yProperty(), 1.0),
                        new KeyValue(incomingCurrent.textFillProperty(), muted)),
                new KeyFrame(Duration.millis(220),
                        new KeyValue(lyricPreviewBox.translateYProperty(),
                                -PREVIEW_ROW_HEIGHT, Interpolator.EASE_BOTH),
                        new KeyValue(outgoingScale.xProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(outgoingScale.yProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(outgoingCurrent.textFillProperty(), muted, Interpolator.EASE_BOTH),
                        new KeyValue(incomingScale.xProperty(),
                                PREVIEW_CURRENT_SCALE, Interpolator.EASE_BOTH),
                        new KeyValue(incomingScale.yProperty(),
                                PREVIEW_CURRENT_SCALE, Interpolator.EASE_BOTH),
                        new KeyValue(incomingCurrent.textFillProperty(),
                                dark, Interpolator.EASE_BOTH)));
        lyricPreviewTimeline.setOnFinished(e -> finishLyricPreviewTransition());
        lyricPreviewTimeline.play();
    }

    private void settleLyricPreviewContent(
            String previousText, String currentText, String nextText, String afterText) {
        cancelLyricPreviewTransition();
        replaceLyricPreviewContent(previousText, currentText, nextText, afterText);
        lyricPreviewBox.setTranslateY(4);
        lyricPreviewTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(lyricPreviewBox.translateYProperty(), 4)),
                new KeyFrame(Duration.millis(150),
                        new KeyValue(lyricPreviewBox.translateYProperty(),
                                0, Interpolator.EASE_BOTH)));
        lyricPreviewTimeline.setOnFinished(e -> {
            lyricPreviewBox.setTranslateY(0);
            lyricPreviewTimeline = null;
        });
        lyricPreviewTimeline.play();
    }

    private void replaceLyricPreviewContent(
            String previousText, String currentText, String nextText, String afterText) {
        VBox content = createLyricPreviewContent(
                previousText, currentText, nextText, afterText);
        StackPane.setAlignment(content, Pos.TOP_LEFT);
        lyricPreviewViewport.getChildren().setAll(content);
        lyricPreviewBox = content;
    }

    private void cancelLyricPreviewTransition() {
        if (lyricPreviewTimeline != null) {
            lyricPreviewTimeline.stop();
            lyricPreviewTimeline = null;
        }
        clearPendingLyricPreview();
        if (lyricPreviewBox != null) {
            lyricPreviewBox.setTranslateY(0);
        }
    }

    private void finishLyricPreviewTransition() {
        if (lyricPreviewTimeline != null) {
            lyricPreviewTimeline.stop();
            lyricPreviewTimeline = null;
        }
        if (pendingPreviewPrev != null) {
            replaceLyricPreviewContent(
                    pendingPreviewPrev, pendingPreviewCurrent,
                    pendingPreviewNext, pendingPreviewAfter);
        }
        clearPendingLyricPreview();
        if (lyricPreviewBox != null) {
            lyricPreviewBox.setTranslateY(0);
        }
    }

    private void clearPendingLyricPreview() {
        pendingPreviewPrev = null;
        pendingPreviewCurrent = null;
        pendingPreviewNext = null;
        pendingPreviewAfter = null;
    }

    private Long activeLyricTimestamp(double sec) {
        List<Integer> indices = doc.findPlayingLineIndices(sec);
        for (int index : indices) {
            if (index >= 0 && index < doc.getLines().size()) {
                LyricLine line = doc.getLines().get(index);
                if (line.timeMs != null && !line.text.isBlank()) {
                    return line.timeMs;
                }
            }
        }
        return null;
    }

    private Long adjacentLyricTimestamp(Long active, boolean forward) {
        Long candidate = null;
        for (LyricLine line : doc.getLines()) {
            if (line.timeMs == null || line.text.isBlank()) continue;

            if (active == null) {
                if (candidate == null
                        || (forward ? line.timeMs < candidate : line.timeMs > candidate)) {
                    candidate = line.timeMs;
                }
            } else if (forward && line.timeMs > active) {
                if (candidate == null || line.timeMs < candidate) candidate = line.timeMs;
            } else if (!forward && line.timeMs < active) {
                if (candidate == null || line.timeMs > candidate) candidate = line.timeMs;
            }
        }
        return candidate;
    }

    private String lyricTextAt(long timestampMs) {
        StringBuilder text = new StringBuilder();
        for (LyricLine line : doc.getLines()) {
            if (line.timeMs != null
                    && line.timeMs.longValue() == timestampMs
                    && !line.text.isBlank()) {
                if (text.length() > 0) text.append("  /  ");
                text.append(line.text.trim());
            }
        }
        return text.toString();
    }

    private StackPane buildRecordCover() {
        return coverShapeCircular ? buildCircularCover() : buildSquareCover();
    }

    private StackPane buildCircularCover() {
        double size    = COVER_SIZE;
        double rOuter  = size / 2;
        double rCover  = rOuter - 4;
        double rHole   = 5;

        Circle vinyl = new Circle(rOuter);
        vinyl.setFill(Color.web("#1a1a1a"));
        vinyl.setStroke(Color.web("#333333"));
        vinyl.setStrokeWidth(1);

        Circle g1 = groove(rOuter * 0.95);
        Circle g2 = groove(rOuter * 0.88);
        Circle g3 = groove(rOuter * 0.81);

        coverView = new ImageView();
        coverView.setFitWidth(rCover * 2);
        coverView.setFitHeight(rCover * 2);
        coverView.setPreserveRatio(true);
        coverView.setSmooth(true);

        Circle clip = new Circle(rCover, rCover, rCover);
        coverView.setClip(clip);

        Circle coverRing = new Circle(rCover);
        coverRing.setFill(Color.TRANSPARENT);
        coverRing.setStroke(Color.web("#0a0a0a"));
        coverRing.setStrokeWidth(1.5);

        Circle hole = new Circle(rHole);
        hole.setFill(Color.web("#f7f7f9"));
        hole.setStroke(Color.web("#0a0a0a"));
        hole.setStrokeWidth(1);

        StackPane pane = new StackPane(vinyl, g1, g2, g3, coverView, coverRing, hole);
        pane.getStyleClass().add("record-cover");

        if (highlightEnabled) {
            Circle highlight = new Circle(rOuter);
            highlight.setFill(new LinearGradient(
                    0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0.00, Color.rgb(255, 255, 255, 0.22)),
                    new Stop(0.35, Color.rgb(255, 255, 255, 0.06)),
                    new Stop(0.55, Color.rgb(255, 255, 255, 0.00)),
                    new Stop(1.00, Color.rgb(0,   0,   0,   0.00))));
            highlight.setMouseTransparent(true);
            pane.getChildren().add(highlight);
        }

        pane.setPrefSize(size, size);
        pane.setMinSize(size, size);
        pane.setMaxSize(size, size);

        recordRotation = new Rotate(0, size / 2, size / 2);
        pane.getTransforms().add(recordRotation);

        return pane;
    }

    private StackPane buildSquareCover() {
        double size = COVER_SIZE;

        coverView = new ImageView();
        coverView.setFitWidth(size);
        coverView.setFitHeight(size);
        coverView.setPreserveRatio(true);
        coverView.setSmooth(true);

        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        coverView.setClip(clip);

        StackPane pane = new StackPane(coverView);
        pane.getStyleClass().add("record-cover");
        pane.setPrefSize(size, size);
        pane.setMinSize(size, size);
        pane.setMaxSize(size, size);
        pane.setStyle("-fx-background-color:#2b2b2b;-fx-background-radius:8;");

        recordRotation = new Rotate(0, size / 2, size / 2);
        pane.getTransforms().add(recordRotation);

        return pane;
    }

    private Circle groove(double r) {
        Circle c = new Circle(r);
        c.setFill(Color.TRANSPARENT);
        c.setStroke(Color.web("#2a2a2a"));
        c.setStrokeWidth(1);
        return c;
    }

    private void applyCoverShape(boolean circular) {
        coverShapeCircular = circular;

        StackPane newPane = buildRecordCover();

        if (recordPane != null && recordPane.getParent() instanceof HBox parent) {
            int idx = parent.getChildren().indexOf(recordPane);
            if (idx >= 0) {
                parent.getChildren().set(idx, newPane);
            }
        }
        recordPane = newPane;

        resetRecordRotation();

        File ref = audioFile != null ? audioFile : doc.getFile();
        if (ref != null) loadCover(ref);

        updateCoverMenuState();
    }

    private void updateCoverMenuState() {
        if (rotationSpeedMenu != null) {
            rotationSpeedMenu.setDisable(!coverShapeCircular);
        }
        if (recordHighlightItem != null) {
            recordHighlightItem.setDisable(!coverShapeCircular);
        }
    }

    private MenuBar buildMenuBar(Stage stage) {
        Menu file = new Menu("文件");
        add(file, "新建",                       () -> { if (confirmDiscard()) { doc.clear(); refreshAll(); } });
        add(file, "导入歌词…  (Ctrl+O)",         () -> importLrc(stage));
        add(file, "导入音频…  (Ctrl+Shift+O)",   () -> importAudio(stage));
        file.getItems().add(new SeparatorMenuItem());
        add(file, "保存  (Ctrl+S)",              () -> saveLrc(false, stage));
        add(file, "另存为…  (Ctrl+Shift+S)",     () -> saveLrc(true, stage));
        file.getItems().add(new SeparatorMenuItem());
        add(file, "退出",                        () -> { if (confirmDiscard()) { bgExecutor.shutdownNow(); Platform.exit(); } });

        Menu edit = new Menu("编辑");
        add(edit, "撤销  (Ctrl+Z)",              () -> { doc.undo(); refreshAll(); });
        add(edit, "重做  (Ctrl+Y)",              () -> { doc.redo(); refreshAll(); });
        edit.getItems().add(new SeparatorMenuItem());
        add(edit, "全选  (Ctrl+A)",              () -> lyricView.selectAllLyricLines());
        edit.getItems().add(new SeparatorMenuItem());
        add(edit, "生成时间标记（覆盖选中行）  (Ctrl+T)", this::stampSelectedLines);
        add(edit, "跳转到光标处时间戳",                   this::jumpToTimestamp);
        edit.getItems().add(new SeparatorMenuItem());
        add(edit, "插入新行  (Ctrl+N)",                   this::insertNewLine);
        add(edit, "删除选中行  (Ctrl+D)",                 this::deleteSelectedLines);
        add(edit, "批量偏移选中时间戳…  (Ctrl+Shift+T)", this::shiftSelectedTimestamps);
        edit.getItems().add(new SeparatorMenuItem());
        add(edit, "修改 offset",                          this::showOffsetDialog);
        add(edit, "时间分块 / 统合",                      () -> { doc.toggleTimeChunk(); refreshAll(); });

        Menu view = new Menu("视图");

        CheckMenuItem bomItem = new CheckMenuItem("导出时写入 UTF-8 BOM");
        bomItem.selectedProperty().addListener((o, ov, nv) -> bomOnExport = nv);
        view.getItems().add(bomItem);

        view.getItems().add(new SeparatorMenuItem());

        view.getItems().add(buildOpacityMenu(
                "输入框边框可见度", borderOpacity,
                op -> { borderOpacity = op; if (lyricView != null) lyricView.setBorderOpacity(op); }));

        view.getItems().add(buildOpacityMenu(
                "选中高亮可见度（蓝）", 1.0,
                op -> { if (lyricView != null) lyricView.setSelectedHighlightOpacity(op); }));

        view.getItems().add(buildOpacityMenu(
                "播放高亮可见度（黄）", 1.0,
                op -> { if (lyricView != null) lyricView.setPlayingHighlightOpacity(op); }));

        view.getItems().add(buildPreviewPlayingColorMenu());

        view.getItems().add(new SeparatorMenuItem());

        Menu shapeMenu = new Menu("封面形状");
        ToggleGroup shapeGroup = new ToggleGroup();

        RadioMenuItem circularItem = new RadioMenuItem("圆形（黑胶）");
        circularItem.setToggleGroup(shapeGroup);
        circularItem.setSelected(true);
        circularItem.setOnAction(e -> applyCoverShape(true));

        RadioMenuItem squareItem = new RadioMenuItem("方形");
        squareItem.setToggleGroup(shapeGroup);
        squareItem.setOnAction(e -> applyCoverShape(false));

        shapeMenu.getItems().addAll(circularItem, squareItem);
        view.getItems().add(shapeMenu);

        recordHighlightItem = new CheckMenuItem("唱片高光");
        recordHighlightItem.setSelected(true);
        recordHighlightItem.selectedProperty().addListener((o, ov, nv) -> {
            highlightEnabled = nv;
            if (coverShapeCircular) {
                applyCoverShape(true);
            }
        });
        view.getItems().add(recordHighlightItem);

        rotationSpeedMenu = new Menu("旋转速度");
        ToggleGroup speedGroup = new ToggleGroup();

        double[] speeds = { SPEED_STOP, SPEED_SLOW, SPEED_MED, SPEED_FAST };
        String[] labels = { "停止", "慢速（约 8 秒/圈）", "中速（约 5 秒/圈）", "快速（约 2.5 秒/圈）" };

        for (int i = 0; i < speeds.length; i++) {
            final double spd = speeds[i];
            RadioMenuItem item = new RadioMenuItem(labels[i]);
            item.setToggleGroup(speedGroup);
            if (Math.abs(spd - SPEED_MED) < 0.001) item.setSelected(true);
            item.setOnAction(e -> recordDegPerSec = spd);
            rotationSpeedMenu.getItems().add(item);
        }
        view.getItems().add(rotationSpeedMenu);

        Menu help = new Menu("使用说明");
        add(help, "查看使用说明",  this::showUsageGuide);
        help.getItems().add(new SeparatorMenuItem());
        add(help, "快捷键速查",    this::showShortcutsOnly);
        add(help, "重要注意事项",  this::showNotesOnly);

        Menu about = new Menu("关于");
        add(about, "关于 LRC 编辑器", () ->
                alert("LRC 歌词编辑器 v4.9\n"
                              + "JDK 17 + JavaFX 21\n"
                              + "内置 jflac 解码器\n\n"
                              + "by 忆昔风依旧"));

        MenuBar menuBar = new MenuBar(file, edit, view, help, about);
        menuBar.getStyleClass().add("app-menu-bar");
        return menuBar;
    }

    private Menu buildOpacityMenu(String title, double defaultVal, java.util.function.DoubleConsumer onSelect) {
        Menu menu = new Menu(title);
        ToggleGroup group = new ToggleGroup();

        double[] options = { 0.0, 0.3, 0.5, 0.7, 1.0 };
        String[] labels  = { "隐藏", "30%", "50%", "70%", "100%" };

        for (int i = 0; i < options.length; i++) {
            final double op = options[i];
            RadioMenuItem item = new RadioMenuItem(labels[i]);
            item.setToggleGroup(group);
            if (Math.abs(op - defaultVal) < 0.001) item.setSelected(true);
            item.setOnAction(e -> onSelect.accept(op));
            menu.getItems().add(item);
        }
        return menu;
    }

    private Menu buildPreviewPlayingColorMenu() {
        Menu menu = new Menu("当前播放行颜色");
        ToggleGroup group = new ToggleGroup();
        String[] labels = { "黑色", "红色", "绿色", "蓝色" };
        String[] colors = { "#000000", "#dc2626", "#16a34a", "#2563eb" };

        for (int i = 0; i < colors.length; i++) {
            final String colorHex = colors[i];
            RadioMenuItem item = new RadioMenuItem(labels[i]);
            item.setToggleGroup(group);
            item.setSelected(previewPlayingColor.equals(Color.web(colorHex)));
            item.setOnAction(e -> setPreviewPlayingColor(colorHex));
            menu.getItems().add(item);
        }
        return menu;
    }

    private Color loadPreviewPlayingColor() {
        String saved = preferences.get(PREF_PREVIEW_PLAYING_COLOR, "#000000");
        try {
            return Color.web(saved);
        } catch (IllegalArgumentException ex) {
            return Color.BLACK;
        }
    }

    private void setPreviewPlayingColor(String colorHex) {
        finishLyricPreviewTransition();
        previewPlayingColor = Color.web(colorHex);
        if (previewCurrentLabel != null) {
            previewCurrentLabel.setTextFill(previewPlayingColor);
        }
        try {
            preferences.put(PREF_PREVIEW_PLAYING_COLOR, colorHex);
            preferences.flush();
        } catch (Exception ignored) {
            // The color still applies for the current session.
        }
    }

    private void add(Menu m, String text, Runnable r) {
        MenuItem mi = new MenuItem(text);
        mi.setOnAction(e -> r.run());
        m.getItems().add(mi);
    }

    // =====================================================================
    // 使用说明
    // =====================================================================
    private void showUsageGuide() {
        showTextDialog("使用说明", "LRC 歌词编辑器 —— 使用说明", buildUsageText());
    }

    private void showShortcutsOnly() {
        showTextDialog("快捷键速查", "快捷键速查", buildShortcutsText());
    }

    private void showNotesOnly() {
        showTextDialog("重要注意事项", "重要注意事项", buildNotesText());
    }

    private void showTextDialog(String title, String header, String content) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(title);
        dlg.setHeaderText(header);

        TextArea ta = new TextArea(content);
        ta.setEditable(false);
        ta.setWrapText(false);
        ta.setPrefSize(780, 640);
        ta.setStyle(
                "-fx-font-family:'Consolas','Microsoft YaHei',monospace;" +
                        "-fx-font-size:14px;" +
                        "-fx-control-inner-background:#fafafa;");
        ta.positionCaret(0);

        dlg.getDialogPane().setContent(ta);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.setResizable(true);
        dlg.showAndWait();
    }

    private String buildUsageText() {
        return
                "================================================================\n" +
                        "  LRC 歌词编辑器 · 使用说明\n" +
                        "  by 忆昔风依旧\n" +
                        "================================================================\n" +
                        "\n" +
                        "一、基本流程\n" +
                        "----------------------------------------------------------------\n" +
                        "  1) 导入音频（Ctrl+Shift+O）→ 等波形生成\n" +
                        "  2) 导入歌词（Ctrl+O）或直接新建手动输入\n" +
                        "  3) 播放音频，用 Ctrl+T（或空格）打时间戳\n" +
                        "  4) 用 Ctrl+S 保存 / 导出\n" +
                        "\n" +
                        "二、快捷键（列表焦点时生效）\n" +
                        "----------------------------------------------------------------\n" +
                        "  【文件】\n" +
                        "    Ctrl + O            导入歌词 (.lrc / .txt)\n" +
                        "    Ctrl + Shift + O    导入音频 (.mp3 / .wav / .flac)\n" +
                        "    Ctrl + S            保存\n" +
                        "    Ctrl + Shift + S    另存为\n" +
                        "\n" +
                        "  【编辑】\n" +
                        "    Ctrl + Z            撤销\n" +
                        "    Ctrl + Y            重做\n" +
                        "    Ctrl + A            全选所有非空行\n" +
                        "    Ctrl + T            生成时间标记（用当前播放时间覆盖选中行）\n" +
                        "    Ctrl + Shift + T    批量偏移选中时间戳（弹窗输入毫秒）\n" +
                        "    Ctrl + N            在选中行下方插入新行\n" +
                        "    Ctrl + D            删除所有选中行\n" +
                        "    Space               批量打戳（等价于 Ctrl+T）\n" +
                        "    Delete              删除选中行\n" +
                        "    Insert              插入新行\n" +
                        "    Enter               从选中行开始播放\n" +
                        "\n" +
                        "三、鼠标操作\n" +
                        "----------------------------------------------------------------\n" +
                        "  · 单击行             只选中（第一次）\n" +
                        "  · 再次单击同一行     进入编辑（保护机制，避免误触）\n" +
                        "  · Shift + 单击       连续范围选择\n" +
                        "  · Ctrl  + 单击       独立加减选择\n" +
                        "  · 单击 ▶ 图标        跳转音频到该组时间戳\n" +
                        "  · 单击波形图         跳转到该处播放\n" +
                        "  · 拖动窗口边缘       调整窗口大小\n" +
                        "\n" +
                        "四、播放与高亮\n" +
                        "----------------------------------------------------------------\n" +
                        "  · 黄色高亮   = 当前播放时间对应的歌词组\n" +
                        "  · 蓝色高亮   = 用户手动选中的行\n" +
                        "  · 蓝 + 黄   = 用户选中行恰好在当前播放组中（深蓝底 + 黄边条）\n" +
                        "  · 左侧 ▶ 绿色三角  出现于同一时间戳组的首行\n" +
                        "    点击它 = 快速跳到该组时间戳\n" +
                        "\n" +
                        "五、同步跳转开关（左侧按钮：同步跳转: 开/关）\n" +
                        "----------------------------------------------------------------\n" +
                        "  开（默认）:\n" +
                        "      播放时，高亮跟随播放位置，列表自动滚动到当前行\n" +
                        "  关:\n" +
                        "      高亮冻结在用户选中的那一组，列表不滚动\n" +
                        "      → 编辑时间戳时不会被播放位置顶走，方便精细修改\n" +
                        "      → 点选任意行，高亮会立刻切到该行对应的组\n" +
                        "\n" +
                        "六、自动选择同组（左侧按钮：自动选择同组: 开/关）\n" +
                        "----------------------------------------------------------------\n" +
                        "  关（默认）:\n" +
                        "      单击某行 → 只选中该行\n" +
                        "  开:\n" +
                        "      单击某行 → 立即选中该行时间戳对应的所有行（整组）\n" +
                        "      再单击同一行 → 进入编辑（仍保留两次点击保护）\n" +
                        "      按住 Shift / Ctrl 单击 → 保留原生多选，不触发自动扩展\n" +
                        "  · 适合：一键 Ctrl+T 给整组打戳，或 Ctrl+Shift+T 整组偏移\n" +
                        "\n" +
                        "七、时间分块 / 统合（左侧按钮）\n" +
                        "----------------------------------------------------------------\n" +
                        "  点一次（灰 → 绿）:\n" +
                        "      相同时间戳的行归到一组，不同组之间插入空行\n" +
                        "      便于观察多语言 / 多版本歌词的分组情况\n" +
                        "  再点一次（绿 → 灰）:\n" +
                        "      删除所有空行，恢复紧凑格式\n" +
                        "  · 空行在界面上完全隐形，无法选中、无法编辑，只是视觉间距\n" +
                        "  · 导出时始终自动删除空行，无需先手动统合\n" +
                        "\n" +
                        "八、offset（左侧按钮：修改 offset）\n" +
                        "----------------------------------------------------------------\n" +
                        "  · 输入值 = 目标偏移量（毫秒）\n" +
                        "  · 内部采用「增量法」:\n" +
                        "      输入 500 → 所有时间戳 +500ms\n" +
                        "      输入 0   → 所有时间戳 -500ms，恢复到原始值\n" +
                        "      输入 200 → 再 +200ms，总偏移 = +200ms\n" +
                        "  · 因此改回 0 即可一键恢复用户最初导入的原始时间戳\n" +
                        "\n" +
                        "九、视图设置\n" +
                        "----------------------------------------------------------------\n" +
                        "  · 导出时写入 UTF-8 BOM\n" +
                        "      开启后导出文件带 BOM 头，兼容部分老播放器中文显示\n" +
                        "  · 输入框边框可见度 / 选中高亮可见度 / 播放高亮可见度\n" +
                        "      调整三种界面元素的视觉强度，0% = 隐藏\n" +
                        "  · 封面形状：圆形（黑胶）/ 方形\n" +
                        "  · 唱片高光：圆形时可开关\n" +
                        "  · 旋转速度：停止 / 慢速 / 中速 / 快速（方形时禁用）\n" +
                        "\n" +
                        "================================================================\n" +
                        "  详细注意事项请见：使用说明 → 重要注意事项\n" +
                        "================================================================\n";
    }

    private String buildShortcutsText() {
        return
                "================================================================\n" +
                        "  快捷键速查\n" +
                        "================================================================\n" +
                        "\n" +
                        "【文件】\n" +
                        "  Ctrl + O            导入歌词\n" +
                        "  Ctrl + Shift + O    导入音频\n" +
                        "  Ctrl + S            保存\n" +
                        "  Ctrl + Shift + S    另存为\n" +
                        "\n" +
                        "【编辑】\n" +
                        "  Ctrl + Z            撤销\n" +
                        "  Ctrl + Y            重做\n" +
                        "  Ctrl + A            全选所有非空行\n" +
                        "  Ctrl + T            生成时间标记（覆盖选中行）\n" +
                        "  Ctrl + Shift + T    批量偏移选中时间戳\n" +
                        "  Ctrl + N            插入新行\n" +
                        "  Ctrl + D            删除选中行\n" +
                        "\n" +
                        "【列表内】\n" +
                        "  Space               批量打戳（等价于 Ctrl+T）\n" +
                        "  Delete              删除选中行\n" +
                        "  Insert              插入新行\n" +
                        "  Enter               从选中行开始播放\n" +
                        "\n" +
                        "【多选】\n" +
                        "  Shift + 单击        连续范围选择\n" +
                        "  Ctrl  + 单击        独立加减选择\n" +
                        "  Ctrl + A            全选\n" +
                        "\n" +
                        "【播放】\n" +
                        "  单击左侧按钮         播放 / 暂停\n" +
                        "  单击波形图           跳转到该处播放\n" +
                        "  单击 ▶ 图标          跳转到该组时间戳\n";
    }

    private String buildNotesText() {
        return
                "================================================================\n" +
                        "  重要注意事项\n" +
                        "================================================================\n" +
                        "\n" +
                        "1. 时间戳格式\n" +
                        "----------------------------------------------------------------\n" +
                        "  标准格式： [mm:ss.xxx]\n" +
                        "  兼容格式： [mm:ss.xx]    [mm:ss]    [mm:ss:xx]\n" +
                        "  编辑时间戳时可省略方括号，保存时会自动补上。\n" +
                        "\n" +
                        "2. 两次点击才进入编辑\n" +
                        "----------------------------------------------------------------\n" +
                        "  为防止误触，第一次单击仅「选中」，第二次点击同一行才进入编辑。\n" +
                        "  按 Enter 提交并退出编辑，光标停在原行不会跳走。\n" +
                        "  · 开启「自动选择同组」后：第一击选中整组，第二击才进入编辑。\n" +
                        "\n" +
                        "3. 批量操作使用「焦点行」\n" +
                        "----------------------------------------------------------------\n" +
                        "  多选状态下，Ctrl+T / Ctrl+D / Ctrl+N 等操作以「焦点行」\n" +
                        "  （最后点击或键盘移动到的行）为准进行计算和定位。\n" +
                        "\n" +
                        "4. 播放高亮 vs 用户选中\n" +
                        "----------------------------------------------------------------\n" +
                        "  黄色 = 播放时间驱动（系统）\n" +
                        "  蓝色 = 用户手动选择\n" +
                        "  两者叠加时采用「深蓝底 + 黄边条」组合，不会互相遮挡。\n" +
                        "\n" +
                        "5. 关闭同步跳转后\n" +
                        "----------------------------------------------------------------\n" +
                        "  当前高亮会被「锁定」在切换的瞬间，不随播放前进。\n" +
                        "  想重新跟随播放时，再点一次左侧按钮即可。\n" +
                        "\n" +
                        "6. 时间分块产生的空行\n" +
                        "----------------------------------------------------------------\n" +
                        "  是纯视觉元素，无法选中、无法编辑；导出时自动丢弃。\n" +
                        "  不需要为了导出而先手动统合。\n" +
                        "\n" +
                        "7. offset 采用增量法\n" +
                        "----------------------------------------------------------------\n" +
                        "  每次输入的是「目标偏移量」，不是「改动量」。\n" +
                        "  改回 0 即恢复原始时间戳。\n" +
                        "\n" +
                        "8. 保存编码\n" +
                        "----------------------------------------------------------------\n" +
                        "  默认 UTF-8。开启「导出时写入 UTF-8 BOM」可兼容老播放器。\n" +
                        "  导入时自动识别 UTF-8 / GBK / BOM。\n" +
                        "\n" +
                        "9. FLAC 音频\n" +
                        "----------------------------------------------------------------\n" +
                        "  首次导入 FLAC 时会在后台解码为临时 WAV，\n" +
                        "  大文件可能需要几秒，请耐心等待进度条完成。\n" +
                        "\n" +
                        "10. 撤销栈\n" +
                        "----------------------------------------------------------------\n" +
                        "  最多保留 300 步。批量操作算作一步，可一次 Ctrl+Z 撤销。\n" +
                        "\n" +
                        "11. 封面来源\n" +
                        "----------------------------------------------------------------\n" +
                        "  优先读 MP3 / FLAC 内嵌封面；\n" +
                        "  其次搜索音频同目录的同名图片；\n" +
                        "  再找 cover / folder / album / front / artwork / albumart。\n" +
                        "  找不到时显示空白黑胶盘。\n" +
                        "\n" +
                        "12. 圆形封面旋转\n" +
                        "----------------------------------------------------------------\n" +
                        "  仅在「圆形 + 非零速度 + 播放中」三个条件同时满足时旋转。\n" +
                        "  暂停、停止、切换歌曲、切换为方形 → 立刻停住 / 归零。\n";
    }

    // =====================================================================
    // 左侧
    // =====================================================================
    private Region buildLeft(Stage stage) {
        VBox box = new VBox(4);
        box.getStyleClass().add("sidebar");
        box.setFillWidth(true);
        box.setPadding(new Insets(12, 12, 16, 12));

        Button bImportLrc   = sidebarButton("导入歌词");
        Button bImportAudio = sidebarButton("导入音频");
        Button bMark        = sidebarButton("生成时间标记");
        Button bJump        = sidebarButton("跳到光标时间戳");
        syncJumpBtn         = sidebarButton("同步跳转：开");
        autoSelectBtn       = sidebarButton("自动选择同组：关");
        Button bInsertLine  = sidebarButton("插入新行");
        Button bDeleteLine  = sidebarButton("删除选中行");
        Button bShift       = sidebarButton("批量偏移时间戳");
        playPauseBtn        = sidebarButton("▶ 播放");
        Button bOffset      = sidebarButton("修改 offset");
        chunkToggleBtn      = sidebarButton("时间分块 / 统合");
        Button bExport      = sidebarButton("导出歌词");

        bImportLrc.getStyleClass().add("is-outline");
        bImportAudio.getStyleClass().add("is-outline");
        playPauseBtn.getStyleClass().add("is-primary");
        bExport.getStyleClass().add("is-outline");

        bImportLrc.setOnAction(e -> importLrc(stage));
        bImportAudio.setOnAction(e -> importAudio(stage));
        bMark.setOnAction(e -> stampSelectedLines());
        bJump.setOnAction(e -> jumpToTimestamp());
        syncJumpBtn.setOnAction(e -> {
            if (lyricView == null) return;
            boolean newState = !lyricView.isAutoScrollToPlaying();
            lyricView.setAutoScrollToPlaying(newState);
            updateSyncJumpButtonStyle();
        });
        autoSelectBtn.setOnAction(e -> {
            if (lyricView == null) return;
            boolean newState = !lyricView.isAutoSelectSameTimestamp();
            lyricView.setAutoSelectSameTimestamp(newState);
            updateAutoSelectButtonStyle();
        });
        bInsertLine.setOnAction(e -> insertNewLine());
        bDeleteLine.setOnAction(e -> deleteSelectedLines());
        bShift.setOnAction(e -> shiftSelectedTimestamps());
        playPauseBtn.setOnAction(e -> togglePlay());
        bOffset.setOnAction(e -> showOffsetDialog());
        chunkToggleBtn.setOnAction(e -> { doc.toggleTimeChunk(); refreshAll(); });
        bExport.setOnAction(e -> exportLrc(stage));

        box.getChildren().addAll(
                sidebarSection("文件"),
                bImportLrc, bImportAudio,
                sidebarSeparator(), sidebarSection("打点与定位"),
                bMark, bJump, syncJumpBtn, autoSelectBtn,
                sidebarSeparator(), sidebarSection("编辑"),
                bInsertLine, bDeleteLine, bShift,
                sidebarSeparator(), sidebarSection("播放与输出"),
                playPauseBtn, bOffset, chunkToggleBtn, bExport);

        ScrollPane scroll = new ScrollPane(box);
        scroll.getStyleClass().add("sidebar-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPrefWidth(SIDEBAR_WIDTH);
        scroll.setMinWidth(SIDEBAR_WIDTH);
        scroll.setMaxWidth(SIDEBAR_WIDTH);

        updateChunkButtonStyle();
        return scroll;
    }

    private Button sidebarButton(String text) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setPrefHeight(33);
        b.setMinHeight(33);
        b.getStyleClass().add("sidebar-button");
        return b;
    }

    private Label sidebarSection(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("sidebar-section");
        return label;
    }

    private Separator sidebarSeparator() {
        Separator separator = new Separator();
        separator.getStyleClass().add("sidebar-separator");
        return separator;
    }

    private void setButtonState(Button button, String stateClass) {
        button.getStyleClass().removeAll("is-on", "is-warning");
        if (stateClass != null && !stateClass.isBlank()) {
            button.getStyleClass().add(stateClass);
        }
    }

    private void updateSyncJumpButtonStyle() {
        if (syncJumpBtn == null || lyricView == null) return;
        if (lyricView.isAutoScrollToPlaying()) {
            syncJumpBtn.setText("同步跳转：开");
            setButtonState(syncJumpBtn, null);
        } else {
            syncJumpBtn.setText("同步跳转：关");
            setButtonState(syncJumpBtn, "is-warning");
        }
    }

    private void updateAutoSelectButtonStyle() {
        if (autoSelectBtn == null || lyricView == null) return;
        if (lyricView.isAutoSelectSameTimestamp()) {
            autoSelectBtn.setText("自动选择同组：开");
            setButtonState(autoSelectBtn, "is-on");
        } else {
            autoSelectBtn.setText("自动选择同组：关");
            setButtonState(autoSelectBtn, null);
        }
    }

    private void updateChunkButtonStyle() {
        if (chunkToggleBtn == null) return;
        if (doc.isChunked()) {
            chunkToggleBtn.setText("已分块 · 点击统合");
            setButtonState(chunkToggleBtn, "is-on");
        } else {
            chunkToggleBtn.setText("时间分块 / 统合");
            setButtonState(chunkToggleBtn, null);
        }
    }

    // =====================================================================
    // 中间
    // =====================================================================
    private Region buildCenter() {
        lyricView = new LyricEditorView(doc);
        lyricView.getStyleClass().add("lyric-list");
        lyricView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        lyricView.setOnDocChanged(this::refreshStatus);

        lyricView.setOnStamp(indices -> {
            if (mediaPlayer == null) return;
            if (indices.isEmpty()) return;

            long ms = (long) mediaPlayer.getCurrentTime().toMillis();
            doc.stampMany(indices, ms);

            if (indices.size() == 1) {
                int next = indices.get(0) + 1;
                if (next < doc.getLines().size()) {
                    lyricView.getSelectionModel().clearSelection();
                    lyricView.getSelectionModel().select(next);
                    lyricView.scrollTo(Math.max(0, next - 3));
                }
            }
            refreshStatus();
        });

        lyricView.setOnJumpToTime(this::jumpToTime);
        lyricView.setOnPlayFromTime(this::playFromTime);

        updateSyncJumpButtonStyle();
        updateAutoSelectButtonStyle();

        StackPane workspace = new StackPane(lyricView);
        workspace.getStyleClass().add("workspace");
        workspace.setPadding(new Insets(12));
        return workspace;
    }

    // =====================================================================
    // 底部
    // =====================================================================
    private VBox buildBottom() {
        offsetLabel     = new Label("Offset: 0 ms");
        saveStatusLabel = new Label("● 未保存");
        fileNamesLabel  = new Label("无文件");
        encodeLabel     = new Label("编码: —");

        saveStatusLabel.getStyleClass().addAll("status-save", "is-dirty");
        offsetLabel.getStyleClass().add("status-meta");
        fileNamesLabel.getStyleClass().add("status-filename");
        encodeLabel.getStyleClass().add("status-meta");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox params = new HBox(18, saveStatusLabel, offsetLabel, fileNamesLabel, spacer, encodeLabel);
        params.getStyleClass().add("status-bar");
        params.setAlignment(Pos.CENTER_LEFT);

        waveCanvas   = new Canvas(1080, WAVE_HEIGHT);
        waveProgress = new ProgressBar(0);
        waveProgress.getStyleClass().add("wave-progress");
        waveProgress.setPrefWidth(200);
        waveProgress.setVisible(false);

        Pane holder = new Pane(waveCanvas, waveProgress);
        holder.getStyleClass().add("wave-panel");
        holder.setPrefHeight(WAVE_HEIGHT);
        holder.setMinHeight(WAVE_HEIGHT);

        holder.widthProperty().addListener((o, ov, nv) -> {
            double w = nv.doubleValue();
            waveCanvas.setWidth(w);
            waveProgress.setLayoutX((w - waveProgress.getPrefWidth()) / 2);
        });
        holder.heightProperty().addListener((o, ov, nv) -> {
            waveCanvas.setHeight(nv.doubleValue());
        });

        waveCanvas.setWidth(1080);
        waveCanvas.setHeight(WAVE_HEIGHT);
        waveProgress.setLayoutX(440);
        waveProgress.setLayoutY((WAVE_HEIGHT - 20) / 2);

        VBox bottom = new VBox(params, holder);
        bottom.getStyleClass().add("bottom-panel");
        VBox.setVgrow(holder, Priority.ALWAYS);

        installWaveMouseSupport();
        return bottom;
    }

    // =====================================================================
    // 波形图交互
    // =====================================================================
    private void installWaveMouseSupport() {
        waveCanvas.setCursor(Cursor.HAND);
        waveCanvas.setOnMouseClicked(e -> {
            if (mediaPlayer == null) return;
            cancelEndRewindAnimation();
            double W = waveCanvas.getWidth();
            double clickedSec = viewCenterSec + (e.getX() - W / 2) / W * WAVE_WINDOW_SEC;
            clickedSec = clampTime(clickedSec);
            mediaPlayer.seek(Duration.seconds(clickedSec));
            clickTargetSec = clickedSec;
            clickPhase     = 0;
            clickAnimating = true;
        });
    }

    private double clampTime(double sec) {
        if (sec < 0) return 0;
        if (mediaPlayer == null) return sec;
        Duration tot = mediaPlayer.getTotalDuration();
        if (tot != null && tot.greaterThan(Duration.ZERO)) {
            return Math.min(sec, tot.toSeconds());
        }
        return sec;
    }

    // =====================================================================
    // 波形绘制
    // =====================================================================
    private void drawWaveform(double viewCenter, double playhead) {
        GraphicsContext gc = waveCanvas.getGraphicsContext2D();
        double W = waveCanvas.getWidth(), H = waveCanvas.getHeight();
        gc.setFill(Color.web("#f8fafc"));
        gc.fillRect(0, 0, W, H);

        if (waveform == null || waveform.bucketCount() == 0) {
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFill(Color.web("#94a3b8"));
            gc.fillText("未载入音频", W / 2, H / 2 + 5);
            return;
        }
        gc.setTextAlign(TextAlignment.LEFT);

        double startSec = viewCenter - WAVE_WINDOW_SEC / 2;
        double midY = H / 2, amp = H / 2 * 0.9;

        gc.setStroke(Color.web("#e2e8f0"));
        gc.setLineWidth(1);
        gc.strokeLine(0, midY, W, midY);

        gc.setStroke(Color.web("#2563eb"));
        gc.setLineWidth(1);
        for (int px = 0; px < (int) W; px++) {
            double timelineSec = startSec + (px / W) * WAVE_WINDOW_SEC;
            double sampleSec = mapWaveformTime(timelineSec);
            if (sampleSec < 0) continue;
            int b = (int) (sampleSec / waveform.secPerBucket);
            if (b < 0 || b >= waveform.bucketCount()) continue;
            double y1 = midY - waveform.max[b] * amp;
            double y2 = midY - waveform.min[b] * amp;
            if (y2 - y1 < 1.0) { y1 = midY - 0.5; y2 = midY + 0.5; }
            gc.strokeLine(px + 0.5, y1, px + 0.5, y2);
        }

        double playheadX = W / 2 + (playhead - viewCenter) / WAVE_WINDOW_SEC * W;
        playheadX = Math.max(0, Math.min(W, playheadX));

        gc.setFill(Color.rgb(239, 68, 68, 0.09));
        gc.fillRect(playheadX - 8, 0, 16, H);

        gc.setStroke(Color.web("#ef4444"));
        gc.setLineWidth(1.8);
        gc.strokeLine(playheadX, 0, playheadX, H);
    }

    // =====================================================================
    // 导入歌词
    // =====================================================================
    private void configureFileChooserDirectory(FileChooser chooser) {
        File directory = lastUsedDirectory();
        if (directory != null) {
            chooser.setInitialDirectory(directory);
        }
    }

    private File lastUsedDirectory() {
        try {
            String savedPath = preferences.get(PREF_LAST_DIRECTORY, "");
            if (savedPath != null && !savedPath.isBlank()) {
                File saved = new File(savedPath);
                if (saved.isDirectory()) return saved;
                if (saved.isFile() && saved.getParentFile() != null
                        && saved.getParentFile().isDirectory()) {
                    return saved.getParentFile();
                }
            }
        } catch (RuntimeException ignored) {
            // Fall back to the currently loaded file below.
        }

        File reference = audioFile != null ? audioFile : doc.getFile();
        if (reference != null) {
            File directory = reference.isDirectory()
                    ? reference
                    : reference.getParentFile();
            if (directory != null && directory.isDirectory()) {
                return directory;
            }
        }
        return null;
    }

    private void rememberLastDirectory(File selectedFile) {
        if (selectedFile == null) return;

        File directory = selectedFile.isDirectory()
                ? selectedFile
                : selectedFile.getParentFile();
        if (directory == null || !directory.isDirectory()) return;

        try {
            preferences.put(PREF_LAST_DIRECTORY, directory.getAbsolutePath());
            preferences.flush();
        } catch (Exception ignored) {
            // File dialogs still work even if preferences cannot be persisted.
        }
    }

    private void importLrc(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("选择 LRC 文件");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("LRC 歌词", "*.lrc", "*.txt"));
        configureFileChooserDirectory(fc);
        File f = fc.showOpenDialog(stage);
        if (f == null) return;
        rememberLastDirectory(f);
        try {
            LrcIo.ReadResult r = LrcIo.read(f);
            doc.load(r.text, f);
            doc.setEncoding(r.encoding);
            doc.setBom(r.hasBom);
            refreshAll();
            loadCover(f);
        } catch (Exception ex) {
            alert("读取失败：" + ex.getMessage());
        }
    }

    // =====================================================================
    // 导入音频
    // =====================================================================
    private void importAudio(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("选择音频文件");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("音频", "*.mp3", "*.wav", "*.flac"));
        configureFileChooserDirectory(fc);
        File f = fc.showOpenDialog(stage);
        if (f == null) return;
        rememberLastDirectory(f);
        audioFile = f;

        if (mediaPlayer != null) { mediaPlayer.dispose(); mediaPlayer = null; }
        cancelEndRewindAnimation();
        waveform = null;
        viewCenterSec = 0;
        displayPlayheadSec = 0;
        clickAnimating = false;

        resetRecordRotation();

        waveProgress.setVisible(true);
        waveProgress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        Task<File> prepTask = new Task<>() {
            @Override protected File call() throws Exception {
                return AudioIo.preparePlayable(f);
            }
        };
        prepTask.setOnSucceeded(e -> {
            File playable = prepTask.getValue();
            startPlayback(playable);
            startWaveform(f);
        });
        prepTask.setOnFailed(e -> {
            waveProgress.setVisible(false);
            alert("音频准备失败：" + prepTask.getException().getMessage());
        });
        bgExecutor.submit(prepTask);

        updateFileNames();
        loadCover(f);
    }

    private void resetRecordRotation() {
        recordAngle = 0;
        if (recordRotation != null) recordRotation.setAngle(0);
    }

    private void startPlayback(File playable) {
        try {
            Media media = new Media(playable.toURI().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setOnReady(() -> timeLabel.setText(
                    "00:00.000 / " + LrcIo.formatDuration(mediaPlayer.getTotalDuration().toSeconds())));
            mediaPlayer.setOnEndOfMedia(() -> {
                double totalSec = mediaPlayer.getTotalDuration().toSeconds();
                mediaPlayer.stop();
                mediaPlayer.seek(Duration.ZERO);
                playPauseBtn.setText("▶ 播放");
                startEndRewindAnimation(totalSec);
            });
        } catch (Exception ex) {
            alert("播放失败：" + ex.getMessage());
        }
    }

    private void startWaveform(File source) {
        WaveformTask task = new WaveformTask(source, 0.01);

        waveProgress.progressProperty().bind(task.progressProperty());
        waveProgress.setVisible(true);

        task.setOnSucceeded(e -> {
            waveform = task.getValue();
            waveProgress.setVisible(false);
            waveProgress.progressProperty().unbind();
        });
        task.setOnFailed(e -> {
            waveProgress.setVisible(false);
            waveProgress.progressProperty().unbind();
            System.err.println("波形生成失败：" + task.getException());
        });
        bgExecutor.submit(task);
    }

    // =====================================================================
    // 播放 / 暂停
    // =====================================================================
    private void togglePlay() {
        if (mediaPlayer == null) { alert("请先导入音频"); return; }
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
            playPauseBtn.setText("▶ 播放");
        } else {
            if (endRewindAnimating) {
                finishEndRewindAnimation();
            } else {
                Duration current = mediaPlayer.getCurrentTime();
                Duration total = mediaPlayer.getTotalDuration();
                if (current != null && total != null && total.greaterThan(Duration.ZERO)
                        && current.greaterThan(total.subtract(Duration.millis(80)))) {
                    resetWaveViewToStart();
                }
            }
            mediaPlayer.play();
            playPauseBtn.setText("⏸ 暂停");
        }
    }

    private void resetWaveViewToStart() {
        cancelEndRewindAnimation();
        clickAnimating = false;
        viewCenterSec = 0;
        displayPlayheadSec = 0;
        if (mediaPlayer != null) {
            mediaPlayer.seek(Duration.ZERO);
        }
    }

    // =====================================================================
    // 批量操作
    // =====================================================================
    private void stampSelectedLines() {
        if (mediaPlayer == null) { alert("请先导入音频"); return; }
        if (doc.getLines().isEmpty()) { alert("请先导入歌词或手动创建内容"); return; }

        List<Integer> selected = lyricView.getSelectedLyricIndices();
        if (selected.isEmpty()) {
            alert("请先在歌词列表里选中至少一行");
            return;
        }

        long ms = (long) mediaPlayer.getCurrentTime().toMillis();
        doc.stampMany(selected, ms);

        if (selected.size() == 1) {
            int next = selected.get(0) + 1;
            if (next < doc.getLines().size()) {
                lyricView.getSelectionModel().clearSelection();
                lyricView.getSelectionModel().select(next);
                lyricView.scrollTo(Math.max(0, next - 3));
            }
        }

        refreshStatus();
        System.out.println("[批量覆盖] " + selected.size()
                                   + " 行 → " + LrcIo.formatTime(ms));
    }

    private void deleteSelectedLines() {
        List<Integer> selected = lyricView.getSelectedLyricIndices();
        if (selected.isEmpty()) {
            alert("请先在歌词列表里选中至少一行");
            return;
        }

        int minIdx = selected.get(0);
        doc.removeMany(selected);

        int size = doc.getLines().size();
        int newSel = Math.min(minIdx, size - 1);
        lyricView.getSelectionModel().clearSelection();
        if (newSel >= 0) {
            lyricView.getSelectionModel().select(newSel);
            lyricView.scrollTo(newSel);
        }
        refreshStatus();
        lyricView.requestFocus();
    }

    private void shiftSelectedTimestamps() {
        if (doc.getLines().isEmpty()) { alert("请先导入歌词或手动创建内容"); return; }

        List<Integer> selected = lyricView.getSelectedLyricIndices();
        if (selected.isEmpty()) {
            alert("请先在歌词列表里选中至少一行");
            return;
        }

        TextInputDialog dlg = new TextInputDialog("0");
        dlg.setTitle("批量偏移选中时间戳");
        dlg.setHeaderText("对选中的 " + selected.size() + " 行时间戳统一偏移（毫秒）。\n"
                                  + "正数 = 后移，负数 = 前移。0 = 不变。");
        dlg.setContentText("偏移量(ms):");
        dlg.showAndWait().ifPresent(s -> {
            try {
                long delta = Long.parseLong(s.trim());
                if (delta == 0) return;
                doc.shiftMany(selected, delta);
                refreshStatus();
                System.out.println("[批量偏移] " + selected.size()
                                           + " 行 × " + delta + "ms");
            } catch (NumberFormatException ex) {
                alert("请输入整数毫秒值");
            }
        });
    }

    // =====================================================================
    // 单行操作
    // =====================================================================
    private void insertNewLine() {
        int idx = lyricView.getSelectionModel().getSelectedIndex();

        int insertPos;
        if (idx < 0 || idx >= doc.getLines().size()) {
            insertPos = doc.getLines().size();
        } else {
            insertPos = idx + 1;
        }

        doc.addLine(insertPos, new LyricLine(0L, ""));

        lyricView.getSelectionModel().clearSelection();
        lyricView.getSelectionModel().select(insertPos);
        lyricView.scrollTo(insertPos);
        refreshStatus();
        lyricView.requestFocus();
    }

    // =====================================================================
    // 跳转
    // =====================================================================
    private void jumpToTime(long timeMs) {
        if (mediaPlayer == null) return;
        cancelEndRewindAnimation();
        double targetSec = clampTime(timeMs / 1000.0);
        mediaPlayer.seek(Duration.seconds(targetSec));
        clickTargetSec = targetSec;
        clickPhase     = 0;
        clickAnimating = true;
    }

    private void playFromTime(long timeMs) {
        if (mediaPlayer == null) { alert("请先导入音频"); return; }
        jumpToTime(timeMs);
        mediaPlayer.play();
        playPauseBtn.setText("⏸ 暂停");
    }

    private void jumpToTimestamp() {
        if (mediaPlayer == null) { alert("请先导入音频"); return; }

        int idx = lyricView.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= doc.getLines().size()) {
            alert("请先在歌词列表里点选一行，作为跳转目标");
            return;
        }
        LyricLine line = doc.getLines().get(idx);
        if (line.timeMs == null) {
            alert("该行还没有时间戳，无法跳转");
            return;
        }
        jumpToTime(line.timeMs);
        lyricView.requestFocus();
    }

    // =====================================================================
    // offset
    // =====================================================================
    private void showOffsetDialog() {
        TextInputDialog dlg = new TextInputDialog(String.valueOf(doc.getOffset()));
        dlg.setTitle("修改 offset");
        dlg.setHeaderText("输入新的 offset（毫秒）。0 = 恢复原始时间戳；正数=整体后移。");
        dlg.setContentText("Offset(ms):");
        dlg.showAndWait().ifPresent(s -> {
            try {
                doc.applyOffset(Long.parseLong(s.trim()));
                refreshStatus();
            } catch (NumberFormatException ex) {
                alert("请输入整数毫秒值");
            }
        });
    }

    // =====================================================================
    // 保存 / 导出
    // =====================================================================
    private void saveLrc(boolean saveAs, Stage stage) {
        File target = doc.getFile();
        if (saveAs || target == null) {
            FileChooser fc = new FileChooser();
            fc.setTitle("保存 LRC");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("LRC 歌词", "*.lrc"));
            fc.setInitialFileName(target != null ? target.getName() : "lyrics.lrc");
            configureFileChooserDirectory(fc);
            target = fc.showSaveDialog(stage);
            if (target == null) return;
            rememberLastDirectory(target);
        }
        try {
            LrcIo.write(target, doc.serialize(false), doc.hasBom());
            doc.setFile(target);
            doc.setDirty(false);
            refreshAll();
        } catch (Exception ex) {
            alert("保存失败：" + ex.getMessage());
        }
    }

    private void exportLrc(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("导出 LRC（删除空行）");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("LRC 歌词", "*.lrc"));
        fc.setInitialFileName(doc.getFile() != null ? doc.getFile().getName() : "export.lrc");
        configureFileChooserDirectory(fc);
        File target = fc.showSaveDialog(stage);
        if (target == null) return;
        rememberLastDirectory(target);
        try {
            LrcIo.write(target, doc.serialize(true), bomOnExport);
        } catch (Exception ex) {
            alert("导出失败：" + ex.getMessage());
        }
    }

    // =====================================================================
    // 状态刷新 / 封面 / 通用
    // =====================================================================
    private void refreshAll() {
        lyricView.refresh();
        refreshStatus();
        updateChunkButtonStyle();
        updateSyncJumpButtonStyle();
        updateAutoSelectButtonStyle();
    }

    private void refreshStatus() {
        offsetLabel.setText("Offset: " + doc.getOffset() + " ms");
        saveStatusLabel.getStyleClass().removeAll("is-dirty", "is-saved");
        if (doc.isDirty()) {
            saveStatusLabel.setText("● 未保存");
            saveStatusLabel.getStyleClass().add("is-dirty");
        } else {
            saveStatusLabel.setText("✓ 已保存");
            saveStatusLabel.getStyleClass().add("is-saved");
        }
        encodeLabel.setText("编码: " + doc.getEncoding() + (doc.hasBom() ? " (BOM)" : ""));
        updateFileNames();
        updateLyricPreview(currentPlaybackSec(), true);

        if (lyricView != null) lyricView.refresh();
    }

    private double currentPlaybackSec() {
        if (mediaPlayer == null) return 0;
        try {
            return mediaPlayer.getCurrentTime().toSeconds();
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private void updateFileNames() {
        String a = audioFile == null ? "—" : audioFile.getName();
        String l = doc.getFile() == null ? "—" : doc.getFile().getName();
        fileNamesLabel.setText("音频: " + a + "   歌词: " + l);
    }

    private void loadCover(File reference) {
        Image img = CoverArtLoader.loadFor(audioFile, reference);

        if (img != null) {
            coverView.setImage(img);
        } else {
            coverView.setImage(null);
        }

        if (reference != null) {
            String base = reference.getName().replaceAll("\\.[^.]+$", "");
            songTitleLabel.setText(base);
        }
    }

    private boolean confirmDiscard() {
        if (!doc.isDirty()) return true;
        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                            "当前有未保存的修改，确定放弃吗？", ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText(null);
        return a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void alert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
