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
