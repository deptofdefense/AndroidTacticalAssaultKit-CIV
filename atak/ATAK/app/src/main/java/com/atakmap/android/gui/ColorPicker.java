
package com.atakmap.android.gui;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import com.atakmap.android.util.SimpleSeekBarChangeListener;
import com.atakmap.app.R;

import androidx.annotation.ColorInt;

import gov.tak.api.annotation.ModifierApi;

/**
 * A view containing 3 or 4 {@link SeekBar} allowing the user to specify a
 * custom color outside of the preset defaults
 */
public class ColorPicker extends LinearLayout {

    private SeekBar redSeek;
    private SeekBar greenSeek;
    private SeekBar blueSeek;
    private SeekBar alphaSeek;
    private ColorButton preview;
    private final boolean enableAlphaSlider;

    public ColorPicker(Context context) {
        super(context);
        this.enableAlphaSlider = false;
        _init(Color.BLACK);
    }

    public ColorPicker(Context context, int initialColor) {
        super(context);
        this.enableAlphaSlider = false;
        _init(initialColor);
    }

    public ColorPicker(Context context, int initialColor,
            boolean enableAlphaSlider) {
        super(context);
        this.enableAlphaSlider = enableAlphaSlider;
        _init(initialColor);
    }

    private void _init(int initialColor) {

        View root = LayoutInflater.from(getContext()).inflate(
                R.layout.color_picker, this, false);

        redSeek = root.findViewById(R.id.red);
        greenSeek = root.findViewById(R.id.green);
        blueSeek = root.findViewById(R.id.blue);
        alphaSeek = root.findViewById(R.id.alpha);
        preview = root.findViewById(R.id.preview);

        redSeek.setProgress(Color.red(initialColor));
        greenSeek.setProgress(Color.green(initialColor));
        blueSeek.setProgress(Color.blue(initialColor));
        alphaSeek.setProgress(Color.alpha(initialColor));
        root.findViewById(R.id.alpha_layout).setVisibility(
                enableAlphaSlider ? VISIBLE : GONE);

        updateColorPreview();

        final SimpleSeekBarChangeListener seekListener = new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int prog, boolean user) {
                if (user)
                    updateColorPreview();
            }
        };
        redSeek.setOnSeekBarChangeListener(seekListener);
        greenSeek.setOnSeekBarChangeListener(seekListener);
        blueSeek.setOnSeekBarChangeListener(seekListener);
        alphaSeek.setOnSeekBarChangeListener(seekListener);

        addView(root);
    }

    /**
     * Get the current color
     * @return Color
     */
    @ColorInt
    public final int getColor() {
        return Color.argb(alphaSeek.getProgress(), redSeek.getProgress(),
                greenSeek.getProgress(),
                blueSeek.getProgress());
    }

    /**
     * Update the color preview box
     */
    @ModifierApi(since = "4.5", target = "4.8", modifiers = {
            "private"
    })
    protected void updateColorPreview() {
        int color = getColor();
        if (!enableAlphaSlider)
            color = 0xFF000000 | (color & 0xFFFFFF);
        preview.setColor(color);
    }
}
