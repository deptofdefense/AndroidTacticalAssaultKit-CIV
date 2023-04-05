
package com.atakmap.android.gui.drawable;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * An icon used to show both shape stroke color and fill color
 */
public class ShapeColorDrawable extends Drawable {

    private final int _strokeColor, _fillColor;
    private final Paint _paint = new Paint();
    private final float _dp;

    public ShapeColorDrawable(Resources res, int strokeColor, int fillColor) {
        _dp = res.getDisplayMetrics().density;
        _strokeColor = strokeColor;
        _fillColor = fillColor;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        float strokeWidth = 3 * _dp;
        float p = strokeWidth / 2f;
        float radius = 4 * _dp;
        float width = b.width() - p;
        float height = b.height() - p;
        _paint.setStrokeWidth(strokeWidth);

        _paint.setStyle(Paint.Style.FILL);
        _paint.setColor(_fillColor);
        canvas.drawRoundRect(p, p, width, height, radius, radius, _paint);

        _paint.setStyle(Paint.Style.STROKE);
        _paint.setColor(_strokeColor);
        canvas.drawRoundRect(p, p, width, height, radius, radius, _paint);
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
