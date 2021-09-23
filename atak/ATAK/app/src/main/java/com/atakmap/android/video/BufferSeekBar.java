
package com.atakmap.android.video;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class BufferSeekBar extends View implements View.OnTouchListener {
    private int bufMin;
    private int bufMax;
    private int rangeMin;
    private int rangeMax;
    private int maxBufTime;
    private float curTime;
    private float curFramePct;

    private final Paint paint;

    public BufferSeekBar(Context context) {
        this(context, null);
    }

    public BufferSeekBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BufferSeekBar(Context context, AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);

    }

    public BufferSeekBar(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        bufMin = bufMax = 0;
        rangeMin = rangeMax = 0;
        maxBufTime = 0;
        curTime = 0;
        curFramePct = 0;
        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        setOnTouchListener(this);
    }

    private BufferSeekBarChangeListener seekListener;

    public interface BufferSeekBarChangeListener {
        void onProgressChanged(BufferSeekBar bar, int progress);

        void onStartTrackingTouch(BufferSeekBar bar);

        void onStopTrackingTouch(BufferSeekBar bar);
    }

    public void setSeekBarChangeListener(BufferSeekBarChangeListener listener) {
        seekListener = listener;
    }

    public boolean onTouch(View v, MotionEvent ev) {
        if (seekListener == null)
            return true;

        boolean fireStop = false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                seekListener.onStartTrackingTouch(this);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                fireStop = true;
                break;
        }

        float x = ev.getX() / getWidth();
        if (x >= 0 && x < 1.0) {
            int t = (int) (x * (rangeMax - rangeMin)) + rangeMin;
            if (maxBufTime > 0) {
                if (t > bufMax)
                    t = bufMax;
                if (t < bufMin)
                    t = bufMin;
            }
            seekListener.onProgressChanged(this, t);
        }

        if (fireStop)
            seekListener.onStopTrackingTouch(this);
        return true;
    }

    public void resetBufTime(int minMs, int maxMs, int timeMs) {
        maxBufTime = timeMs;
        curTime = timeMs;
        recalculate(minMs, maxMs);
    }

    public void setRange(int minMillis, int maxMillis) {
        recalculate(minMillis, maxMillis);
    }

    private void recalculate(int min, int max) {
        if (maxBufTime > 0) {
            rangeMax = max;
            rangeMin = max - maxBufTime;
            bufMin = min;
            bufMax = max;
        } else {
            rangeMin = min;
            rangeMax = max;
            bufMin = bufMax = -1;
        }
        curFramePct = Math
                .max((curTime - rangeMin) / (rangeMax - rangeMin), 0);

        invalidate();
    }

    public void setCurrent(int currentTime) {
        curTime = currentTime;
        curFramePct = Math
                .max((curTime - rangeMin) / (rangeMax - rangeMin), 0);
        invalidate();
    }

    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        Paint p = paint;
        p.setARGB(255, 127, 127, 127);
        canvas.drawRoundRect(0, 0, w, h, h / 5.0f, h / 5.0f, p);

        if (bufMax >= 0 && bufMin >= 0) {
            int clampedMin = Math.max(rangeMin, bufMin);
            float b0 = (float) (clampedMin - rangeMin) / (rangeMax - rangeMin);
            float bend = (float) (bufMax - rangeMin) / (rangeMax - rangeMin);
            b0 *= w;
            bend *= w;

            p.setARGB(255, 125, 161, 190);
            canvas.drawRoundRect(b0, 0, bend, h, h / 5.0f, h / 5.0f, p);

        }

        float tx = curFramePct * w;
        p.setARGB(255, 255, 255, 255);
        final int knobWidth = 16;
        canvas.drawRoundRect(tx - knobWidth / 2, 0, tx + knobWidth / 2, h,
                knobWidth, knobWidth, p);
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w, h;
        w = MeasureSpec.getSize(widthMeasureSpec);
        h = MeasureSpec.getSize(heightMeasureSpec);
        switch (MeasureSpec.getMode(widthMeasureSpec)) {
            case MeasureSpec.AT_MOST:
                // Use all available space
                break;
            case MeasureSpec.EXACTLY:
                // just use the specified size
                break;
            case MeasureSpec.UNSPECIFIED:
            default:
                // Use minimum
                w = getSuggestedMinimumWidth();
                break;
        }
        switch (MeasureSpec.getMode(heightMeasureSpec)) {
            case MeasureSpec.AT_MOST:
                // Use minimum height
                h = getSuggestedMinimumHeight();
                break;
            case MeasureSpec.EXACTLY:
                // just use the specified size
                break;
            case MeasureSpec.UNSPECIFIED:
            default:
                // Use minimum
                h = getSuggestedMinimumWidth();
                break;
        }
        setMeasuredDimension(w, h);
    }

}
