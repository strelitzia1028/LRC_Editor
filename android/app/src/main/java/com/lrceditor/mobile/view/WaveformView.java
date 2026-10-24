package com.lrceditor.mobile.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public final class WaveformView extends View {
    public interface SeekListener {
        void onSeek(float fraction);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] minimum = new float[0];
    private float[] maximum = new float[0];
    private long durationMs;
    private long positionMs;
    private boolean dark = true;
    private String placeholder = "";
    private SeekListener seekListener;

    public WaveformView(Context context) {
        super(context);
    }

    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public WaveformView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setDark(boolean dark) {
        this.dark = dark;
        invalidate();
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
        invalidate();
    }

    public void setOnSeekListener(SeekListener listener) {
        seekListener = listener;
    }

    public void setWaveform(float[] minimum, float[] maximum, long durationMs) {
        this.minimum = minimum == null ? new float[0] : minimum;
        this.maximum = maximum == null ? new float[0] : maximum;
        this.durationMs = Math.max(0, durationMs);
        invalidate();
    }

    public void setPositionMs(long positionMs) {
        this.positionMs = Math.max(0, positionMs);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float mid = height / 2f;
        float left = dp(4);
        float right = width - dp(4);
        float usable = Math.max(1, right - left);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(dark ? Color.TRANSPARENT : Color.TRANSPARENT);
        canvas.drawRect(0, 0, width, height, paint);

        paint.setStrokeWidth(dp(1));
        paint.setColor(dark ? Color.rgb(51, 65, 85) : Color.rgb(226, 232, 240));
        canvas.drawLine(left, mid, right, mid, paint);

        if (maximum.length == 0 || durationMs <= 0) {
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(11));
            paint.setColor(dark ? Color.rgb(100, 116, 139) : Color.rgb(148, 163, 184));
            canvas.drawText(placeholder, width / 2f, mid + dp(4), paint);
            return;
        }

        paint.setStrokeWidth(Math.max(dp(1), usable / maximum.length * 0.72f));
        paint.setColor(dark ? Color.rgb(96, 165, 250) : Color.rgb(37, 99, 235));
        float amp = height * 0.42f;
        for (int i = 0; i < maximum.length; i++) {
            float x = left + i * usable / maximum.length;
            float top = mid - maximum[i] * amp;
            float bottom = mid - minimum[i] * amp;
            if (bottom - top < dp(1)) {
                top = mid - dp(0.5f);
                bottom = mid + dp(0.5f);
            }
            canvas.drawLine(x, top, x, bottom, paint);
        }

        float fraction = Math.max(0f, Math.min(1f, positionMs / (float) durationMs));
        float playhead = left + usable * fraction;
        paint.setStrokeWidth(dp(6));
        paint.setColor(Color.argb(48, 239, 68, 68));
        canvas.drawLine(playhead, 0, playhead, height, paint);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.rgb(239, 68, 68));
        canvas.drawLine(playhead, 0, playhead, height, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (durationMs <= 0) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                getParent().requestDisallowInterceptTouchEvent(true);
                seek(event.getX());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                if (event.getActionMasked() == MotionEvent.ACTION_UP) seek(event.getX());
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void seek(float x) {
        float left = dp(4);
        float right = getWidth() - dp(4);
        float fraction = (x - left) / Math.max(1, right - left);
        fraction = Math.max(0f, Math.min(1f, fraction));
        positionMs = (long) (durationMs * fraction);
        invalidate();
        if (seekListener != null) seekListener.onSeek(fraction);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
