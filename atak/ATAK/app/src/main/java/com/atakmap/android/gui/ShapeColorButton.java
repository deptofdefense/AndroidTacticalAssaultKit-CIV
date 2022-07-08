
package com.atakmap.android.gui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

import com.atakmap.android.gui.drawable.ShapeColorDrawable;
import com.atakmap.android.maps.Shape;
import com.atakmap.app.R;
import com.atakmap.math.MathUtils;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

/**
 * An {@link ImageButton} that can be used to select both a shape stroke
 * and fill color
 */
public class ShapeColorButton extends ImageButton
        implements View.OnClickListener {

    /**
     * Callback interface for when both a stroke and fill color is selected
     */
    public interface OnColorsSelectedListener {

        /**
         * Stroke and fill color selected
         * @param strokeColor Stroke color
         * @param fillColor Fill color
         */
        void onColorsSelected(@ColorInt int strokeColor,
                @ColorInt int fillColor);
    }

    private Shape _shape = null;
    private int _strokeColor = Color.WHITE;
    private int _fillColor = Color.WHITE;
    private boolean _showAlpha = false;
    private boolean _showFill = true;
    private OnColorsSelectedListener _selectListener;

    public ShapeColorButton(Context context) {
        super(context);
        init();
    }

    public ShapeColorButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ShapeColorButton(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr, R.style.darkColorButton);
        init();
    }

    /**
     * Set the default click listener to this button so it opens the color
     * picker dialog
     */
    private void init() {
        setOnClickListener(this);
    }

    /**
     * Set the reference shape for this button to pull colors and state from
     * @param shape Shape
     */
    public void setShape(@NonNull Shape shape) {
        _shape = shape;
        final boolean filled = MathUtils.hasBits(shape.getStyle(),
                Shape.STYLE_FILLED_MASK);
        if (filled)
            setColors(shape.getStrokeColor(), shape.getFillColor());
        else
            setColors(shape.getStrokeColor(), shape.getStrokeColor());
        setShowFill(filled);
    }

    /**
     * Set the stroke color displayed by this button
     * @param color Color integer
     */
    public void setStrokeColor(@ColorInt int color) {
        if (_strokeColor != color) {
            _strokeColor = color;
            updateIcon();
        }
    }

    /**
     * Set the stroke color displayed by this button
     * @param color Color integer
     */
    public void setFillColor(@ColorInt int color) {
        if (_fillColor != color) {
            _fillColor = color;
            updateIcon();
        }
    }

    /**
     * Set both the stroke and fill color displayed by this button
     * @param strokeColor Stroke color
     * @param fillColor Fill color
     */
    public void setColors(@ColorInt int strokeColor, @ColorInt int fillColor) {
        if (_strokeColor != strokeColor || _fillColor != fillColor) {
            _strokeColor = strokeColor;
            _fillColor = fillColor;
            updateIcon();
        }
    }

    /**
     * Set whether to display the alpha value of both colors in the icon
     * @param showAlpha True to show alpha
     */
    public void setShowAlpha(boolean showAlpha) {
        if (_showAlpha != showAlpha) {
            _showAlpha = showAlpha;
            updateIcon();
        }
    }

    /**
     * Set whether to show the fill color or just the stroke color
     * @param showFill True to show the fill color, false to just show stroke
     */
    public void setShowFill(boolean showFill) {
        if (_showFill != showFill) {
            _showFill = showFill;
            updateIcon();
        }
    }

    /**
     * Get the current stroke color set on this button
     * @return Stroke color
     */
    @ColorInt
    public int getStrokeColor() {
        return _strokeColor;
    }

    /**
     * Get the current fill color set on this button
     * @return Fill color
     */
    @ColorInt
    public int getFillColor() {
        return _fillColor;
    }

    /**
     * Set the listener that's called when a color is selected from the color
     * picker
     * @param l Color selected listener
     */
    public void setOnColorsSelectedListener(OnColorsSelectedListener l) {
        _selectListener = l;
    }

    /**
     * Open the color picker dialog using this button's color as the default
     */
    public void openColorPicker() {
        // Use the latest shape colors
        if (_shape != null)
            setShape(_shape);

        // Inflate the shape color palette
        final ColorPalette palette = new ColorPalette(getContext());
        palette.setShowAlpha(_showAlpha);
        palette.setShowFill(_showFill);
        palette.setColors(_strokeColor, _fillColor);

        // Show a dialog containing the color palette
        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        b.setTitle(R.string.select_a_color);
        b.setView(palette);
        if (_showFill) {
            b.setPositiveButton(R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            int stroke = palette.getStrokeColor();
                            int fill = palette.getFillColor();
                            setColors(stroke, fill);
                            if (_shape != null) {
                                _shape.setStrokeColor(stroke);
                                _shape.setFillColor(fill);
                            }
                            if (_selectListener != null)
                                _selectListener.onColorsSelected(stroke, fill);
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
        }
        final AlertDialog d = b.show();

        // Only applies when fill is disabled
        palette.setOnColorSelectedListener(
                new ColorPalette.OnColorSelectedListener() {
                    @Override
                    public void onColorSelected(int color, String label) {
                        d.dismiss();
                        setStrokeColor(color);
                        if (_shape != null)
                            _shape.setStrokeColor(color);
                        if (_selectListener != null)
                            _selectListener.onColorsSelected(color, color);
                    }
                });
    }

    /**
     * Update the icon used for this button that shows both the
     * stroke and fill color
     */
    private void updateIcon() {
        int strokeColor = _strokeColor;
        int fillColor = _fillColor;
        if (!_showAlpha) {
            // Ignore alpha on both colors
            strokeColor = 0xFF000000 | (strokeColor & 0xFFFFFF);
            fillColor = 0xFF000000 | (fillColor & 0xFFFFFF);
        }
        if (!_showFill) {
            // Ignore fill color
            fillColor = strokeColor;
        }
        setImageDrawable(new ShapeColorDrawable(getResources(),
                strokeColor, fillColor));
    }

    @Override
    public void onClick(View v) {
        openColorPicker();
    }
}
