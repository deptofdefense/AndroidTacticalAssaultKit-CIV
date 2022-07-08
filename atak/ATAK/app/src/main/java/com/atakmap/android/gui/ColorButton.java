
package com.atakmap.android.gui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

import com.atakmap.android.gui.ColorPalette.OnColorSelectedListener;

import com.atakmap.app.R;

import androidx.annotation.ColorInt;

/**
 * An {@link ImageButton} that can be used to select a color
 */
public class ColorButton extends ImageButton implements View.OnClickListener {

    private int _color = Color.WHITE;
    private OnColorSelectedListener _selectListener;

    public ColorButton(Context context) {
        super(context);
        init();
    }

    public ColorButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ColorButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr, R.style.darkColorButton);
        init();
    }

    /**
     * Set the default click listener to opening the color picker
     */
    private void init() {
        setOnClickListener(this);
    }

    /**
     * Set the color displayed by this button
     * @param color Color integer
     */
    public void setColor(@ColorInt int color) {
        if (_color != color) {
            _color = color;
            setColorFilter(color, PorterDuff.Mode.MULTIPLY);
        }
    }

    /**
     * Get the color currently set on this button
     * @return Color integer
     */
    @ColorInt
    public int getColor() {
        return _color;
    }

    /**
     * Set the listener that's called when a color is selected from the color
     * picker
     * @param l Color selected listener
     */
    public void setOnColorSelectedListener(OnColorSelectedListener l) {
        _selectListener = l;
    }

    /**
     * Open the color picker dialog using this button's color as the default
     */
    public void openColorPicker() {
        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        b.setTitle(R.string.select_a_color);
        ColorPalette palette = new ColorPalette(getContext());
        palette.setColor(_color);
        b.setView(palette);
        final AlertDialog d = b.show();
        palette.setOnColorSelectedListener(new OnColorSelectedListener() {
            @Override
            public void onColorSelected(int color, String label) {
                d.dismiss();
                setColor(color);
                if (_selectListener != null)
                    _selectListener.onColorSelected(color, label);
            }
        });
    }

    @Override
    public void onClick(View v) {
        openColorPicker();
    }
}
