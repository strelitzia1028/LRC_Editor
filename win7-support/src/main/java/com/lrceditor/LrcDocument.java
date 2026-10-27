package com.lrceditor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.File;
import java.util.*;

public class LrcDocument {

    private static final int MAX_UNDO = 300;
    public static final int MOVE_MODE_TRANSFER = 0;
    public static final int MOVE_MODE_SWAP = 1;
    public static final int MOVE_MODE_OVERWRITE = 2;

    private final ObservableList<LyricLine> lines = FXCollections.observableArrayList();
    private final Deque<List<LyricLine>> undoStack = new ArrayDeque<>();
    private final Deque<List<LyricLine>> redoStack = new ArrayDeque<>();

    private long    offset = 0;
    private File    file;
    private boolean dirty  = false;
    private String  encoding = "UTF-8";
    private boolean bom = false;

    /** 是否处于"时间分块"模式 */
    private boolean chunked = false;

    public ObservableList<LyricLine> getLines() { return lines; }
    public File getFile()                 { return file; }
    public void setFile(File f)           { this.file = f; }
    public boolean isDirty()              { return dirty; }
    public void setDirty(boolean d)       { this.dirty = d; }
    public long getOffset()               { return offset; }
    public String getEncoding()           { return encoding; }
    public void setEncoding(String e)     { this.encoding = e; }
    public boolean hasBom()               { return bom; }
    public void setBom(boolean b)         { this.bom = b; }
    public boolean isChunked()            { return chunked; }

    // =============================================================
    // 撤销栈
    // =============================================================
    public void snapshot() {
        undoStack.push(deepCopy());
        while (undoStack.size() > MAX_UNDO) undoStack.removeLast();
        redoStack.clear();
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    public void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(deepCopy());
        lines.setAll(undoStack.pop());
        dirty = true;
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(deepCopy());
        lines.setAll(redoStack.pop());
        dirty = true;
    }

    private List<LyricLine> deepCopy() {
        List<LyricLine> out = new ArrayList<>(lines.size());
        for (LyricLine l : lines) out.add(l.copy());
        return out;
    }

    // =============================================================
    // 内容 & 编辑操作
    // =============================================================
    public void load(String rawText, File src) {
        lines.setAll(LrcIo.parse(rawText));
        file = src;
        offset = 0;
        dirty = false;
        chunked = false;
        undoStack.clear();
        redoStack.clear();
    }

    public void clear() {
        lines.clear();
        file = null;
        offset = 0;
        dirty = false;
        chunked = false;
        undoStack.clear();
        redoStack.clear();
    }

    public void editLine(int idx, Long newTimeMs, String newText) {
        if (idx < 0 || idx >= lines.size()) return;
        LyricLine l = lines.get(idx);
        if (Objects.equals(l.timeMs, newTimeMs) && Objects.equals(l.text, newText)) return;
        snapshot();
        l.timeMs = newTimeMs;
        l.text   = newText;
        dirty = true;
    }

    public void addLine(int idx, LyricLine line) {
        snapshot();
        lines.add(Math.max(0, Math.min(idx, lines.size())), line);
        dirty = true;
    }

    public void addLines(int idx, List<LyricLine> newLines) {
        if (newLines == null || newLines.isEmpty()) return;
        snapshot();
        int position = Math.max(0, Math.min(idx, lines.size()));
        lines.addAll(position, newLines);
        dirty = true;
    }

    public void removeLine(int idx) {
        if (idx < 0 || idx >= lines.size()) return;
        snapshot();
        lines.remove(idx);
        dirty = true;
    }

    public void stampAt(int idx, long ms) {
        if (idx < 0 || idx >= lines.size()) return;
        LyricLine l = lines.get(idx);
        if (l.timeMs != null && l.timeMs == ms) return;
        snapshot();
        l.timeMs = ms;
        dirty = true;
    }

    // =============================================================
    // 批量操作
    // =============================================================
    /** 批量打戳：给所有 idx 打同一时间戳（覆盖式） */
    public void stampMany(List<Integer> indices, long ms) {
        if (indices == null || indices.isEmpty()) return;
        snapshot();
        for (int i : indices) {
            if (i >= 0 && i < lines.size()) {
                lines.get(i).timeMs = ms;
            }
        }
        dirty = true;
    }

    /** 批量删除：从后往前删除，避免索引错位 */
    public void removeMany(List<Integer> indices) {
        if (indices == null || indices.isEmpty()) return;
        snapshot();
        List<Integer> sorted = new ArrayList<>(indices);
        Collections.sort(sorted, Collections.reverseOrder());
        for (int i : sorted) {
            if (i >= 0 && i < lines.size()) {
                lines.remove(i);
            }
        }
        dirty = true;
    }

    /** 批量偏移时间戳：选中行的时间戳统一 ±deltaMs */
    public void shiftMany(List<Integer> indices, long deltaMs) {
        if (indices == null || indices.isEmpty() || deltaMs == 0) return;
        snapshot();
        for (int i : indices) {
            if (i >= 0 && i < lines.size()) {
                LyricLine l = lines.get(i);
                if (l.timeMs != null) {
                    l.timeMs = Math.max(0, l.timeMs + deltaMs);
                }
            }
        }
        dirty = true;
    }

    /**
     * 将选中行的歌词内容整体移动一格，所有现有行的时间戳保持不变。
     * direction 为 -1 时上移，为 1 时下移。
     */
    public List<Integer> moveContentMany(List<Integer> indices, int direction) {
        return moveContentMany(indices, direction, false);
    }

    public List<Integer> moveContentMany(
            List<Integer> indices, int direction, boolean transferMode) {
        return moveContentMany(indices, direction,
                transferMode ? MOVE_MODE_SWAP : MOVE_MODE_OVERWRITE);
    }

    /**
     * mode 使用 MOVE_MODE_TRANSFER、MOVE_MODE_SWAP 或 MOVE_MODE_OVERWRITE。
     */
    public List<Integer> moveContentMany(
            List<Integer> indices, int direction, int moveMode) {
        if ((direction != -1 && direction != 1)
                || moveMode < MOVE_MODE_TRANSFER
                || moveMode > MOVE_MODE_OVERWRITE
                || indices == null || indices.isEmpty() || lines.isEmpty()) {
            return Collections.emptyList();
        }

        TreeSet<Integer> valid = new TreeSet<Integer>();
        for (int index : indices) {
            if (index < 0 || index >= lines.size()) continue;
            LyricLine line = lines.get(index);
            if (line.timeMs != null || !line.text.trim().isEmpty()) valid.add(index);
        }
        if (valid.isEmpty()) return Collections.emptyList();

        List<Integer> selected = new ArrayList<Integer>(valid);
        int effectiveMode = moveMode;
        if (moveMode == MOVE_MODE_TRANSFER
                && !transferWouldOverwrite(lines, selected, direction)) {
            effectiveMode = MOVE_MODE_OVERWRITE;
        }
        int originalLast = lines.size() - 1;
        snapshot();
        List<LyricLine> working = deepCopy();

        if (effectiveMode == MOVE_MODE_TRANSFER) {
            int first = selected.get(0);
            int last = selected.get(selected.size() - 1);
            List<Integer> targets = new ArrayList<Integer>(selected.size());
            if (direction < 0) {
                working.add(0, new LyricLine(0L, working.get(0).text));
                for (int i = 0; i <= last; i++) {
                    working.get(i).text = working.get(i + 1).text;
                }
                working.get(last + 1).text = "";
                working.get(last + 1).timeMs = 0L;
                targets.addAll(selected);
            } else {
                working.add(new LyricLine(
                        oneSecondAfterLatestTimestamp(working, working.size() - 1),
                        working.get(working.size() - 1).text));
                for (int i = working.size() - 1; i > first; i--) {
                    working.get(i).text = working.get(i - 1).text;
                }
                working.get(first).text = "";
                working.get(first).timeMs = 0L;
                for (int index : selected) targets.add(index + 1);
            }
            lines.setAll(working);
            dirty = true;
            return targets;
        }

        boolean insertTop = direction < 0 && selected.get(0) == 0;
        if (insertTop) {
            working.add(0, new LyricLine(0L, working.get(0).text));
        }

        List<Integer> adjusted = new ArrayList<Integer>(selected.size());
        for (int index : selected) adjusted.add(index + (insertTop ? 1 : 0));

        boolean appendBottom = direction > 0
                && selected.get(selected.size() - 1) == originalLast;
        if (appendBottom) {
            int lastIndex = adjusted.get(adjusted.size() - 1);
            working.add(new LyricLine(
                    oneSecondAfterLatestTimestamp(working, lastIndex),
                    working.get(lastIndex).text));
        }

        List<Integer> targets = new ArrayList<Integer>(adjusted.size());
        for (int i = 0; i < adjusted.size(); i++) {
            targets.add(adjusted.get(i) + direction);
        }

        if (effectiveMode == MOVE_MODE_SWAP) {
            moveContentSwap(
                    working, adjusted, direction, insertTop, appendBottom);
        } else {
            List<String> movedTexts = new ArrayList<String>(adjusted.size());
            for (int index : adjusted) movedTexts.add(working.get(index).text);
            for (int index : adjusted) working.get(index).text = "";
            for (int i = 0; i < adjusted.size(); i++) {
                working.get(targets.get(i)).text = movedTexts.get(i);
            }
        }

        lines.setAll(working);
        dirty = true;
        return targets;
    }

    private boolean transferWouldOverwrite(
            List<LyricLine> source,
            List<Integer> selected,
            int direction) {
        java.util.Set<Integer> selectedSet =
                new java.util.HashSet<Integer>(selected);
        for (int index : selected) {
            int target = index + direction;
            if (target < 0 || target >= source.size()) return true;
            if (selectedSet.contains(target)) continue;
            if (!source.get(target).text.trim().isEmpty()) return true;
        }
        return false;
    }

    private void moveContentSwap(
            List<LyricLine> working,
            List<Integer> selected,
            int direction,
            boolean insertedTop,
            boolean appendedBottom) {
        int runStart = selected.get(0);
        int runEnd = runStart;
        for (int i = 1; i < selected.size(); i++) {
            int index = selected.get(i);
            if (index == runEnd + 1) {
                runEnd = index;
            } else {
                moveTransferRun(working, runStart, runEnd, direction,
                        insertedTop, appendedBottom);
                runStart = index;
                runEnd = index;
            }
        }
        moveTransferRun(working, runStart, runEnd, direction,
                insertedTop, appendedBottom);
    }

    private void moveTransferRun(
            List<LyricLine> working,
            int start,
            int end,
            int direction,
            boolean insertedTop,
            boolean appendedBottom) {
        if (direction < 0) {
            if (insertedTop && start == 1) {
                for (int i = start; i <= end; i++) {
                    working.get(i - 1).text = working.get(i).text;
                }
                working.get(end).text = "";
                return;
            }
            String displaced = working.get(start - 1).text;
            for (int i = start - 1; i < end; i++) {
                working.get(i).text = working.get(i + 1).text;
            }
            working.get(end).text = displaced;
            return;
        }

        if (appendedBottom && end == working.size() - 2) {
            for (int i = end; i >= start; i--) {
                working.get(i + 1).text = working.get(i).text;
            }
            working.get(start).text = "";
            return;
        }
        String displaced = working.get(end + 1).text;
        for (int i = end + 1; i > start; i--) {
            working.get(i).text = working.get(i - 1).text;
        }
        working.get(start).text = displaced;
    }

    private long oneSecondAfterLatestTimestamp(
            List<LyricLine> source, int lastIndex) {
        long latest = -1L;
        for (int i = 0; i <= lastIndex && i < source.size(); i++) {
            Long time = source.get(i).timeMs;
            if (time != null && time > latest) latest = time;
        }
        if (latest < 0) return 1000L;
        return latest > Long.MAX_VALUE - 1000L
                ? Long.MAX_VALUE : latest + 1000L;
    }

    // =============================================================
    // offset / 分块
    // =============================================================
    public void applyOffset(long target) {
        long delta = target - offset;
        if (delta == 0) return;
        snapshot();
        for (LyricLine l : lines) {
            if (l.timeMs != null) l.timeMs = Math.max(0, l.timeMs + delta);
        }
        offset = target;
        dirty = true;
    }

    public void toggleTimeChunk() {
        if (chunked) doUnchunk();
        else         doChunk();
    }

    private void doChunk() {
        snapshot();
        TreeMap<Long, List<LyricLine>> grouped = new TreeMap<>();
        List<LyricLine> untimed = new ArrayList<>();
        for (LyricLine l : lines) {
            if (l.timeMs == null) {
                if (!l.text.trim().isEmpty()) untimed.add(l);
            } else {
                grouped.computeIfAbsent(l.timeMs, k -> new ArrayList<>()).add(l);
            }
        }
        List<LyricLine> out = new ArrayList<>();
        boolean first = true;
        for (Map.Entry<Long, List<LyricLine>> e : grouped.entrySet()) {
            if (!first) out.add(new LyricLine(null, ""));
            first = false;
            out.addAll(e.getValue());
        }
        if (!untimed.isEmpty()) {
            out.add(new LyricLine(null, ""));
            out.addAll(untimed);
        }
        lines.setAll(out);
        chunked = true;
        dirty = true;
    }

    private void doUnchunk() {
        snapshot();
        List<LyricLine> out = new ArrayList<>(lines.size());
        for (LyricLine l : lines) {
            if (l.timeMs == null && l.text.trim().isEmpty()) continue;
            out.add(l);
        }
        lines.setAll(out);
        chunked = false;
        dirty = true;
    }

    public int firstUntimedIndex() {
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).timeMs == null) return i;
        return -1;
    }

    // =============================================================
    // 播放高亮
    // =============================================================
    public List<Integer> findPlayingLineIndices(double sec) {
        long ms = (long) (sec * 1000);

        Long maxTimeMs = null;
        for (LyricLine l : lines) {
            if (l.timeMs != null && l.timeMs <= ms) {
                if (maxTimeMs == null || l.timeMs > maxTimeMs) {
                    maxTimeMs = l.timeMs;
                }
            }
        }
        if (maxTimeMs == null) return Collections.emptyList();

        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            LyricLine l = lines.get(i);
            if (maxTimeMs.equals(l.timeMs)) result.add(i);
        }
        return result;
    }

    public int findPlayingLineIndex(double sec) {
        List<Integer> list = findPlayingLineIndices(sec);
        return list.isEmpty() ? -1 : list.get(0);
    }

    public String serialize(boolean dropBlank) {
        StringBuilder sb = new StringBuilder();
        for (LyricLine l : lines) {
            if (dropBlank && l.text.trim().isEmpty() && l.timeMs == null) continue;
            sb.append(l.toLrc()).append('\n');
        }
        return sb.toString();
    }
}
