
package com.atakmap.android.gui.drawable;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A drawable icon that has 3 states, typically ON, OFF, and SEMI
 * Used for visibility toggles and checkboxes in Overlay Manager
 */
public class ThreeStateDrawable extends Drawable {

    public static final int ON = 0;
    public static final int SEMI = 1;
    public static final int OFF = 2;

    protected final Drawable _onIcon, _semiIcon, _offIcon;

    protected int _state = ON;
    protected int _alpha = 255;
    protected ColorFilter _colorFilter;

    public ThreeStateDrawable(@NonNull Drawable onIcon,
            @NonNull Drawable semiIcon,
            @NonNull Drawable offIcon) {
        _onIcon = onIcon;
        _semiIcon = semiIcon;
        _offIcon = offIcon;
    }

    public ThreeStateDrawable(Context context, int onIconId, int semiIconId,
            int offIconId) {
        this(context.getDrawable(onIconId), context.getDrawable(semiIconId),
                context.getDrawable(offIconId));
    }

    /**
     * Set the state of the drawable
     * @param state One of the three states: {@link #ON}, {@link #OFF},
     *             or {@link #SEMI}
     */
    public void setCurrentState(int state) {
        _state = state;
        invalidateSelf();
    }

    /**
     * Get the current state of the drawable
     * @return One of the three states: {@link #ON}, {@link #OFF},
     * or {@link #SEMI}
     */
    public int getCurrentState() {
        return _state;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Drawable dr;
        switch (_state) {
            default:
            case ON:
                dr = _onIcon;
                break;
            case SEMI:
                dr = _semiIcon;
                break;
            case OFF:
                dr = _offIcon;
                break;
        }

        ColorFilter originalFilter = dr.getColorFilter();
        int originalAlpha = dr.getAlpha();
        dr.setColorFilter(_colorFilter);
        dr.setAlpha(_alpha);
        dr.draw(canvas);
        dr.setAlpha(originalAlpha);
        dr.setColorFilter(originalFilter);
    }

    @Override
    public void setAlpha(int alpha) {
        _alpha = alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        _colorFilter = colorFilter;
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        _onIcon.setBounds(bounds);
        _semiIcon.setBounds(bounds);
        _offIcon.setBounds(bounds);
    }
}
