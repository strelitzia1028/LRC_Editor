package com.lrceditor.mobile.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;

import java.io.IOException;

public final class AudioSession {
    public interface Listener {
        void onReady(long durationMs);
        void onCompletion();
        void onError(String message);
    }

    private MediaPlayer player;
    private Uri uri;
    private long durationMs;
    private Listener listener;

    public void open(Context context, Uri source, Listener callback) {
        release();
        listener = callback;
        uri = source;
        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        player.setOnPreparedListener(mediaPlayer -> {
            durationMs = Math.max(0, mediaPlayer.getDuration());
            callback.onReady(durationMs);
        });
        player.setOnCompletionListener(mediaPlayer -> callback.onCompletion());
        player.setOnErrorListener((mediaPlayer, what, extra) -> {
            callback.onError("音频播放失败 (" + what + ", " + extra + ")");
            return true;
        });
        try {
            player.setDataSource(context, source);
            player.prepareAsync();
        } catch (IOException | IllegalArgumentException | SecurityException error) {
            callback.onError("无法打开音频：" + error.getMessage());
        }
    }

    public Uri getUri() {
        return uri;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public long getPositionMs() {
        if (player == null) return 0;
        try {
            return player.getCurrentPosition();
        } catch (IllegalStateException ignored) {
            return 0;
        }
    }

    public boolean isPlaying() {
        if (player == null) return false;
        try {
            return player.isPlaying();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    public void play() {
        if (player == null) return;
        try {
            player.start();
        } catch (IllegalStateException ignored) {
        }
    }

    public void pause() {
        if (player == null) return;
        try {
            if (player.isPlaying()) player.pause();
        } catch (IllegalStateException ignored) {
        }
    }

    public void toggle() {
        if (isPlaying()) pause();
        else play();
    }

    public void seekTo(long positionMs) {
        if (player == null) return;
        try {
            player.seekTo((int) Math.max(0, Math.min(positionMs, durationMs)));
        } catch (IllegalStateException ignored) {
        }
    }

    public void release() {
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
            }
            player.release();
            player = null;
        }
        durationMs = 0;
    }
}
