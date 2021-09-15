
package com.atakmap.android.gui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.atakmap.app.R;

public class ColorPicker extends LinearLayout {

    private static final int SEEK_MAX = 255;

    private SeekBar redSeek;
    private SeekBar greenSeek;
    private SeekBar blueSeek;
    private SeekBar alphaSeek;
    private RelativeLayout colorLayout;
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
        this.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                550, LinearLayout.LayoutParams.WRAP_CONTENT);
        this.setLayoutParams(params);

        LinearLayout layout = new LinearLayout(this.getContext());
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(10, 10, 10, 0);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setLayoutParams(layoutParams);

        RelativeLayout seekbarLayout = new RelativeLayout(this.getContext());
        LinearLayout.LayoutParams seekbarLayoutParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT);
        seekbarLayoutParams.weight = (float) 0.75;
        seekbarLayout.setLayoutParams(seekbarLayoutParams);

        LinearLayout seekbarInnerLayout = new LinearLayout(this.getContext());
        LinearLayout.LayoutParams seekbarInnerLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        seekbarInnerLayout.setLayoutParams(seekbarInnerLayoutParams);
        seekbarInnerLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout redLayout = new LinearLayout(this.getContext());
        LinearLayout.LayoutParams redLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        redLayout.setLayoutParams(redLayoutParams);
        redLayout.setGravity(Gravity.CENTER);
        redLayout.setOrientation(LinearLayout.HORIZONTAL);
        TextView redLabel = new TextView(this.getContext());

        redLabel.setText(R.string.r);
        redSeek = new SeekBar(this.getContext());
        redSeek.setMax(SEEK_MAX);
        redSeek.setProgress(Color.red(initialColor));
        LinearLayout.LayoutParams redSeekParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        redSeek.setLayoutParams(redSeekParams);
        redSeek.setOnSeekBarChangeListener(new ProgressChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                updateColor();
            }
        });
        redLayout.addView(redLabel);
        redLayout.addView(redSeek);

        LinearLayout greenLayout = new LinearLayout(this.getContext());
        LinearLayout.LayoutParams greenLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        greenLayout.setLayoutParams(greenLayoutParams);
        greenLayout.setGravity(Gravity.CENTER);
        greenLayout.setOrientation(LinearLayout.HORIZONTAL);
        TextView greenLabel = new TextView(this.getContext());
        greenLabel.setText(R.string.g);
        greenSeek = new SeekBar(this.getContext());
        greenSeek.setMax(SEEK_MAX);
        greenSeek.setProgress(Color.green(initialColor));
        LinearLayout.LayoutParams greenSeekParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        greenSeek.setLayoutParams(greenSeekParams);
        greenSeek.setOnSeekBarChangeListener(new ProgressChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                updateColor();
            }
        });
        greenLayout.addView(greenLabel);
        greenLayout.addView(greenSeek);

        LinearLayout blueLayout = new LinearLayout(this.getContext());
        LinearLayout.LayoutParams blueLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        blueLayout.setLayoutParams(blueLayoutParams);
        blueLayout.setGravity(Gravity.CENTER);
        blueLayout.setOrientation(LinearLayout.HORIZONTAL);
        TextView blueLabel = new TextView(this.getContext());
        blueLabel.setText(R.string.b);
        blueSeek = new SeekBar(this.getContext());
        blueSeek.setMax(SEEK_MAX);
        blueSeek.setProgress(Color.blue(initialColor));
        LinearLayout.LayoutParams blueSeekParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        blueSeek.setLayoutParams(blueSeekParams);
        blueSeek.setOnSeekBarChangeListener(new ProgressChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                updateColor();
            }
        });
        blueLayout.addView(blueLabel);
        blueLayout.addView(blueSeek);

        LinearLayout alphaLayout = new LinearLayout(this.getContext());
        LinearLayout.LayoutParams alphaLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        // Setup the slider so we can still use it for updateColor() and getColor() without more
        // checks on enableAlphaSlider.
        alphaSeek = new SeekBar(this.getContext());
        alphaSeek.setMax(SEEK_MAX);
        alphaSeek.setProgress(Color.alpha(initialColor));
        if (enableAlphaSlider) {
            alphaLayout.setLayoutParams(alphaLayoutParams);
            alphaLayout.setGravity(Gravity.CENTER);
            alphaLayout.setOrientation(LinearLayout.HORIZONTAL);
            TextView alphaLabel = new TextView(this.getContext());
            alphaLabel.setText(R.string.a);
            LinearLayout.LayoutParams alphaSeekParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            alphaSeek.setLayoutParams(alphaSeekParams);
            alphaSeek.setOnSeekBarChangeListener(new ProgressChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress,
                        boolean fromUser) {
                    updateColor();
                }
            });
            alphaLayout.addView(alphaLabel);
            alphaLayout.addView(alphaSeek);
        }

        seekbarInnerLayout.addView(redLayout);
        seekbarInnerLayout.addView(greenLayout);
        seekbarInnerLayout.addView(blueLayout);
        if (enableAlphaSlider) {
            seekbarInnerLayout.addView(alphaLayout);
        }
        seekbarLayout.addView(seekbarInnerLayout);
        layout.addView(seekbarLayout);

        colorLayout = new RelativeLayout(this.getContext());
        LinearLayout.LayoutParams colorLayoutParams = new LinearLayout.LayoutParams(
                0, RelativeLayout.LayoutParams.MATCH_PARENT);
        colorLayoutParams.weight = (float) 0.25;
        colorLayoutParams.setMargins(5, 5, 5, 5);
        colorLayout.setLayoutParams(colorLayoutParams);
        colorLayout.setBackgroundColor(initialColor);
        layout.addView(colorLayout);
        this.addView(layout);
    }

    public final int getColor() {
        return Color.argb(alphaSeek.getProgress(), redSeek.getProgress(),
                greenSeek.getProgress(),
                blueSeek.getProgress());
    }

    private void updateColor() {
        colorLayout.setBackgroundColor(Color.argb(alphaSeek.getProgress(),
                redSeek.getProgress(), greenSeek.getProgress(),
                blueSeek.getProgress()));
    }

    private static abstract class ProgressChangeListener implements
            OnSeekBarChangeListener {

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public abstract void onProgressChanged(SeekBar seekBar, int progress,
                boolean fromUser);
    }

}
