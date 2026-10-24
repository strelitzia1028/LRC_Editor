package com.lrceditor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.File;
import java.util.*;

public class LrcDocument {

    private static final int MAX_UNDO = 300;

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
                if (!l.text.isBlank()) untimed.add(l);
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
            if (l.timeMs == null && l.text.isBlank()) continue;
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
            if (dropBlank && l.text.isBlank() && l.timeMs == null) continue;
            sb.append(l.toLrc()).append('\n');
        }
        return sb.toString();
    }
}
