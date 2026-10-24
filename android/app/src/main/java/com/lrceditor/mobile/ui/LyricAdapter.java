package com.lrceditor.mobile.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.lrceditor.mobile.model.LrcDocument;
import com.lrceditor.mobile.model.LyricLine;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class LyricAdapter extends BaseAdapter {
    public interface Listener {
        void onPlayRequested(int position);
        void onRowTapped(int position);
        void onRowLongPressed(int position);
    }

    private final Context context;
    private final LrcDocument document;
    private final Listener listener;
    private UiPalette palette;
    private Set<Integer> selected = Collections.emptySet();
    private Set<Integer> playing = Collections.emptySet();

    public LyricAdapter(Context context, LrcDocument document, Listener listener) {
        this.context = context;
        this.document = document;
        this.listener = listener;
    }

    public void setPalette(UiPalette palette) {
        this.palette = palette;
        notifyDataSetChanged();
    }

    public void setSelected(Collection<Integer> indices) {
        selected = indices == null ? Collections.emptySet() : new HashSet<>(indices);
        notifyDataSetChanged();
    }

    public void setPlaying(Collection<Integer> indices) {
        playing = indices == null ? Collections.emptySet() : new HashSet<>(indices);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return document.getLines().size();
    }

    @Override
    public LyricLine getItem(int position) {
        return document.getLines().get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Row row;
        if (convertView instanceof LinearLayout && convertView.getTag() instanceof Row) {
            row = (Row) convertView.getTag();
        } else {
            row = createRow();
            convertView = row.root;
            convertView.setTag(row);
        }

        LyricLine line = getItem(position);
        if (line.isBlankSeparator()) {
            row.root.setMinimumHeight(dp(18));
            row.root.setLayoutParams(new AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(18)));
            row.root.setBackgroundColor(Color.TRANSPARENT);
            row.playIcon.setVisibility(View.GONE);
            row.timeView.setVisibility(View.GONE);
            row.textView.setVisibility(View.GONE);
            row.root.setContentDescription(null);
            row.root.setClickable(false);
            row.root.setOnClickListener(null);
            row.root.setOnLongClickListener(null);
            return convertView;
        }

        row.root.setMinimumHeight(dp(52));
        row.root.setLayoutParams(new AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        row.playIcon.setVisibility(isGroupFirst(position) ? View.VISIBLE : View.INVISIBLE);
        row.timeView.setVisibility(View.VISIBLE);
        row.textView.setVisibility(View.VISIBLE);

        boolean isSelected = selected.contains(position);
        boolean isPlaying = playing.contains(position);
        int background;
        if (isSelected && isPlaying) {
            background = palette.selectedPlaying;
        } else if (isSelected) {
            background = palette.selected;
        } else if (isPlaying) {
            background = palette.playing;
        } else {
            background = position % 2 == 0 ? palette.list : palette.listAlt;
        }
        row.root.setBackground(rounded(background, 6));
        row.timeView.setText(line.timeMs == null ? "" : formatTime(line.timeMs));
        row.timeView.setTextColor(palette.accent);
        row.textView.setText(line.text);
        row.textView.setTextColor(palette.text);
        row.textView.setTypeface(Typeface.DEFAULT, isPlaying ? Typeface.BOLD : Typeface.NORMAL);
        row.playIcon.setTextColor(Color.rgb(22, 163, 74));
        row.playIcon.setOnClickListener(v -> {
            if (line.timeMs != null && listener != null) {
                listener.onPlayRequested(position);
            }
        });
        row.playIcon.setContentDescription("从这一行开始播放");
        StringBuilder description = new StringBuilder("第 ")
                .append(position + 1)
                .append(" 行");
        if (isPlaying) description.append("，播放中");
        if (isSelected) description.append("，已选中");
        row.root.setContentDescription(description.toString());
        row.root.setClickable(true);
        row.root.setLongClickable(true);
        row.root.setOnClickListener(v -> {
            if (listener != null) listener.onRowTapped(position);
        });
        row.root.setOnLongClickListener(v -> {
            if (listener != null) listener.onRowLongPressed(position);
            return true;
        });
        return convertView;
    }

    private Row createRow() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(10), 0, dp(12), 0);
        root.setLayoutParams(new AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        TextView playIcon = new TextView(context);
        playIcon.setGravity(Gravity.CENTER);
        playIcon.setText("▶");
        playIcon.setTextSize(17);
        playIcon.setClickable(true);
        playIcon.setFocusable(true);
        LinearLayout.LayoutParams playParams =
                new LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(playIcon, playParams);

        TextView timeView = new TextView(context);
        timeView.setGravity(Gravity.CENTER_VERTICAL);
        timeView.setTextSize(12);
        timeView.setTypeface(Typeface.MONOSPACE);
        timeView.setSingleLine(true);
        LinearLayout.LayoutParams timeParams =
                new LinearLayout.LayoutParams(dp(94), ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(timeView, timeParams);

        TextView textView = new TextView(context);
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setTextSize(16);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        textParams.setMarginStart(dp(4));
        root.addView(textView, textParams);
        return new Row(root, playIcon, timeView, textView);
    }

    private boolean isGroupFirst(int position) {
        LyricLine line = getItem(position);
        if (line.timeMs == null) return false;
        if (position == 0) return true;
        Long previous = getItem(position - 1).timeMs;
        return previous == null || !line.timeMs.equals(previous);
    }

    private String formatTime(long timeMs) {
        return String.format(Locale.ROOT, "%02d:%02d.%03d",
                timeMs / 60_000, (timeMs / 1000) % 60, timeMs % 1000);
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class Row {
        final LinearLayout root;
        final TextView playIcon;
        final TextView timeView;
        final TextView textView;

        Row(LinearLayout root, TextView playIcon, TextView timeView, TextView textView) {
            this.root = root;
            this.playIcon = playIcon;
            this.timeView = timeView;
            this.textView = textView;
        }
    }
}
