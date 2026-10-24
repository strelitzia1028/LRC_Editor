package com.lrceditor.mobile.ui;

import android.content.Context;
import android.graphics.Color;

public final class UiPalette {
    public final boolean dark;
    public final int background;
    public final int surface;
    public final int list;
    public final int listAlt;
    public final int text;
    public final int muted;
    public final int accent;
    public final int accentStrong;
    public final int selected;
    public final int selectedPlaying;
    public final int playing;
    public final int border;
    public final int button;
    public final int buttonPressed;

    public UiPalette(Context context, boolean dark) {
        this.dark = dark;
        if (dark) {
            background = Color.rgb(11, 17, 32);
            surface = Color.rgb(17, 24, 39);
            list = Color.rgb(15, 23, 42);
            listAlt = Color.rgb(17, 24, 39);
            text = Color.rgb(226, 232, 240);
            muted = Color.rgb(148, 163, 184);
            accent = Color.rgb(96, 165, 250);
            accentStrong = Color.rgb(37, 99, 235);
            selected = Color.rgb(30, 64, 175);
            selectedPlaying = Color.rgb(29, 78, 216);
            playing = Color.rgb(92, 58, 10);
            border = Color.rgb(30, 41, 59);
            button = Color.rgb(30, 41, 59);
            buttonPressed = Color.rgb(51, 65, 85);
        } else {
            background = Color.rgb(243, 245, 247);
            surface = Color.WHITE;
            list = Color.rgb(248, 250, 252);
            listAlt = Color.WHITE;
            text = Color.rgb(31, 41, 55);
            muted = Color.rgb(100, 116, 139);
            accent = Color.rgb(37, 99, 235);
            accentStrong = Color.rgb(29, 78, 216);
            selected = Color.rgb(219, 234, 254);
            selectedPlaying = Color.rgb(191, 219, 254);
            playing = Color.rgb(254, 243, 199);
            border = Color.rgb(226, 232, 240);
            button = Color.rgb(241, 245, 249);
            buttonPressed = Color.rgb(226, 232, 240);
        }
    }
}
