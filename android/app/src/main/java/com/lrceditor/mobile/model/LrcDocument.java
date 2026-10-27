package com.lrceditor.mobile.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public final class LrcDocument {
    private static final int MAX_UNDO = 300;
    public static final int MOVE_MODE_TRANSFER = 0;
    public static final int MOVE_MODE_SWAP = 1;
    public static final int MOVE_MODE_OVERWRITE = 2;

    private final List<LyricLine> lines = new ArrayList<>();
    private final Deque<List<LyricLine>> undoStack = new ArrayDeque<>();
    private final Deque<List<LyricLine>> redoStack = new ArrayDeque<>();
    private long offset;
    private boolean dirty;
    private boolean chunked;

    public List<LyricLine> getLines() {
        return lines;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public long getOffset() {
        return offset;
    }

    public boolean isChunked() {
        return chunked;
    }

    public void restoreMetadata(long offset, boolean dirty, boolean chunked) {
        this.offset = offset;
        this.dirty = dirty;
        this.chunked = chunked;
    }

    public void snapshot() {
        undoStack.push(deepCopy());
        while (undoStack.size() > MAX_UNDO) undoStack.removeLast();
        redoStack.clear();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(deepCopy());
        replaceAll(undoStack.pop());
        dirty = true;
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(deepCopy());
        replaceAll(redoStack.pop());
        dirty = true;
    }

    public void load(String rawText) {
        lines.clear();
        lines.addAll(LrcIo.parse(rawText));
        offset = 0;
        dirty = false;
        chunked = false;
        undoStack.clear();
        redoStack.clear();
    }

    public void clear() {
        lines.clear();
        offset = 0;
        dirty = false;
        chunked = false;
        undoStack.clear();
        redoStack.clear();
    }

    public void editLine(int index, Long timeMs, String text) {
        if (index < 0 || index >= lines.size()) return;
        LyricLine line = lines.get(index);
        if (Objects.equals(line.timeMs, timeMs) && Objects.equals(line.text, text)) {
            return;
        }
        snapshot();
        line.timeMs = timeMs;
        line.text = text == null ? "" : text;
        dirty = true;
    }

    public void addLine(int index, LyricLine line) {
        snapshot();
        lines.add(Math.max(0, Math.min(index, lines.size())), line);
        dirty = true;
    }

    public void addLines(int index, List<LyricLine> newLines) {
        if (newLines == null || newLines.isEmpty()) return;
        snapshot();
        int position = Math.max(0, Math.min(index, lines.size()));
        lines.addAll(position, newLines);
        dirty = true;
    }

    public void stampMany(List<Integer> indices, long timeMs) {
        if (indices == null || indices.isEmpty()) return;
        snapshot();
        for (int index : indices) {
            if (index >= 0 && index < lines.size()) {
                lines.get(index).timeMs = timeMs;
            }
        }
        dirty = true;
    }

    public void removeMany(List<Integer> indices) {
        if (indices == null || indices.isEmpty()) return;
        snapshot();
        List<Integer> sorted = new ArrayList<>(indices);
        sorted.sort(Collections.reverseOrder());
        for (int index : sorted) {
            if (index >= 0 && index < lines.size()) {
                lines.remove(index);
            }
        }
        dirty = true;
    }

    public void shiftMany(List<Integer> indices, long deltaMs) {
        if (indices == null || indices.isEmpty() || deltaMs == 0) return;
        snapshot();
        for (int index : indices) {
            if (index >= 0 && index < lines.size()) {
                LyricLine line = lines.get(index);
                if (line.timeMs != null) {
                    line.timeMs = Math.max(0, line.timeMs + deltaMs);
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

        java.util.TreeSet<Integer> valid = new java.util.TreeSet<>();
        for (int index : indices) {
            if (index < 0 || index >= lines.size()) continue;
            if (!lines.get(index).isBlankSeparator()) valid.add(index);
        }
        if (valid.isEmpty()) return Collections.emptyList();

        List<Integer> selected = new ArrayList<>(valid);
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
            List<Integer> targets = new ArrayList<>(selected.size());
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
            replaceAll(working);
            dirty = true;
            return targets;
        }

        boolean insertTop = direction < 0 && selected.get(0) == 0;
        if (insertTop) {
            working.add(0, new LyricLine(0L, working.get(0).text));
        }

        List<Integer> adjusted = new ArrayList<>(selected.size());
        for (int index : selected) adjusted.add(index + (insertTop ? 1 : 0));

        boolean appendBottom = direction > 0
                && selected.get(selected.size() - 1) == originalLast;
        if (appendBottom) {
            int lastIndex = adjusted.get(adjusted.size() - 1);
            working.add(new LyricLine(
                    oneSecondAfterLatestTimestamp(working, lastIndex),
                    working.get(lastIndex).text));
        }

        List<Integer> targets = new ArrayList<>(adjusted.size());
        for (int i = 0; i < adjusted.size(); i++) {
            targets.add(adjusted.get(i) + direction);
        }

        if (effectiveMode == MOVE_MODE_SWAP) {
            moveContentSwap(
                    working, adjusted, direction, insertTop, appendBottom);
        } else {
            List<String> movedTexts = new ArrayList<>(adjusted.size());
            for (int index : adjusted) movedTexts.add(working.get(index).text);
            for (int index : adjusted) working.get(index).text = "";
            for (int i = 0; i < adjusted.size(); i++) {
                working.get(targets.get(i)).text = movedTexts.get(i);
            }
        }

        replaceAll(working);
        dirty = true;
        return targets;
    }

    private boolean transferWouldOverwrite(
            List<LyricLine> source,
            List<Integer> selected,
            int direction) {
        java.util.Set<Integer> selectedSet = new java.util.HashSet<>(selected);
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

    public void applyOffset(long targetOffset) {
        long delta = targetOffset - offset;
        if (delta == 0) return;
        snapshot();
        for (LyricLine line : lines) {
            if (line.timeMs != null) {
                line.timeMs = Math.max(0, line.timeMs + delta);
            }
        }
        offset = targetOffset;
        dirty = true;
    }

    public void toggleChunked() {
        if (chunked) {
            unChunk();
        } else {
            chunk();
        }
    }

    public List<Integer> findPlayingIndices(long positionMs) {
        Long latest = null;
        for (LyricLine line : lines) {
            if (line.timeMs != null && line.timeMs <= positionMs) {
                if (latest == null || line.timeMs > latest) latest = line.timeMs;
            }
        }
        if (latest == null) return Collections.emptyList();
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (latest.equals(lines.get(i).timeMs)) result.add(i);
        }
        return result;
    }

    public List<Integer> collectSameTimestampGroup(int index) {
        if (index < 0 || index >= lines.size()) return Collections.emptyList();
        Long time = lines.get(index).timeMs;
        if (time == null) return Collections.emptyList();
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (time.equals(lines.get(i).timeMs)) result.add(i);
        }
        return result;
    }

    public List<Integer> collectExpandedRange(int index, int extraRows) {
        if (index < 0 || index >= lines.size() || extraRows <= 0) {
            return Collections.emptyList();
        }
        List<Integer> result = new ArrayList<>();
        if (!lines.get(index).isBlankSeparator()) result.add(index);
        for (int i = index + 1;
             i < lines.size() && result.size() <= extraRows;
             i++) {
            if (!lines.get(i).isBlankSeparator()) result.add(i);
        }
        return result;
    }

    public Set<Integer> nonBlankIndices() {
        Set<Integer> result = new LinkedHashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).isBlankSeparator()) result.add(i);
        }
        return result;
    }

    public String serialize(boolean dropBlank) {
        StringBuilder out = new StringBuilder();
        for (LyricLine line : lines) {
            if (dropBlank && line.isBlankSeparator()) continue;
            out.append(line.toLrc()).append('\n');
        }
        return out.toString();
    }

    private void chunk() {
        snapshot();
        TreeMap<Long, List<LyricLine>> grouped = new TreeMap<>();
        List<LyricLine> untimed = new ArrayList<>();
        for (LyricLine line : lines) {
            if (line.timeMs == null) {
                if (!line.text.trim().isEmpty()) untimed.add(line);
            } else {
                grouped.computeIfAbsent(line.timeMs, ignored -> new ArrayList<>()).add(line);
            }
        }
        List<LyricLine> result = new ArrayList<>();
        boolean first = true;
        for (List<LyricLine> group : grouped.values()) {
            if (!first) result.add(new LyricLine(null, ""));
            first = false;
            result.addAll(group);
        }
        if (!untimed.isEmpty()) {
            result.add(new LyricLine(null, ""));
            result.addAll(untimed);
        }
        replaceAll(result);
        chunked = true;
        dirty = true;
    }

    private void unChunk() {
        snapshot();
        List<LyricLine> result = new ArrayList<>();
        for (LyricLine line : lines) {
            if (!line.isBlankSeparator()) result.add(line);
        }
        replaceAll(result);
        chunked = false;
        dirty = true;
    }

    private void replaceAll(List<LyricLine> replacement) {
        lines.clear();
        lines.addAll(replacement);
    }

    private List<LyricLine> deepCopy() {
        List<LyricLine> copy = new ArrayList<>(lines.size());
        for (LyricLine line : lines) copy.add(line.copy());
        return copy;
    }
}
