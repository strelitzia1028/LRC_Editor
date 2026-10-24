package com.lrceditor.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.lrceditor.mobile.audio.AudioSession;
import com.lrceditor.mobile.audio.WaveformExtractor;
import com.lrceditor.mobile.model.LrcDocument;
import com.lrceditor.mobile.model.LrcExportFormatter;
import com.lrceditor.mobile.model.LrcIo;
import com.lrceditor.mobile.model.LrcPasteParser;
import com.lrceditor.mobile.model.LyricLine;
import com.lrceditor.mobile.ui.LyricAdapter;
import com.lrceditor.mobile.ui.UiPalette;
import com.lrceditor.mobile.view.RecordView;
import com.lrceditor.mobile.view.WaveformView;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;

public final class MainActivity extends Activity {
    private static final int REQUEST_OPEN_LRC = 1001;
    private static final int REQUEST_OPEN_AUDIO = 1002;
    private static final int REQUEST_SAVE_LRC = 1003;
    private static final int REQUEST_EXPORT_LRC = 1004;

    private static final String PREFS = "lrc_editor_mobile";
    private static final String PREF_DARK = "dark_mode";
    private static final String PREF_AUTO_SYNC = "auto_scroll";
    private static final String PREF_AUTO_GROUP = "auto_group";
    private static final String PREF_AUTO_EXPAND = "auto_expand";
    private static final String PREF_PREVIEW_COLOR = "preview_color";
    private static final String PREF_ADVANCE_MS = "lyric_advance_ms";
    private static final String STATE_DOCUMENT = "document_text";
    private static final String STATE_LRC_URI = "lrc_uri";
    private static final String STATE_LRC_NAME = "lrc_name";
    private static final String STATE_AUDIO_URI = "audio_uri";
    private static final String STATE_AUDIO_NAME = "audio_name";
    private static final String STATE_ENCODING = "encoding";
    private static final String STATE_BOM = "bom";
    private static final String STATE_OFFSET = "offset";
    private static final String STATE_DIRTY = "dirty";
    private static final String STATE_CHUNKED = "chunked";

    private final LrcDocument document = new LrcDocument();
    private final AudioSession audio = new AudioSession();
    private final Set<Integer> selection = new LinkedHashSet<>();
    private final Set<Integer> playing = new LinkedHashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private SharedPreferences preferences;
    private UiPalette palette;
    private LyricAdapter adapter;
    private boolean darkMode;
    private boolean autoSyncSelection;
    private boolean applyingAutoSelection;
    private boolean autoGroup;
    private int autoExpand;
    private int lyricAdvanceMs;
    private int previewColor;
    private boolean userSeeking;
    private int waveformRequest;
    private String pendingExportText;

    private Uri lrcUri;
    private Uri audioUri;
    private String lrcName;
    private String audioName;
    private String encoding = "UTF-8";
    private boolean bom;
    private long durationMs;
    private long currentPositionMs;
    private Set<Integer> lastAutoSyncGroup = Collections.emptySet();

    private LinearLayout root;
    private LinearLayout appBar;
    private LinearLayout hero;
    private LinearLayout timelineBand;
    private LinearLayout transportBar;
    private HorizontalScrollView quickActionScroll;
    private TextView titleView;
    private TextView songTitle;
    private TextView timeLabel;
    private TextView previewPrev;
    private TextView previewCurrent;
    private TextView previewNext;
    private TextView emptyState;
    private TextView statusText;
    private ImageButton menuButton;
    private ImageButton playButton;
    private ImageButton rewindButton;
    private ImageButton forwardButton;
    private Button stampButton;
    private Button editLineButton;
    private Button addLineButton;
    private Button insertRowsButton;
    private Button pasteLyricsButton;
    private Button deleteLineButton;
    private Button playFromLineButton;
    private Button undoButton;
    private Button redoButton;
    private ListView lyricList;
    private SeekBar seekBar;
    private RecordView recordView;
    private WaveformView waveformView;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!userSeeking) {
                currentPositionMs = audio.getPositionMs();
                updateTimeUi(currentPositionMs);
            }
            handler.postDelayed(this, 100);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        darkMode = preferences.getBoolean(PREF_DARK, true);
        setTheme(darkMode ? R.style.Theme_LRC_Dark : R.style.Theme_LRC_Light);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        palette = new UiPalette(this, darkMode);
        autoSyncSelection = preferences.getBoolean(PREF_AUTO_SYNC, true);
        autoGroup = preferences.getBoolean(PREF_AUTO_GROUP, false);
        autoExpand = Math.max(0, Math.min(10,
                preferences.getInt(PREF_AUTO_EXPAND, 0)));
        lyricAdvanceMs = Math.max(0, preferences.getInt(PREF_ADVANCE_MS, 0));
        previewColor = preferences.getInt(PREF_PREVIEW_COLOR,
                darkMode ? Color.WHITE : Color.BLACK);

        bindViews();
        configureWindow();
        configureAdapter();
        configureListeners();
        applyPalette();
        updateAllUi();
        restoreSession(savedInstanceState);
        handler.post(ticker);
    }

    private void bindViews() {
        root = findViewById(R.id.root);
        appBar = findViewById(R.id.appBar);
        hero = findViewById(R.id.hero);
        timelineBand = findViewById(R.id.timelineBand);
        transportBar = findViewById(R.id.transportBar);
        quickActionScroll = findViewById(R.id.quickActionScroll);
        titleView = findViewById(R.id.titleView);
        songTitle = findViewById(R.id.songTitle);
        timeLabel = findViewById(R.id.timeLabel);
        previewPrev = findViewById(R.id.previewPrev);
        previewCurrent = findViewById(R.id.previewCurrent);
        previewNext = findViewById(R.id.previewNext);
        emptyState = findViewById(R.id.emptyState);
        statusText = findViewById(R.id.statusText);
        menuButton = findViewById(R.id.menuButton);
        playButton = findViewById(R.id.playButton);
        rewindButton = findViewById(R.id.rewindButton);
        forwardButton = findViewById(R.id.forwardButton);
        stampButton = findViewById(R.id.stampButton);
        editLineButton = findViewById(R.id.editLineButton);
        addLineButton = findViewById(R.id.addLineButton);
        insertRowsButton = findViewById(R.id.insertRowsButton);
        pasteLyricsButton = findViewById(R.id.pasteLyricsButton);
        deleteLineButton = findViewById(R.id.deleteLineButton);
        playFromLineButton = findViewById(R.id.playFromLineButton);
        undoButton = findViewById(R.id.undoButton);
        redoButton = findViewById(R.id.redoButton);
        lyricList = findViewById(R.id.lyricList);
        seekBar = findViewById(R.id.seekBar);
        recordView = findViewById(R.id.recordView);
        waveformView = findViewById(R.id.waveformView);
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(palette.surface);
        window.setNavigationBarColor(palette.surface);
        View decor = window.getDecorView();
        if (darkMode) {
            decor.setSystemUiVisibility(0);
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(left, top, right, bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    private void configureAdapter() {
        adapter = new LyricAdapter(this, document, new LyricAdapter.Listener() {
            @Override
            public void onPlayRequested(int position) {
                playFromLine(position);
            }

            @Override
            public void onRowTapped(int position) {
                handleRowTap(position);
            }

            @Override
            public void onRowLongPressed(int position) {
                if (position >= document.getLines().size()) return;
                if (document.getLines().get(position).isBlankSeparator()) return;
                if (!selection.remove(position)) selection.add(position);
                applySelection();
            }
        });
        adapter.setPalette(palette);
        lyricList.setAdapter(adapter);
    }

    private void configureListeners() {
        menuButton.setOnClickListener(this::showMenu);
        playButton.setOnClickListener(v -> togglePlayback());
        rewindButton.setOnClickListener(v -> seekRelative(-3000));
        forwardButton.setOnClickListener(v -> seekRelative(3000));
        stampButton.setOnClickListener(v -> stampSelection());
        editLineButton.setOnClickListener(v -> editSelectedLine());
        addLineButton.setOnClickListener(v -> insertNewLine());
        insertRowsButton.setOnClickListener(v -> showInsertRowsDialog());
        pasteLyricsButton.setOnClickListener(v -> pasteMultilineLyrics());
        deleteLineButton.setOnClickListener(v -> deleteSelectedLines());
        playFromLineButton.setOnClickListener(v -> playFromSelection());
        undoButton.setOnClickListener(v -> undo());
        redoButton.setOnClickListener(v -> redo());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser || durationMs <= 0) return;
                userSeeking = true;
                long target = durationMs * progress / 1000L;
                currentPositionMs = target;
                updateTimeUi(target);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                if (durationMs > 0) {
                    long target = durationMs * bar.getProgress() / 1000L;
                    audio.seekTo(target);
                    currentPositionMs = target;
                    updateTimeUi(target);
                }
                userSeeking = false;
            }
        });

        waveformView.setOnSeekListener(fraction -> {
            if (durationMs <= 0) return;
            long target = (long) (durationMs * fraction);
            audio.seekTo(target);
            currentPositionMs = target;
            updateTimeUi(target);
        });
    }

    private void applyPalette() {
        root.setBackgroundColor(palette.background);
        appBar.setBackgroundColor(palette.surface);
        hero.setBackgroundColor(palette.surface);
        timelineBand.setBackgroundColor(palette.background);
        transportBar.setBackgroundColor(palette.surface);
        quickActionScroll.setBackgroundColor(palette.surface);
        findViewById(R.id.divider).setBackgroundColor(palette.border);

        titleView.setTextColor(palette.text);
        songTitle.setTextColor(palette.text);
        timeLabel.setTextColor(palette.accent);
        previewPrev.setTextColor(palette.muted);
        previewNext.setTextColor(palette.muted);
        emptyState.setTextColor(palette.muted);
        statusText.setTextColor(palette.muted);
        previewCurrent.setTextColor(previewColor);

        styleButton(stampButton, true);
        styleButton(editLineButton, false);
        styleButton(addLineButton, false);
        styleButton(insertRowsButton, false);
        styleButton(pasteLyricsButton, false);
        styleButton(deleteLineButton, false);
        styleButton(playFromLineButton, false);
        styleButton(undoButton, false);
        styleButton(redoButton, false);
        tintImageButton(menuButton);
        tintImageButton(playButton);
        tintImageButton(rewindButton);
        tintImageButton(forwardButton);

        lyricList.setBackgroundColor(palette.list);
        seekBar.setProgressTintList(ColorStateList.valueOf(palette.accent));
        seekBar.setThumbTintList(ColorStateList.valueOf(palette.accent));
        waveformView.setDark(darkMode);
        recordView.setDark(darkMode);
        adapter.setPalette(palette);
        adapter.setSelected(selection);
        adapter.setPlaying(playing);
    }

    private void styleButton(Button button, boolean primary) {
        int fill = primary ? palette.accentStrong : palette.button;
        int textColor = primary ? Color.WHITE : palette.text;
        GradientDrawable shape = rounded(fill, 8);
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(Color.argb(48, 255, 255, 255)),
                shape, null);
        button.setBackground(ripple);
        button.setTextColor(textColor);
        button.setAllCaps(false);
        button.setMinHeight(dp(40));
    }

    private void tintImageButton(ImageButton button) {
        Drawable drawable = button.getDrawable();
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setTint(palette.text);
            button.setImageDrawable(drawable);
        }
    }

    private void showMenu(View anchor) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        addMenuAction(labels, actions, "导入歌词", this::openLyricsDocument);
        addMenuAction(labels, actions, "导入音频", this::openAudioDocument);
        addMenuAction(labels, actions, "保存歌词", () -> saveLyrics(false));
        addMenuAction(labels, actions, "另存为", () -> createLyricsDocument(false));
        addMenuAction(labels, actions, "导出歌词", this::showExportDialog);
        addMenuAction(labels, actions, "新建文档", this::confirmNewDocument);
        addMenuAction(labels, actions, document.canUndo() ? "撤销" : "撤销（无可用记录）",
                this::undo);
        addMenuAction(labels, actions, document.canRedo() ? "重做" : "重做（无可用记录）",
                this::redo);
        addMenuAction(labels, actions, "时间分块 / 统合", () -> {
            document.toggleChunked();
            selection.clear();
            playing.clear();
            adapter.notifyDataSetChanged();
            updateAllUi();
        });
        addMenuAction(labels, actions, "批量偏移时间戳", this::showOffsetDialog);
        addMenuAction(labels, actions, "自定义插入 N 行", this::showInsertRowsDialog);
        addMenuAction(labels, actions, "粘贴多行歌词", this::pasteMultilineLyrics);
        addMenuAction(labels, actions,
                autoSyncSelection ? "自动同步：开" : "自动同步：关", () -> {
            boolean enable = !autoSyncSelection;
            autoSyncSelection = enable;
            preferences.edit().putBoolean(PREF_AUTO_SYNC, autoSyncSelection).apply();
            updatePlayingGroup();
            if (enable) {
                applyAutoSelection(new LinkedHashSet<>(
                        document.findPlayingIndices(displayPositionMs())));
            }
            updateAllUi();
        });
        addMenuAction(labels, actions,
                "自定义提前 " + lyricAdvanceMs + " ms", this::showAdvanceDialog);
        addMenuAction(labels, actions, autoGroup ? "自动选择同组：开" : "自动选择同组：关",
                this::toggleAutoGroup);
        addMenuAction(labels, actions,
                autoExpand > 0 ? "自动扩选 " + autoExpand + " 行" : "自动扩选 0 行",
                this::showAutoExpandDialog);
        addMenuAction(labels, actions, "外观", this::showAppearanceDialog);
        addMenuAction(labels, actions, "当前播放行颜色", this::showPreviewColorDialog);
        addMenuAction(labels, actions, "关于", this::showAbout);
        new AlertDialog.Builder(this)
                .setTitle("更多")
                .setItems(labels.toArray(new String[0]),
                        (dialog, which) -> actions.get(which).run())
                .setNegativeButton("关闭", null)
                .show();
    }

    private void addMenuAction(List<String> labels, List<Runnable> actions,
                               String label, Runnable action) {
        labels.add(label);
        actions.add(action);
    }

    private void showAppearanceDialog() {
        new AlertDialog.Builder(this)
                .setTitle("外观")
                .setSingleChoiceItems(new String[]{"深色模式", "浅色模式"},
                        darkMode ? 0 : 1, (dialog, which) -> {
                            dialog.dismiss();
                            switchTheme(which == 0);
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showPreviewColorDialog() {
        String[] labels = {"黑色", "白色", "红色", "绿色", "蓝色"};
        int[] colors = {
                Color.BLACK,
                Color.WHITE,
                Color.rgb(239, 68, 68),
                Color.rgb(34, 197, 94),
                Color.rgb(59, 130, 246)
        };
        int checked = 0;
        for (int i = 0; i < colors.length; i++) {
            if (colors[i] == previewColor) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("当前播放行颜色")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    previewColor = colors[which];
                    preferences.edit().putInt(PREF_PREVIEW_COLOR, previewColor).apply();
                    previewCurrent.setTextColor(previewColor);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openLyricsDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_OPEN_LRC);
    }

    private void openAudioDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        startActivityForResult(intent, REQUEST_OPEN_AUDIO);
    }

    private void createLyricsDocument(boolean export) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        String name = baseName(lrcName != null ? lrcName
                : audioName != null ? audioName : "lyrics");
        intent.putExtra(Intent.EXTRA_TITLE,
                (TextUtils.isEmpty(name) ? "lyrics" : name) + ".lrc");
        startActivityForResult(intent, export ? REQUEST_EXPORT_LRC : REQUEST_SAVE_LRC);
    }

    private void saveLyrics(boolean export) {
        if (lrcUri == null) {
            createLyricsDocument(export);
            return;
        }
        writeLyrics(lrcUri, export, lrcName);
    }

    private void writeLyrics(Uri uri, boolean export, String displayName) {
        String content = export
                ? (pendingExportText != null ? pendingExportText : document.serialize(true))
                : document.serialize(false);
        ioExecutor.execute(() -> {
            try {
                ContentResolver resolver = getContentResolver();
                try (OutputStream output = resolver.openOutputStream(uri)) {
                    if (output == null) throw new IllegalStateException("无法写入文件");
                    LrcIo.write(output, content, false);
                }
                runOnUiThread(() -> {
                    if (!export) {
                        lrcUri = uri;
                        lrcName = displayName;
                        document.setDirty(false);
                    } else {
                        pendingExportText = null;
                    }
                    updateAllUi();
                    toast(export ? "歌词已导出" : "歌词已保存");
                });
            } catch (Throwable error) {
                runOnUiThread(() -> toast("保存失败：" + messageOf(error)));
            }
        });
    }

    private void showExportDialog() {
        final boolean[] merge = {false};
        LinearLayout rootBox = new LinearLayout(this);
        rootBox.setOrientation(LinearLayout.VERTICAL);
        rootBox.setPadding(dp(20), dp(10), dp(20), dp(10));

        TextView pathTitle = new TextView(this);
        pathTitle.setText("保存路径");
        pathTitle.setTextColor(palette.muted);
        pathTitle.setTextSize(12);
        rootBox.addView(pathTitle);

        TextView pathValue = new TextView(this);
        pathValue.setText("系统文件选择器（默认下载目录）");
        pathValue.setTextColor(palette.text);
        pathValue.setTextSize(14);
        pathValue.setPadding(0, dp(4), 0, dp(8));
        Button pathButton = new Button(this);
        pathButton.setText("…");
        styleButton(pathButton, false);
        LinearLayout pathRow = new LinearLayout(this);
        pathRow.setOrientation(LinearLayout.HORIZONTAL);
        pathRow.setGravity(Gravity.CENTER_VERTICAL);
        pathRow.addView(pathValue, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        pathRow.addView(pathButton, new LinearLayout.LayoutParams(dp(48), dp(40)));
        rootBox.addView(pathRow);

        Button mergeButton = new Button(this);
        styleButton(mergeButton, false);
        rootBox.addView(mergeButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        TextView hint = new TextView(this);
        hint.setText("合并后相同歌词内容会放在首次出现的位置，"
                + "所有时间戳按从小到大写入同一行。");
        hint.setTextColor(palette.muted);
        hint.setTextSize(12);
        hint.setPadding(0, dp(8), 0, dp(8));
        rootBox.addView(hint);

        TextView preview = new TextView(this);
        preview.setTextIsSelectable(true);
        preview.setTypeface(Typeface.MONOSPACE);
        preview.setTextColor(palette.text);
        preview.setTextSize(12);
        preview.setPadding(dp(8), dp(8), dp(8), dp(8));
        ScrollView previewScroll = new ScrollView(this);
        previewScroll.setBackgroundColor(palette.list);
        previewScroll.addView(preview, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        previewParams.topMargin = dp(8);
        rootBox.addView(previewScroll, previewParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        Button cancelButton = new Button(this);
        cancelButton.setText("取消");
        styleButton(cancelButton, false);
        Button exportButton = new Button(this);
        exportButton.setText("选择位置并导出");
        styleButton(exportButton, true);
        actions.addView(cancelButton);
        actions.addView(exportButton);
        rootBox.addView(actions);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("导出 LRC 歌词")
                .setView(rootBox)
                .create();
        Runnable refresh = () -> {
            mergeButton.setText(merge[0]
                    ? "合并/分离相同时间戳：合并"
                    : "合并/分离相同时间戳：分离");
            preview.setText(LrcExportFormatter.buildText(document, merge[0]));
        };
        mergeButton.setOnClickListener(v -> {
            merge[0] = !merge[0];
            refresh.run();
        });
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        exportButton.setOnClickListener(v -> {
            pendingExportText = LrcExportFormatter.buildText(document, merge[0]);
            dialog.dismiss();
            createLyricsDocument(true);
        });
        pathButton.setOnClickListener(v -> exportButton.performClick());
        refresh.run();
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.82));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        persistPermission(uri, data.getFlags());
        if (requestCode == REQUEST_OPEN_LRC) {
            loadLyrics(uri);
        } else if (requestCode == REQUEST_OPEN_AUDIO) {
            loadAudio(uri);
        } else if (requestCode == REQUEST_SAVE_LRC || requestCode == REQUEST_EXPORT_LRC) {
            writeLyrics(uri, requestCode == REQUEST_EXPORT_LRC,
                    displayName(uri));
        }
    }

    private void loadLyrics(Uri uri) {
        ioExecutor.execute(() -> {
            try {
                LrcIo.ReadResult result;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IllegalStateException("无法读取文件");
                    result = LrcIo.read(input);
                }
                String name = displayName(uri);
                runOnUiThread(() -> {
                    document.load(result.text);
                    lrcUri = uri;
                    lrcName = name;
                    encoding = result.encoding;
                    bom = result.hasBom;
                    selection.clear();
                    playing.clear();
                    lastAutoSyncGroup = Collections.emptySet();
                    adapter.notifyDataSetChanged();
                    lyricList.setSelection(0);
                    updateAllUi();
                    toast("已导入 " + name);
                });
            } catch (Throwable error) {
                runOnUiThread(() -> toast("导入歌词失败：" + messageOf(error)));
            }
        });
    }

    private void loadAudio(Uri uri) {
        audioName = displayName(uri);
        waveformRequest++;
        waveformView.setWaveform(null, null, 0);
        waveformView.setPlaceholder("正在准备音频…");
        audio.open(this, uri, new AudioSession.Listener() {
            @Override
            public void onReady(long readyDurationMs) {
                audioUri = uri;
                durationMs = readyDurationMs;
                currentPositionMs = 0;
                songTitle.setText(audioName);
                updateAllUi();
                loadAlbumArt(uri);
                loadWaveform(uri);
            }

            @Override
            public void onCompletion() {
                currentPositionMs = durationMs;
                updateTimeUi(currentPositionMs);
                updatePlayIcon();
                recordView.setSpinning(false);
            }

            @Override
            public void onError(String message) {
                toast(message);
                waveformView.setPlaceholder(getString(R.string.audio_waveform_unavailable));
            }
        });
    }

    private void loadAlbumArt(Uri uri) {
        ioExecutor.execute(() -> {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(this, uri);
                byte[] picture = retriever.getEmbeddedPicture();
                Bitmap bitmap = picture == null ? null
                        : BitmapFactory.decodeByteArray(picture, 0, picture.length);
                runOnUiThread(() -> recordView.setBitmap(bitmap));
            } catch (Throwable ignored) {
                runOnUiThread(() -> recordView.setBitmap(null));
            } finally {
                try {
                    retriever.release();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private void loadWaveform(Uri uri) {
        final int request = ++waveformRequest;
        ioExecutor.execute(() -> WaveformExtractor.extract(
                this, uri, 0.04f, new WaveformExtractor.Callback() {
                    @Override
                    public void onProgress(int percent) {
                        runOnUiThread(() -> {
                            if (request != waveformRequest) return;
                            waveformView.setPlaceholder(
                                    "正在分析波形… " + percent + "%");
                        });
                    }

                    @Override
                    public void onWaveform(float[] minimum, float[] maximum, long durationMs) {
                        runOnUiThread(() -> {
                            if (request != waveformRequest) return;
                            waveformView.setWaveform(minimum, maximum, durationMs);
                            if (MainActivity.this.durationMs <= 0) {
                                MainActivity.this.durationMs = durationMs;
                            }
                            updateTimeUi(currentPositionMs);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            if (request != waveformRequest) return;
                            waveformView.setPlaceholder(
                                    getString(R.string.audio_waveform_unavailable));
                        });
                    }
                }));
    }

    private void togglePlayback() {
        if (audioUri == null) {
            toast("请先导入音频");
            openAudioDocument();
            return;
        }
        audio.toggle();
        updatePlayIcon();
        recordView.setSpinning(audio.isPlaying());
    }

    private void seekRelative(long deltaMs) {
        if (audioUri == null) return;
        long target = Math.max(0, Math.min(durationMs,
                audio.getPositionMs() + deltaMs));
        audio.seekTo(target);
        currentPositionMs = target;
        updateTimeUi(target);
    }

    private void showAdvanceDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(lyricAdvanceMs));
        input.setHint("0");
        int padding = dp(20);
        input.setPadding(padding, dp(8), padding, dp(8));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("自定义提前量")
                .setMessage("播放行歌词的显示提前量，不会修改 LRC 时间戳。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("应用", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    try {
                        int advance = Integer.parseInt(input.getText().toString().trim());
                        if (advance < 0) {
                            toast("提前量必须是大于等于 0 的整数");
                            return;
                        }
                        lyricAdvanceMs = advance;
                        preferences.edit().putInt(PREF_ADVANCE_MS, advance).apply();
                        updateTimeUi(currentPositionMs);
                        dialog.dismiss();
                    } catch (NumberFormatException error) {
                        toast("请输入整数毫秒值");
                    }
                }));
        dialog.show();
    }

    private void playFromSelection() {
        int index = firstSelected();
        if (index < 0) {
            toast("请先选择一行歌词");
            return;
        }
        playFromLine(index);
    }

    private void editSelectedLine() {
        int index = firstSelected();
        if (index < 0) {
            toast("请先选择一行歌词");
            return;
        }
        editLine(index);
    }

    private void playFromLine(int index) {
        if (index < 0 || index >= document.getLines().size()) return;
        Long time = document.getLines().get(index).timeMs;
        if (time == null) {
            toast("这一行还没有时间戳");
            return;
        }
        if (audioUri == null) {
            toast("请先导入音频");
            return;
        }
        audio.seekTo(time);
        audio.play();
        currentPositionMs = time;
        updateTimeUi(time);
        updatePlayIcon();
        recordView.setSpinning(true);
    }

    private void stampSelection() {
        if (selection.isEmpty()) {
            toast("请先选择至少一行歌词");
            return;
        }
        long position = audioUri == null ? currentPositionMs : audio.getPositionMs();
        List<Integer> indices = sortedSelection();
        document.stampMany(indices, position);
        if (indices.size() == 1) {
            int next = indices.get(0) + 1;
            if (next < document.getLines().size()) {
                selection.clear();
                selection.add(next);
            }
        }
        adapter.notifyDataSetChanged();
        updateAllUi();
        toast("已写入 " + LrcIo.formatTime(position));
    }

    private void insertNewLine() {
        int selected = firstSelected();
        int insertPosition = selected >= 0 ? selected + 1 : document.getLines().size();
        int firstVisible = lyricList.getFirstVisiblePosition();
        View firstChild = lyricList.getChildAt(0);
        int top = firstChild == null ? 0 : firstChild.getTop();
        document.addLine(insertPosition, new LyricLine(0L, ""));
        selection.clear();
        selection.add(insertPosition);
        adapter.notifyDataSetChanged();
        lyricList.setSelectionFromTop(firstVisible, top);
        updateAllUi();
    }

    private void showInsertRowsDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText("2");
        input.setHint("1 到 1000");
        int padding = dp(20);
        input.setPadding(padding, dp(8), padding, dp(8));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("自定义插入行数")
                .setMessage("在当前位置插入指定数量的空歌词行。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("插入", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    try {
                        int count = Integer.parseInt(input.getText().toString().trim());
                        if (count <= 0 || count > 1000) {
                            toast("插入行数必须在 1 到 1000 之间");
                            return;
                        }
                        int selected = firstSelected();
                        int position = selected >= 0 ? selected + 1
                                : document.getLines().size();
                        List<LyricLine> newLines = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            newLines.add(new LyricLine(0L, ""));
                        }
                        insertLinesAt(position, newLines);
                        dialog.dismiss();
                    } catch (NumberFormatException error) {
                        toast("请输入整数行数");
                    }
                }));
        dialog.show();
    }

    private void pasteMultilineLyrics() {
        ClipboardManager manager =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = manager == null ? null : manager.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            toast("剪贴板中没有可粘贴的歌词");
            return;
        }
        CharSequence raw = clip.getItemAt(0).coerceToText(this);
        String text = raw == null ? "" : raw.toString();
        if (text.trim().isEmpty()) {
            toast("剪贴板中没有可粘贴的歌词");
            return;
        }
        int selected = firstSelected();
        int position = selected >= 0 ? selected : document.getLines().size();
        Long previousTime = previousTimestamp(position - 1);
        List<LyricLine> parsed = LrcPasteParser.parse(text, previousTime);
        if (parsed.isEmpty()) {
            toast("剪贴板中没有有效歌词");
            return;
        }
        insertLinesAt(position, parsed);
    }

    private void insertLinesAt(int position, List<LyricLine> newLines) {
        int firstVisible = lyricList.getFirstVisiblePosition();
        View firstChild = lyricList.getChildAt(0);
        int top = firstChild == null ? 0 : firstChild.getTop();
        document.addLines(position, newLines);
        selection.clear();
        selection.add(Math.max(0, Math.min(position, document.getLines().size() - 1)));
        playing.clear();
        adapter.notifyDataSetChanged();
        lyricList.setSelectionFromTop(firstVisible, top);
        updateAllUi();
    }

    private Long previousTimestamp(int index) {
        for (int i = Math.min(index, document.getLines().size() - 1); i >= 0; i--) {
            Long time = document.getLines().get(i).timeMs;
            if (time != null) return time;
        }
        return null;
    }

    private void deleteSelectedLines() {
        if (selection.isEmpty()) {
            toast("请先选择至少一行歌词");
            return;
        }
        int firstSelected = firstSelected();
        int firstVisible = lyricList.getFirstVisiblePosition();
        View firstChild = lyricList.getChildAt(0);
        int top = firstChild == null ? 0 : firstChild.getTop();
        document.removeMany(sortedSelection());
        selection.clear();
        if (!document.getLines().isEmpty() && firstSelected >= 0) {
            selection.add(Math.min(firstSelected, document.getLines().size() - 1));
        }
        playing.clear();
        adapter.notifyDataSetChanged();
        lyricList.setSelectionFromTop(firstVisible, top);
        updateAllUi();
    }

    private void undo() {
        if (!document.canUndo()) return;
        document.undo();
        selection.clear();
        playing.clear();
        adapter.notifyDataSetChanged();
        updateAllUi();
    }

    private void redo() {
        if (!document.canRedo()) return;
        document.redo();
        selection.clear();
        playing.clear();
        adapter.notifyDataSetChanged();
        updateAllUi();
    }

    private void handleRowTap(int position) {
        if (position < 0 || position >= document.getLines().size()) return;
        if (document.getLines().get(position).isBlankSeparator()) return;

        if (autoExpand > 0) {
            List<Integer> range = document.collectExpandedRange(position, autoExpand);
            if (range.isEmpty()) return;
            if (selection.equals(new LinkedHashSet<>(range))) {
                editLine(position);
            } else {
                setSelection(range);
            }
            return;
        }

        if (autoGroup) {
            List<Integer> group = document.collectSameTimestampGroup(position);
            if (group.isEmpty()) return;
            if (selection.equals(new LinkedHashSet<>(group))) {
                editLine(position);
            } else {
                setSelection(group);
            }
            return;
        }

        if (selection.size() == 1 && selection.contains(position)) {
            editLine(position);
        } else {
            setSelection(Collections.singletonList(position));
        }
    }

    private void setSelection(List<Integer> indices) {
        selection.clear();
        selection.addAll(indices);
        applySelection();
    }

    private void applySelection() {
        adapter.setSelected(selection);
        updatePlayingGroup();
        updateAllUi();
    }

    private void editLine(int position) {
        LyricLine line = document.getLines().get(position);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        form.setPadding(padding, dp(8), padding, 0);

        TextView timeLabel = new TextView(this);
        timeLabel.setText("时间戳（mm:ss.xxx，留空表示无时间）");
        timeLabel.setTextColor(palette.muted);
        timeLabel.setTextSize(12);
        form.addView(timeLabel);

        EditText timeInput = new EditText(this);
        timeInput.setSingleLine(true);
        timeInput.setInputType(InputType.TYPE_CLASS_TEXT);
        timeInput.setText(line.timeMs == null ? "" : LrcIo.formatTime(line.timeMs));
        timeInput.setTextColor(palette.text);
        timeInput.setHint("00:12.500");
        form.addView(timeInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView textLabel = new TextView(this);
        textLabel.setText("歌词内容");
        textLabel.setTextColor(palette.muted);
        textLabel.setTextSize(12);
        textLabel.setPadding(0, dp(10), 0, 0);
        form.addView(textLabel);

        EditText textInput = new EditText(this);
        textInput.setSingleLine(false);
        textInput.setMinLines(2);
        textInput.setGravity(Gravity.TOP | Gravity.START);
        textInput.setText(line.text);
        textInput.setTextColor(palette.text);
        form.addView(textInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("编辑歌词")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    Long time = parseTimeInput(timeInput.getText().toString());
                    if (!timeInput.getText().toString().trim().isEmpty() && time == null) {
                        toast("时间格式示例：00:12.500");
                        return;
                    }
                    document.editLine(position, time, textInput.getText().toString());
                    adapter.notifyDataSetChanged();
                    updateAllUi();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private Long parseTimeInput(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) return null;
        if (!value.startsWith("[")) value = "[" + value + "]";
        Matcher matcher = LrcIo.LRC_TAG.matcher(value);
        if (!matcher.matches()) return null;
        try {
            return LrcIo.parseTimeMs(matcher);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void showOffsetDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setHint("例如 250 或 -250");
        input.setText(String.valueOf(document.getOffset()));
        int padding = dp(20);
        input.setPadding(padding, dp(8), padding, dp(8));
        new AlertDialog.Builder(this)
                .setTitle("修改 offset")
                .setMessage("输入目标偏移量（毫秒），不是每次改动量。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("应用", (dialog, which) -> {
                    try {
                        long target = Long.parseLong(input.getText().toString().trim());
                        document.applyOffset(target);
                        adapter.notifyDataSetChanged();
                        updateAllUi();
                    } catch (NumberFormatException error) {
                        toast("请输入整数毫秒值");
                    }
                })
                .show();
    }

    private void toggleAutoGroup() {
        if (!autoGroup && autoExpand > 0) {
            showConflictDialog(autoExpand, () -> {
                autoExpand = 0;
                autoGroup = true;
                saveAutoSettings();
            });
            return;
        }
        autoGroup = !autoGroup;
        saveAutoSettings();
    }

    private void showAutoExpandDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(autoExpand));
        input.setHint("0 到 10");
        int padding = dp(20);
        input.setPadding(padding, dp(8), padding, dp(8));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("自动扩选行数")
                .setMessage("输入 0 表示关闭；开启后会向下扩选指定数量的可见歌词行。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("应用", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    int rows;
                    try {
                        rows = Integer.parseInt(input.getText().toString().trim());
                    } catch (NumberFormatException error) {
                        toast("请输入 0 到 10 的整数");
                        return;
                    }
                    if (rows < 0 || rows > 10) {
                        toast("自动扩选范围为 0 到 10");
                        return;
                    }
                    if (rows > 0 && autoGroup) {
                        showConflictDialog(rows, () -> {
                            autoGroup = false;
                            autoExpand = rows;
                            saveAutoSettings();
                            dialog.dismiss();
                        });
                        return;
                    }
                    autoExpand = rows;
                    saveAutoSettings();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void showConflictDialog(int rows, Runnable onConfirm) {
        new AlertDialog.Builder(this)
                .setTitle("选项冲突")
                .setMessage("自动选择同组 与 自动扩选" + rows + "行 无法同时开启")
                .setNegativeButton("取消", null)
                .setPositiveButton("继续", (dialog, which) -> onConfirm.run())
                .show();
    }

    private void saveAutoSettings() {
        preferences.edit()
                .putBoolean(PREF_AUTO_GROUP, autoGroup)
                .putInt(PREF_AUTO_EXPAND, autoExpand)
                .apply();
        toast(autoGroup ? "自动选择同组已开启"
                : autoExpand > 0 ? "自动扩选 " + autoExpand + " 行已开启"
                : "自动选择与扩选均已关闭");
    }

    private void switchTheme(boolean dark) {
        preferences.edit()
                .putBoolean(PREF_DARK, dark)
                .putInt(PREF_PREVIEW_COLOR, dark ? Color.WHITE : Color.BLACK)
                .apply();
        recreate();
    }

    private void updateAllUi() {
        songTitle.setText(audioName != null ? audioName
                : lrcName != null ? lrcName : "未命名歌词");
        emptyState.setVisibility(document.getLines().isEmpty() ? View.VISIBLE : View.GONE);
        lyricList.setVisibility(document.getLines().isEmpty() ? View.INVISIBLE : View.VISIBLE);
        adapter.setSelected(selection);
        adapter.setPlaying(playing);
        undoButton.setEnabled(document.canUndo());
        redoButton.setEnabled(document.canRedo());
        updateStatusText();
        updateTimeUi(currentPositionMs);
        updatePlayIcon();
    }

    private void updateStatusText() {
        String state = document.isDirty() ? "未保存" : "已保存";
        String audioState = audioName == null ? "无音频" : audioName;
        statusText.setText(encoding + " · " + state + " · " + audioState
                + (selection.isEmpty() ? "" : " · 已选 " + selection.size() + " 行"));
    }

    private void updateTimeUi(long positionMs) {
        currentPositionMs = Math.max(0, positionMs);
        timeLabel.setText(LrcIo.formatDuration(currentPositionMs)
                + " / " + LrcIo.formatDuration(durationMs));
        waveformView.setPositionMs(currentPositionMs);
        if (!userSeeking && durationMs > 0) {
            int progress = (int) Math.min(1000,
                    currentPositionMs * 1000L / Math.max(1, durationMs));
            seekBar.setProgress(progress);
        }
        updatePreview(displayPositionMs());
        updatePlayingGroup();
    }

    private long displayPositionMs() {
        return currentPositionMs + lyricAdvanceMs;
    }

    private void updatePreview(long positionMs) {
        List<Integer> timed = new ArrayList<>();
        for (int i = 0; i < document.getLines().size(); i++) {
            LyricLine line = document.getLines().get(i);
            if (line.timeMs != null && !line.text.trim().isEmpty()) timed.add(i);
        }
        if (timed.isEmpty()) {
            previewPrev.setText("");
            previewCurrent.setText(getString(R.string.lyrics_preview_placeholder));
            previewNext.setText("");
            return;
        }

        Long currentTime = null;
        for (int index : timed) {
            long time = document.getLines().get(index).timeMs;
            if (time <= positionMs) {
                if (currentTime == null || time > currentTime) currentTime = time;
            }
        }
        if (currentTime == null) {
            previewPrev.setText("");
            previewCurrent.setText(timed.isEmpty() ? getString(R.string.lyrics_preview_placeholder)
                    : document.getLines().get(timed.get(0)).text);
            previewNext.setText("");
            return;
        }

        int firstCurrent = -1;
        Long previousTime = null;
        Long nextTime = null;
        for (int index : timed) {
            long time = document.getLines().get(index).timeMs;
            if (time == currentTime && firstCurrent < 0) firstCurrent = index;
            if (time < currentTime) previousTime = time;
            if (time > currentTime && nextTime == null) nextTime = time;
        }
        previewPrev.setText(textAtTime(previousTime));
        previewCurrent.setText(textAtTime(currentTime));
        previewNext.setText(textAtTime(nextTime));
        previewCurrent.setTextColor(previewColor);
        previewCurrent.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    }

    private String textAtTime(Long time) {
        if (time == null) return "";
        List<String> values = new ArrayList<>();
        for (LyricLine line : document.getLines()) {
            if (time.equals(line.timeMs) && !line.text.trim().isEmpty()) {
                values.add(line.text.trim());
            }
        }
        return TextUtils.join(" / ", values);
    }

    private void updatePlayingGroup() {
        Set<Integer> next = new LinkedHashSet<>(
                document.findPlayingIndices(displayPositionMs()));
        boolean changed = !next.equals(playing);
        if (changed) {
            playing.clear();
            playing.addAll(next);
            adapter.setPlaying(playing);
        }
        if (autoSyncSelection && changed) {
            applyAutoSelection(next);
        }
        if (autoSyncSelection && audio.isPlaying() && changed
                && !playing.isEmpty() && !playing.equals(lastAutoSyncGroup)) {
            lastAutoSyncGroup = new HashSet<>(playing);
            int first = Collections.min(playing);
            lyricList.post(() -> lyricList.smoothScrollToPosition(Math.max(0, first - 2)));
        }
    }

    private void applyAutoSelection(Set<Integer> playingGroup) {
        if (applyingAutoSelection || playingGroup.isEmpty()) return;
        if (new LinkedHashSet<>(selection).equals(playingGroup)) return;
        applyingAutoSelection = true;
        try {
            selection.clear();
            selection.addAll(playingGroup);
            adapter.setSelected(selection);
            updateStatusText();
        } finally {
            applyingAutoSelection = false;
        }
    }

    private void updatePlayIcon() {
        boolean isPlaying = audio.isPlaying();
        playButton.setImageResource(isPlaying
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play);
        playButton.setContentDescription(getString(isPlaying ? R.string.pause : R.string.play));
        tintImageButton(playButton);
        recordView.setSpinning(isPlaying);
    }

    private List<Integer> sortedSelection() {
        List<Integer> result = new ArrayList<>(selection);
        Collections.sort(result);
        return result;
    }

    private int firstSelected() {
        return selection.isEmpty() ? -1 : Collections.min(selection);
    }

    private void confirmNewDocument() {
        if (!document.isDirty()) {
            newDocument();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("新建文档")
                .setMessage("当前歌词尚未保存，仍要新建吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("新建", (dialog, which) -> newDocument())
                .show();
    }

    private void newDocument() {
        audio.pause();
        document.clear();
        lrcUri = null;
        lrcName = null;
        selection.clear();
        playing.clear();
        adapter.notifyDataSetChanged();
        updateAllUi();
    }

    private void restoreSession(Bundle state) {
        if (state == null || !state.containsKey(STATE_DOCUMENT)) return;
        document.load(state.getString(STATE_DOCUMENT, ""));
        document.restoreMetadata(
                state.getLong(STATE_OFFSET, 0),
                state.getBoolean(STATE_DIRTY, false),
                state.getBoolean(STATE_CHUNKED, false));
        lrcName = state.getString(STATE_LRC_NAME);
        audioName = state.getString(STATE_AUDIO_NAME);
        encoding = state.getString(STATE_ENCODING, "UTF-8");
        bom = state.getBoolean(STATE_BOM, false);
        String savedLrcUri = state.getString(STATE_LRC_URI);
        if (savedLrcUri != null) {
            try {
                lrcUri = Uri.parse(savedLrcUri);
            } catch (Throwable ignored) {
            }
        }
        adapter.notifyDataSetChanged();
        updateAllUi();
        String savedAudioUri = state.getString(STATE_AUDIO_URI);
        if (savedAudioUri != null) {
            try {
                loadAudio(Uri.parse(savedAudioUri));
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_DOCUMENT, document.serialize(false));
        outState.putString(STATE_LRC_URI, lrcUri == null ? null : lrcUri.toString());
        outState.putString(STATE_LRC_NAME, lrcName);
        outState.putString(STATE_AUDIO_URI, audioUri == null ? null : audioUri.toString());
        outState.putString(STATE_AUDIO_NAME, audioName);
        outState.putString(STATE_ENCODING, encoding);
        outState.putBoolean(STATE_BOM, bom);
        outState.putLong(STATE_OFFSET, document.getOffset());
        outState.putBoolean(STATE_DIRTY, document.isDirty());
        outState.putBoolean(STATE_CHUNKED, document.isChunked());
        super.onSaveInstanceState(outState);
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("关于")
                .setMessage("LRC 歌词编辑器 Android 版 1.0.0\n\n"
                        + "原生 Android 界面\n"
                        + "支持 LRC 编辑、音频播放、打点和波形定位\n\n"
                        + "桌面版 v5.3")
                .setPositiveButton("关闭", null)
                .show();
    }

    private void persistPermission(Uri uri, int flags) {
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    flags & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
        } catch (SecurityException ignored) {
        }
    }

    private String displayName(Uri uri) {
        if (uri == null) return "未命名";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (Throwable ignored) {
        }
        String fallback = uri.getLastPathSegment();
        return fallback == null ? "未命名" : fallback;
    }

    private String baseName(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String messageOf(Throwable error) {
        return error.getMessage() == null
                ? error.getClass().getSimpleName() : error.getMessage();
    }

    @Override
    public void onBackPressed() {
        if (document.isDirty()) {
            new AlertDialog.Builder(this)
                    .setTitle("退出")
                    .setMessage("歌词尚未保存，仍要退出吗？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("退出", (dialog, which) -> finish())
                    .show();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        audio.pause();
        updatePlayIcon();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        ioExecutor.shutdownNow();
        audio.release();
        super.onDestroy();
    }
}
