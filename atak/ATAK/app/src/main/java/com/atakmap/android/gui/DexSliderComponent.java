
package com.atakmap.android.gui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.atakmap.app.R;

/**
 * DEX on-screen controls for map rotation and tilt
 */
public class DexSliderComponent extends LinearLayout
        implements SeekBar.OnSeekBarChangeListener {
    private SeekBar directionSeekbar;
    private SeekBar tiltSeekbar;
    private ImageView tiltIcon, directionIcon;

    private float tiltValue = 0.0f;
    private float headingValue = 0.0f;

    private DexSliderListener listener;
    protected View thumbView;
    protected int maxTilt = 0;

    public interface DexSliderListener {
        void onDexTiltChanged(float tilt);

        void onDexRotationChanged(float rotation);
    }

    public DexSliderComponent(Context context) {
        super(context);
        init();
    }

    public DexSliderComponent(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();

    }

    /**
     * Show or hide Tilt slider
     *
     * @param visible Is slider visible
     */
    public void setTiltSliderVisible(boolean visible) {
        if (visible) {
            tiltSeekbar.setVisibility(VISIBLE);
            tiltIcon.setVisibility(VISIBLE);
        } else {
            tiltSeekbar.setVisibility(GONE);
            tiltIcon.setVisibility(GONE);
        }
        tiltSeekbar.setProgress((int) tiltValue);
    }

    /**
     * Show or hide Direction slider
     *
     * @param visible Is slider visible
     */
    public void setDirectionSliderVisible(boolean visible) {
        if (visible) {
            directionIcon.setVisibility(VISIBLE);
            directionSeekbar.setVisibility(VISIBLE);
        } else {
            directionSeekbar.setVisibility(GONE);
            directionIcon.setVisibility(GONE);
        }
    }

    /**
     * Set the max tilt (progress) of the slider
     *
     * @param maxTilt Max tilt from the map view
     */
    public void setMaxTilt(double maxTilt) {
        int t = (int) maxTilt;
        if (this.maxTilt == t)
            return;
        this.maxTilt = t;
        tiltSeekbar.setMax(t);
        if (tiltValue > t)
            tiltValue = t;
        setTilt(tiltValue);
    }

    /**
     * Set the tilt (progress) of the slider to the tilt value
     *
     * @param tilt Tilt value from map view
     */
    public void setTilt(double tilt) {
        tiltValue = (float) tilt;
        tiltSeekbar.setProgress((int) tilt);
        tiltSeekbar.refreshDrawableState();
    }

    /**
     * Set the direction (progress) of the slider to the compass value
     *
     * @param dir Rotation from map view
     */
    public void setDirection(double dir) {
        headingValue = (float) dir;
        directionSeekbar.setProgress((int) dir);
        directionSeekbar.refreshDrawableState();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed) {
            directionSeekbar = findViewById(R.id.direction_seekbar);
            directionSeekbar.setOnSeekBarChangeListener(this);
            directionIcon = findViewById(R.id.direction_text);
            tiltIcon = findViewById(R.id.tilt_text);

            tiltSeekbar = findViewById(R.id.tilt_seekbar);
            tiltSeekbar.setOnSeekBarChangeListener(this);
            thumbView = LayoutInflater.from(getContext()).inflate(
                    R.layout.custom_seekbar_thumb, this,
                    false);
            tiltSeekbar.setMax(maxTilt);
            directionSeekbar.setThumb(getThumb(headingValue, false));
            tiltSeekbar.setThumb(getThumb(tiltValue, true));
            tiltSeekbar.setProgress((int) tiltValue);
            directionSeekbar.setProgress((int) headingValue);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        directionSeekbar = findViewById(R.id.direction_seekbar);
        tiltSeekbar = findViewById(R.id.tilt_seekbar);
        directionIcon = findViewById(R.id.direction_text);
        tiltIcon = findViewById(R.id.tilt_text);
    }

    private void init() {
    }

    public void setDexSliderListener(DexSliderListener listener) {
        this.listener = listener;
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress,
            boolean fromUser) {
        if (getVisibility() == GONE)
            return;
        if (seekBar == directionSeekbar) {
            if (listener != null) {
                listener.onDexRotationChanged((float) progress);
            }

            directionSeekbar.setThumb(getThumb(progress, false));
        } else if (seekBar == tiltSeekbar) {

            if (listener != null) {
                listener.onDexTiltChanged((float) progress);
            }
            tiltValue = (float) progress;
            tiltSeekbar.setThumb(getThumb(progress, true));
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    /**
     * Get custom thumb for slider
     *
     * @param progress Slider value
     * @param isTilt Is this the 3D tilt slider.  Used to disable slider when maxTilt is 0
     * @return Slider thumb drawable
     */
    public Drawable getThumb(double progress, boolean isTilt) {
        ((TextView) thumbView.findViewById(R.id.progress_text_view))
                .setText(getResources().getString(R.string.dex_degrees_dex,
                        (int) progress));
        thumbView.measure(MeasureSpec.UNSPECIFIED,
                MeasureSpec.UNSPECIFIED);
        Bitmap bitmap = Bitmap.createBitmap(thumbView.getMeasuredWidth(),
                thumbView.getMeasuredHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        canvas.rotate(90f);
        canvas.translate(0, -thumbView.getMeasuredWidth());
        thumbView.layout(0, 0, thumbView.getMeasuredWidth(),
                thumbView.getMeasuredHeight());
        thumbView.draw(canvas);

        Drawable d = new BitmapDrawable(getResources(), bitmap);
        if (isTilt && maxTilt == 0) {
            d.setAlpha(64);
        } else {
            d.setAlpha(255);
        }
        return d;
    }

}
