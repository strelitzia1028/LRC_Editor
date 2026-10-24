package com.lrceditor.mobile.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public final class RecordView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap bitmap;
    private boolean spinning;
    private boolean dark = true;
    private float angle;
    private long lastFrameNanos;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            long now = System.nanoTime();
            if (lastFrameNanos == 0) lastFrameNanos = now;
            float delta = (now - lastFrameNanos) / 1_000_000_000f;
            lastFrameNanos = now;
            if (spinning) {
                angle = (angle + delta * 45f) % 360f;
                invalidate();
                postOnAnimation(this);
            }
        }
    };

    public RecordView(Context context) {
        super(context);
    }

    public RecordView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RecordView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        invalidate();
    }

    public void setDark(boolean dark) {
        this.dark = dark;
        invalidate();
    }

    public void setSpinning(boolean spinning) {
        if (this.spinning == spinning) return;
        this.spinning = spinning;
        lastFrameNanos = 0;
        if (spinning) {
            postOnAnimation(frame);
        } else {
            removeCallbacks(frame);
            angle = 0;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) / 2f - 2f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(dark ? Color.rgb(23, 31, 47) : Color.rgb(226, 232, 240));
        canvas.drawCircle(cx, cy, radius, paint);

        canvas.save();
        canvas.rotate(angle, cx, cy);
        float artRadius = radius * 0.68f;
        canvas.save();
        canvas.clipPath(circlePath(cx, cy, artRadius));
        if (bitmap == null || bitmap.isRecycled()) {
            paint.setColor(dark ? Color.rgb(30, 41, 59) : Color.rgb(203, 213, 225));
            canvas.drawCircle(cx, cy, artRadius, paint);
            paint.setColor(dark ? Color.rgb(148, 163, 184) : Color.rgb(71, 85, 105));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            for (int i = 1; i <= 4; i++) {
                canvas.drawCircle(cx, cy, artRadius * (i / 5f), paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(dark ? Color.rgb(37, 99, 235) : Color.rgb(37, 99, 235));
            canvas.drawCircle(cx, cy, artRadius * 0.28f, paint);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(artRadius * 0.34f);
            paint.setFakeBoldText(true);
            canvas.drawText("LRC", cx, cy + artRadius * 0.12f, paint);
            paint.setFakeBoldText(false);
        } else {
            RectF destination = centerCrop(bitmap, cx - artRadius, cy - artRadius,
                    artRadius * 2, artRadius * 2);
            canvas.drawBitmap(bitmap, null, destination, paint);
        }
        canvas.restore();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(dark ? Color.argb(150, 255, 255, 255) : Color.argb(90, 15, 23, 42));
        canvas.drawCircle(cx, cy, radius - dp(2), paint);
        paint.setStrokeWidth(dp(1));
        paint.setColor(dark ? Color.argb(90, 255, 255, 255) : Color.argb(70, 15, 23, 42));
        canvas.drawCircle(cx, cy, radius * 0.82f, paint);
        canvas.restore();
    }

    private android.graphics.Path circlePath(float x, float y, float radius) {
        android.graphics.Path path = new android.graphics.Path();
        path.addCircle(x, y, radius, android.graphics.Path.Direction.CW);
        return path;
    }

    private RectF centerCrop(Bitmap source, float left, float top, float width, float height) {
        float scale = Math.max(width / source.getWidth(), height / source.getHeight());
        float drawWidth = source.getWidth() * scale;
        float drawHeight = source.getHeight() * scale;
        float destinationLeft = left + (width - drawWidth) / 2f;
        float destinationTop = top + (height - drawHeight) / 2f;
        return new RectF(destinationLeft, destinationTop,
                destinationLeft + drawWidth, destinationTop + drawHeight);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(frame);
        super.onDetachedFromWindow();
    }
}
