package com.lrceditor;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SelectionModel;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.regex.Matcher;

public class LyricEditorView extends ListView<LyricLine> {

    private final LrcDocument doc;

    /** 空闲态边框可见度（0~1）。默认 0.5 */
    private double borderOpacity = 0.5;

    /** 选中高亮（蓝）可见度（0~1）。默认 1.0 */
    private double selectedHighlightOpacity = 1.0;

    /** 播放高亮（黄）可见度（0~1）。默认 1.0 */
    private double playingHighlightOpacity = 1.0;

    /** 是否在播放/拖动时自动滚动到当前时间戳所在行。默认开 */
    private boolean autoScrollToPlaying = true;

    /** 是否自动选择相同时间戳的所有行（单击某行即选中整组）。默认关 */
    private boolean autoSelectSameTimestamp = false;

    /** 单击后向下额外扩选的行数，0 表示关闭，最大 10。 */
    private int autoExpandRows = 0;

    /** 深色外观。 */
    private boolean darkMode = false;

    /** 当前应该高亮的所有行索引 */
    private Set<Integer> playingIndices = Collections.emptySet();

    /** 当前处于"编辑模式"的行索引，-1 表示没有 */
    private int editingRow = -1;

    private Consumer<List<Integer>> onStampCallback;
    private Runnable     onDocChangedCallback;
    private LongConsumer onJumpToTimeCallback;
    private LongConsumer onPlayFromTimeCallback;

    public LyricEditorView(LrcDocument doc) {
        this.doc = doc;
        setItems(doc.getLines());
        setFixedCellSize(44);
        setStyle("-fx-font-size:17px;");
        setCellFactory(lv -> new LyricCell());

        getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (editingRow < 0
                    && e.getCode() == KeyCode.A
                    && (e.isControlDown() || e.isMetaDown())) {
                selectAllLyricLines();
                e.consume();
                return;
            }

            if (editingRow < 0
                    && e.getCode() == KeyCode.ENTER
                    && onPlayFromTimeCallback != null) {
                int idx = getSelectionModel().getSelectedIndex();
                if (idx >= 0 && idx < doc.getLines().size()) {
                    Long timeMs = doc.getLines().get(idx).timeMs;
                    if (timeMs != null) {
                        onPlayFromTimeCallback.accept(timeMs);
                    }
                }
                e.consume();
                return;
            }

            if (editingRow < 0
                    && e.getCode() == KeyCode.SPACE
                    && onStampCallback != null) {
                List<Integer> selected = getSelectedLyricIndices();
                if (!selected.isEmpty()) {
                    onStampCallback.accept(selected);
                }
                e.consume();
                return;
            }

            if (editingRow < 0 && e.getCode() == KeyCode.DELETE) {
                List<Integer> selected = getSelectedLyricIndices();
                if (!selected.isEmpty()) {
                    int minIdx = selected.get(0);
                    doc.removeMany(selected);
                    int size = doc.getLines().size();
                    int newSel = Math.min(minIdx, size - 1);
                    getSelectionModel().clearSelection();
                    if (newSel >= 0) {
                        getSelectionModel().select(newSel);
                    }
                    fireChanged();
                }
                e.consume();
                return;
            }

            if (editingRow < 0 && e.getCode() == KeyCode.INSERT) {
                int i = getSelectionModel().getSelectedIndex();
                doc.addLine(i + 1, new LyricLine(0L, ""));
                getSelectionModel().clearSelection();
                getSelectionModel().select(i + 1);
                fireChanged();
                e.consume();
            }
        });

        getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
            repaintAllCells();

            int idx = nv.intValue();

            if (isBlankSeparator(idx)) {
                int oldIdx = ov.intValue();
                int dir = (idx >= oldIdx) ? 1 : -1;

                int cand = idx + dir;
                while (cand >= 0 && cand < getItems().size() && isBlankSeparator(cand)) cand += dir;

                if (cand < 0 || cand >= getItems().size()) {
                    cand = idx - dir;
                    while (cand >= 0 && cand < getItems().size() && isBlankSeparator(cand)) cand -= dir;
                }
                final int target = cand;
                if (target >= 0 && target < getItems().size()) {
                    Platform.runLater(() -> getSelectionModel().select(target));
                }
                return;
            }

            if (!autoScrollToPlaying) {
                updateLockedHighlight();
            }
        });
    }

    public void setOnStamp(Consumer<List<Integer>> c) { this.onStampCallback = c; }
    public void setOnDocChanged(Runnable r)           { this.onDocChangedCallback = r; }
    public void setOnJumpToTime(LongConsumer c)       { this.onJumpToTimeCallback = c; }
    public void setOnPlayFromTime(LongConsumer c)     { this.onPlayFromTimeCallback = c; }
    private void fireChanged() { if (onDocChangedCallback != null) onDocChangedCallback.run(); }

    // ============================================================
    // 外部查询
    // ============================================================
    public void selectAllLyricLines() {
        getSelectionModel().clearSelection();
        for (int i = 0; i < getItems().size(); i++) {
            if (!isBlankSeparator(i)) {
                getSelectionModel().select(i);
            }
        }
    }

    public List<Integer> getSelectedLyricIndices() {
        List<Integer> result = new ArrayList<>();
        for (int i : getSelectionModel().getSelectedIndices()) {
            if (!isBlankSeparator(i)) result.add(i);
        }
        Collections.sort(result);
        return result;
    }

    // ============================================================
    // 边框透明度
    // ============================================================
    public double getBorderOpacity() { return borderOpacity; }

    public void setBorderOpacity(double opacity) {
        this.borderOpacity = Math.max(0, Math.min(1, opacity));
        refresh();
    }

    // ============================================================
    // 高亮可见度
    // ============================================================
    public double getSelectedHighlightOpacity() { return selectedHighlightOpacity; }

    public void setSelectedHighlightOpacity(double opacity) {
        this.selectedHighlightOpacity = Math.max(0, Math.min(1, opacity));
        refresh();
    }

    public double getPlayingHighlightOpacity() { return playingHighlightOpacity; }

    public void setPlayingHighlightOpacity(double opacity) {
        this.playingHighlightOpacity = Math.max(0, Math.min(1, opacity));
        refresh();
    }

    // ============================================================
    // 同步跳转开关
    // ============================================================
    public boolean isAutoScrollToPlaying() { return autoScrollToPlaying; }

    public void setAutoScrollToPlaying(boolean enabled) {
        this.autoScrollToPlaying = enabled;
    }

    // ============================================================
    // 自动选择相同时间戳开关
    // ============================================================
    public boolean isAutoSelectSameTimestamp() { return autoSelectSameTimestamp; }

    public void setAutoSelectSameTimestamp(boolean enabled) {
        this.autoSelectSameTimestamp = enabled;
    }

    public int getAutoExpandRows() {
        return autoExpandRows;
    }

    public void setAutoExpandRows(int rows) {
        autoExpandRows = Math.max(0, Math.min(10, rows));
    }

    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
        refresh();
    }

    // ============================================================
    // 外部（播放时间）驱动的高亮
    // ============================================================
    public void setPlayingIndices(Set<Integer> indices) {
        if (!autoScrollToPlaying) return;
        if (indices == null) return;

        if (Objects.equals(indices, playingIndices)) return;
        playingIndices = indices;
        repaintAllCells();

        if (!playingIndices.isEmpty()) {
            int first = playingIndices.stream().mapToInt(Integer::intValue).min().orElse(0);
            scrollTo(Math.max(0, first - 3));
        }
    }

    // ============================================================
    // 内部（选中行）驱动的高亮
    // ============================================================
    private void updateLockedHighlight() {
        int idx = getSelectionModel().getSelectedIndex();

        Set<Integer> group;
        if (idx < 0 || idx >= doc.getLines().size()) {
            group = Collections.emptySet();
        } else {
            LyricLine sel = doc.getLines().get(idx);
            if (sel.timeMs == null) {
                group = Collections.emptySet();
            } else {
                Set<Integer> tmp = new HashSet<>();
                for (int i = 0; i < doc.getLines().size(); i++) {
                    LyricLine l = doc.getLines().get(i);
                    if (sel.timeMs.equals(l.timeMs)) tmp.add(i);
                }
                group = tmp;
            }
        }

        if (!Objects.equals(group, playingIndices)) {
            playingIndices = group;
            repaintAllCells();
        }
    }

    private void repaintAllCells() {
        for (Node n : lookupAll(".list-cell")) {
            if (n instanceof LyricCell c) {
                c.refreshPlayIcon();
                styleCell(c);
            } else if (n instanceof ListCell<?> c) {
                styleCell(c);
            }
        }
    }

    // ============================================================
    // 颜色工厂
    // ============================================================
    private String rgba(int r, int g, int b, double a) {
        return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.2f)", r, g, b, a);
    }

    private String selBgColor() {
        return darkMode
                ? rgba(37, 99, 235, selectedHighlightOpacity * 0.48)
                : rgba(219, 234, 254, selectedHighlightOpacity);
    }

    private String selPlayBgColor() {
        return darkMode
                ? rgba(59, 130, 246, selectedHighlightOpacity * 0.62)
                : rgba(191, 219, 254, selectedHighlightOpacity);
    }

    private String playBgColor() {
        return darkMode
                ? rgba(146, 64, 14, playingHighlightOpacity * 0.62)
                : rgba(254, 243, 199, playingHighlightOpacity);
    }

    private String playBorderColor() {
        return darkMode
                ? rgba(251, 191, 36, playingHighlightOpacity * 0.72)
                : rgba(253, 230, 138, playingHighlightOpacity);
    }

    private void styleCell(ListCell<?> c) {
        int i = c.getIndex();

        if (isBlankSeparator(i)) {
            c.setStyle("-fx-background-color:transparent;");
            return;
        }

        boolean selected = c.isSelected();
        boolean playing  = playingIndices.contains(i);

        boolean selVisible  = selectedHighlightOpacity > 0.001;
        boolean playVisible = playingHighlightOpacity  > 0.001;

        String altBg = darkMode
                ? ((i % 2 == 0) ? "#0f172a" : "#111827")
                : ((i % 2 == 0) ? "#f8fafc" : "#ffffff");

        String bg;
        if (selected && playing && selVisible) {
            bg = selPlayBgColor();
        } else if (selected && selVisible) {
            bg = selBgColor();
        } else if (playing && playVisible) {
            bg = playBgColor();
        } else {
            bg = altBg;
        }

        String borderCss = "";
        if (playing && playVisible) {
            borderCss = "-fx-border-color:transparent transparent " + playBorderColor() + " transparent;"
                    + "-fx-border-width:0 0 1 0;";
        }

        c.setStyle("-fx-background-color:" + bg + ";" + borderCss);
    }

    // ============================================================
    // 空分隔行判断
    // ============================================================
    private boolean isBlankSeparator(int idx) {
        if (idx < 0 || idx >= doc.getLines().size()) return false;
        LyricLine l = doc.getLines().get(idx);
        return l.timeMs == null && l.text.isBlank();
    }

    // ============================================================
    // 空闲态样式
    // ============================================================
    private String idleBorderColor() {
        if (borderOpacity <= 0.001) return "transparent";
        int r = darkMode ? 71 : 203;
        int g = darkMode ? 85 : 213;
        int b = darkMode ? 105 : 225;
        return "rgba(" + r + "," + g + "," + b + ","
               + String.format(Locale.ROOT, "%.2f", borderOpacity) + ")";
    }

    private String buildIdleTimeStyle() {
        return "-fx-background-color:transparent;" +
                "-fx-border-color:" + idleBorderColor() + ";" +
                "-fx-border-width:1;" +
                "-fx-border-radius:3;" +
                "-fx-font-family:'Consolas',monospace;" +
                "-fx-text-fill:" + (darkMode ? "#60a5fa" : "#2563eb") + ";" +
                "-fx-font-size:18px;";
    }

    private String buildIdleTextStyle() {
        return "-fx-background-color:transparent;" +
                "-fx-border-color:" + idleBorderColor() + ";" +
                "-fx-border-width:1;" +
                "-fx-border-radius:3;";
    }

    private static final String EDIT_STYLE_TIME =
            "-fx-background-color:white;" +
                    "-fx-border-color:#3b82f6;" +
                    "-fx-border-width:1.5;" +
                    "-fx-border-radius:3;" +
                    "-fx-font-family:'Consolas',monospace;" +
                    "-fx-text-fill:#2563eb;" +
                    "-fx-font-size:18px;";

    private static final String EDIT_STYLE_TEXT =
            "-fx-background-color:white;" +
                    "-fx-border-color:#3b82f6;" +
                    "-fx-border-width:1.5;" +
                    "-fx-border-radius:3;";

    private String buildEditTimeStyle() {
        return "-fx-background-color:" + (darkMode ? "#111827" : "white") + ";" +
                "-fx-border-color:" + (darkMode ? "#60a5fa" : "#3b82f6") + ";" +
                "-fx-border-width:1.5;" +
                "-fx-border-radius:3;" +
                "-fx-font-family:'Consolas',monospace;" +
                "-fx-text-fill:" + (darkMode ? "#93c5fd" : "#2563eb") + ";" +
                "-fx-font-size:18px;";
    }

    private String buildEditTextStyle() {
        return "-fx-background-color:" + (darkMode ? "#111827" : "white") + ";" +
                "-fx-border-color:" + (darkMode ? "#60a5fa" : "#3b82f6") + ";" +
                "-fx-border-width:1.5;" +
                "-fx-border-radius:3;" +
                "-fx-text-fill:" + (darkMode ? "#e5e7eb" : "#111827") + ";";
    }

    // ============================================================
    // 组首判断
    // ============================================================
    private boolean isGroupFirst(int idx) {
        if (idx < 0 || idx >= doc.getLines().size()) return false;
        LyricLine cur = doc.getLines().get(idx);
        if (cur.timeMs == null) return false;
        if (idx == 0) return true;
        LyricLine prev = doc.getLines().get(idx - 1);
        return !cur.timeMs.equals(prev.timeMs);
    }

    // ============================================================
    // 自动选择同组：辅助方法
    // ============================================================
    /** 收集与 idx 行相同时间戳的所有行索引 */
    private List<Integer> collectSameTimestampGroup(int idx) {
        List<Integer> group = new ArrayList<>();
        if (idx < 0 || idx >= doc.getLines().size()) return group;
        LyricLine target = doc.getLines().get(idx);
        if (target.timeMs == null) return group;
        for (int i = 0; i < doc.getLines().size(); i++) {
            LyricLine l = doc.getLines().get(i);
            if (target.timeMs.equals(l.timeMs)) group.add(i);
        }
        return group;
    }

    /** 选中 idx 行时间戳对应的整组行，焦点落在 idx */
    private void selectSameTimestampGroup(int idx) {
        List<Integer> group = collectSameTimestampGroup(idx);
        if (group.isEmpty()) return;

        SelectionModel<LyricLine> sm = getSelectionModel();
        sm.clearSelection();
        sm.select(idx);                          // 先选焦点行
        for (int i : group) {
            if (i != idx) sm.select(i);          // 再选其他行
        }
    }

    /** 当前选中集是否完全等于 idx 行的时间戳组 */
    private boolean isCurrentSelectionEqualToGroup(int idx) {
        List<Integer> group = collectSameTimestampGroup(idx);
        if (group.isEmpty()) return false;
        Set<Integer> groupSet = new HashSet<>(group);
        Set<Integer> currSet  = new HashSet<>(getSelectionModel().getSelectedIndices());
        return currSet.equals(groupSet);
    }

    /** 从 idx 开始向下收集 idx 加 N 个可见歌词行，遇到文件末尾自动停止。 */
    private List<Integer> collectExpandedRange(int idx) {
        List<Integer> range = new ArrayList<>();
        if (idx < 0 || idx >= doc.getLines().size() || autoExpandRows <= 0) return range;

        if (!isBlankSeparator(idx)) range.add(idx);
        for (int i = idx + 1; i < doc.getLines().size()
                && range.size() <= autoExpandRows; i++) {
            if (!isBlankSeparator(i)) range.add(i);
        }
        return range;
    }

    private void selectExpandedRange(int idx) {
        List<Integer> range = collectExpandedRange(idx);
        if (range.isEmpty()) return;

        SelectionModel<LyricLine> sm = getSelectionModel();
        sm.clearSelection();
        sm.select(idx);
        for (int i : range) {
            if (i != idx) sm.select(i);
        }
    }

    private boolean isCurrentSelectionEqualToExpandedRange(int idx) {
        List<Integer> range = collectExpandedRange(idx);
        if (range.isEmpty()) return false;
        Set<Integer> rangeSet = new HashSet<>(range);
        Set<Integer> currentSet = new HashSet<>(getSelectionModel().getSelectedIndices());
        return currentSet.equals(rangeSet);
    }

    // ============================================================
    // 单元格
    // ============================================================
    class LyricCell extends ListCell<LyricLine> {

        private final javafx.scene.control.Label playIcon = new javafx.scene.control.Label();
        private final TextField timeField = new TextField();
        private final TextField textField = new TextField();
        private final HBox      layout    = new HBox(2, playIcon, timeField, textField);

        private LyricLine bound;
        private String    snapshotOnFocus;
        private boolean   inEdit = false;
        private boolean   wasSelectedAtPress = false;

        LyricCell() {
            playIcon.setPrefWidth(28);
            playIcon.setMinWidth(28);
            playIcon.setMaxWidth(28);
            playIcon.setAlignment(Pos.CENTER);
            playIcon.setText("▶");
            playIcon.setCursor(Cursor.HAND);
            playIcon.setStyle(ICON_INACTIVE);
            playIcon.setOnMouseClicked(e -> {
                e.consume();
                if (bound != null && bound.timeMs != null && onJumpToTimeCallback != null) {
                    onJumpToTimeCallback.accept(bound.timeMs);
                }
            });
            playIcon.setOnMouseEntered(e -> {
                if (isGroupFirst(getIndex())) playIcon.setStyle(ICON_HOVER);
            });
            playIcon.setOnMouseExited(e -> {
                playIcon.setStyle(isGroupFirst(getIndex()) ? ICON_ACTIVE : ICON_INACTIVE);
            });

            timeField.setPrefWidth(132);
            timeField.setMinWidth(132);
            timeField.setPadding(new Insets(2, 4, 2, 4));

            textField.setMaxWidth(Double.MAX_VALUE);
            textField.setPadding(new Insets(2, 4, 2, 4));
            HBox.setHgrow(textField, Priority.ALWAYS);

            timeField.setStyle(buildIdleTimeStyle());
            textField.setStyle(buildIdleTextStyle());

            timeField.setMouseTransparent(true);
            textField.setMouseTransparent(true);

            layout.setAlignment(Pos.CENTER_LEFT);
            layout.setPadding(new Insets(0, 4, 0, 0));

            timeField.focusedProperty().addListener((o, ov, nv) -> {
                timeField.setStyle(nv ? buildEditTimeStyle() : buildIdleTimeStyle());
                if (nv) beginEdit(); else endEditAndExit();
            });
            textField.focusedProperty().addListener((o, ov, nv) -> {
                textField.setStyle(nv ? buildEditTextStyle() : buildIdleTextStyle());
                if (nv) beginEdit(); else endEditAndExit();
            });

            timeField.setOnAction(e -> commitAndExit());
            textField.setOnAction(e -> commitAndExit());

            // ★ MOUSE_PRESSED 过滤器：判断"是否已经选中该行的时间戳组"
            addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                int idx = getIndex();
                if (isBlankSeparator(idx)) {
                    e.consume();
                    wasSelectedAtPress = false;
                    return;
                }
                if (e.isShiftDown() || e.isControlDown() || e.isMetaDown()) {
                    wasSelectedAtPress = false;
                    return;
                }

                if (autoExpandRows > 0) {
                    boolean rangeAlreadySelected = isCurrentSelectionEqualToExpandedRange(idx);
                    wasSelectedAtPress = rangeAlreadySelected;
                    if (rangeAlreadySelected) {
                        e.consume();
                    }
                    return;
                }

                if (autoSelectSameTimestamp) {
                    // 开启自动选择：判断"当前选中集是否恰为该行时间戳组"
                    boolean groupAlreadySelected = isCurrentSelectionEqualToGroup(idx);
                    wasSelectedAtPress = groupAlreadySelected;
                    if (groupAlreadySelected) {
                        // 阻止 ListView 收敛选择（保留整组选中），准备进入编辑
                        e.consume();
                    }
                    return;
                }

                // 关闭自动选择：保持原有逻辑
                int selCount = getListView().getSelectionModel().getSelectedIndices().size();
                if (selCount > 1) {
                    wasSelectedAtPress = false;
                    return;
                }
                wasSelectedAtPress = (idx >= 0
                        && getListView().getSelectionModel().getSelectedIndex() == idx);
            });

            addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
                if (isBlankSeparator(getIndex())) e.consume();
            });

            addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
                int idx = getIndex();
                if (idx < 0 || inEdit) return;
                if (isBlankSeparator(idx)) return;

                boolean noModifier = !e.isShiftDown() && !e.isControlDown() && !e.isMetaDown();

                if (autoExpandRows > 0 && noModifier) {
                    if (!wasSelectedAtPress) {
                        selectExpandedRange(idx);
                        return;
                    }
                    enterEditMode(e);
                    return;
                }

                if (autoSelectSameTimestamp && noModifier) {
                    if (!wasSelectedAtPress) {
                        // 首次点击：扩展为整组
                        selectSameTimestampGroup(idx);
                        return;              // 不进入编辑
                    } else {
                        // 已选中整组，二次点击：进入编辑
                        enterEditMode(e);
                        return;
                    }
                }

                // 关闭自动选择时的原有逻辑
                if (!wasSelectedAtPress) return;
                enterEditMode(e);
            });
        }

        private void enterEditMode(MouseEvent e) {
            int idx = getIndex();
            if (idx < 0) return;
            if (isBlankSeparator(idx)) return;
            LyricEditorView.this.editingRow = idx;
            inEdit = true;
            timeField.setMouseTransparent(false);
            textField.setMouseTransparent(false);

            double timeFieldRightEdge = playIcon.getWidth() + layout.getSpacing() + timeField.getWidth() + 5;
            boolean focusTime = e.getX() < timeFieldRightEdge;

            if (focusTime) {
                timeField.requestFocus();
                timeField.positionCaret(timeField.getText().length());
            } else {
                textField.requestFocus();
                textField.positionCaret(textField.getText().length());
            }
        }

        private void exitEditMode() {
            if (LyricEditorView.this.editingRow == getIndex()) {
                LyricEditorView.this.editingRow = -1;
            }
            inEdit = false;
            timeField.setMouseTransparent(true);
            textField.setMouseTransparent(true);
        }

        private void commitAndExit() {
            getListView().requestFocus();
        }

        private void endEditAndExit() {
            if (!inEdit) return;
            commit();

            boolean changed = snapshotOnFocus != null
                    && !snapshotOnFocus.equals(doc.serialize(false));
            snapshotOnFocus = null;

            exitEditMode();

            if (changed) {
                doc.setDirty(true);
                fireChanged();
            }
            // A full refresh here rebuilds cells before a click moving focus to
            // another row is delivered, so refresh only the edited cell.
            refreshAfterEdit();
            if (!autoScrollToPlaying) {
                updateLockedHighlight();
            }
            repaintAllCells();
        }

        private void beginEdit() { snapshotOnFocus = doc.serialize(false); }

        private void refreshAfterEdit() {
            int idx = getIndex();
            if (idx < 0 || idx >= getItems().size()) return;

            LyricLine item = getItems().get(idx);
            bound = item;

            if (item.timeMs == null && item.text.isBlank()) {
                bound = null;
                setGraphic(null);
                setText("");
                setStyle("-fx-background-color:transparent;");
                return;
            }

            timeField.setText(item.timeMs == null ? "" : LrcIo.formatTime(item.timeMs));
            textField.setText(item.text);
            setGraphic(layout);
            refreshPlayIcon();
            styleCell(this);
        }

        private void refreshPlayIcon() {
            playIcon.setStyle(isGroupFirst(getIndex()) ? ICON_ACTIVE : ICON_INACTIVE);
        }

        private void commit() {
            if (bound == null) return;
            Long   newTime = parseTime(timeField.getText());
            String newText = textField.getText();
            doc.editLine(getIndex(), newTime, newText);
        }

        private Long parseTime(String s) {
            s = s.trim();
            if (s.isEmpty()) return null;
            if (!s.startsWith("[")) s = "[" + s + "]";
            Matcher m = LrcIo.LRC_TAG.matcher(s);
            return m.matches() ? LrcIo.parseTimeMs(m) : null;
        }

        @Override
        protected void updateItem(LyricLine item, boolean empty) {
            super.updateItem(item, empty);

            if (!empty && item != null && item.timeMs == null && item.text.isBlank()) {
                bound = null;
                setGraphic(null);
                setText("");
                setStyle("-fx-background-color:transparent;");
                if (inEdit) {
                    inEdit = false;
                    timeField.setMouseTransparent(true);
                    textField.setMouseTransparent(true);
                    if (LyricEditorView.this.editingRow == getIndex()) {
                        LyricEditorView.this.editingRow = -1;
                    }
                }
                return;
            }

            if (empty || item == null) {
                bound = null;
                setGraphic(null);
                setStyle("");
                if (inEdit) {
                    inEdit = false;
                    timeField.setMouseTransparent(true);
                    textField.setMouseTransparent(true);
                    if (LyricEditorView.this.editingRow == getIndex()) {
                        LyricEditorView.this.editingRow = -1;
                    }
                }
                return;
            }
            bound = item;

            boolean shouldEdit = (LyricEditorView.this.editingRow == getIndex());
            if (shouldEdit != inEdit) {
                inEdit = shouldEdit;
                timeField.setMouseTransparent(!shouldEdit);
                textField.setMouseTransparent(!shouldEdit);
            }

            if (!inEdit) {
                timeField.setStyle(buildIdleTimeStyle());
                textField.setStyle(buildIdleTextStyle());
            }

            refreshPlayIcon();

            timeField.setText(item.timeMs == null ? "" : LrcIo.formatTime(item.timeMs));
            textField.setText(item.text);
            setGraphic(layout);
            styleCell(this);
        }

        private static final String ICON_ACTIVE   =
                "-fx-text-fill:#16a34a;-fx-font-size:27px;-fx-font-weight:bold;";
        private static final String ICON_HOVER    =
                "-fx-text-fill:#15803d;-fx-font-size:29px;-fx-font-weight:bold;";
        private static final String ICON_INACTIVE =
                "-fx-text-fill:transparent;-fx-font-size:27px;";
    }
}
