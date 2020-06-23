
package com.atakmap.android.imagecapture;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;

/**
 * Image capture post-processor
 */

public abstract class CapturePP {

    public final static double DEFAULT_MIN_RENDER_SCALE = (1.0d / 100000.0d);

    // Capture width and height
    protected float _width, _height;
    protected double _mapScale, _mapRes;

    // Display point scale
    protected float _dp = 1.0f;

    // Draw scale to be applied to item positions during render
    protected int _drawRes = 1;

    protected Canvas _can;
    protected Paint _paint = new Paint();
    protected Path _path = new Path();
    protected float _fontSize, _pdFontSize, _labelSize, _pdLabelSize,
            _borderWidth, _iconSize, _lineWeight;
    protected DashPathEffect _dashStyle, _dotStyle;

    public synchronized float getWidth() {
        return _width;
    }

    public synchronized float getHeight() {
        return _height;
    }

    public synchronized Canvas getCanvas() {
        return _can;
    }

    public synchronized Paint getPaint() {
        return _paint;
    }

    public synchronized Path getPath() {
        return _path;
    }

    public synchronized float getResolution() {
        return _drawRes;
    }

    public synchronized double getMapResolution() {
        return _mapRes;
    }

    public synchronized float getLineWeight() {
        return _lineWeight;
    }

    public synchronized float getIconSize() {
        return _iconSize;
    }

    public synchronized float getFontSize() {
        return _fontSize;
    }

    public synchronized float getLabelSize() {
        return _labelSize;
    }

    public synchronized float getDisplayPoint() {
        return _dp;
    }

    public synchronized DashPathEffect getDashed() {
        return _dashStyle;
    }

    public synchronized DashPathEffect getDotted() {
        return _dotStyle;
    }

    public synchronized int getThemeColor(int color) {
        return color;
    }

    protected synchronized void resetPaint() {
        _paint.setAntiAlias(true);
        _paint.setFilterBitmap(true);
        _paint.setStrokeJoin(Paint.Join.ROUND);
        _paint.setStrokeCap(Paint.Cap.ROUND);
        _paint.setTextAlign(Paint.Align.CENTER);
        _paint.setStyle(Paint.Style.FILL);
        _paint.setTextSize(_fontSize);
        _paint.setStrokeWidth(_borderWidth);
        _paint.setColor(Color.WHITE);
        _paint.setColorFilter(null);
        _paint.setPathEffect(null);
        _paint.setAlpha(255);
        _path.reset();
    }

    /**
     * Draw an ATAK-style map label
     * @param txt Text to draw
     * @param pos Center position and angle of text (see yAlign)
     * @param align Horizontal and vertical text alignment bit num
     * @param txtColor Color of text
     * @param background True to include black background, false to border text
     */
    public synchronized void drawLabel(String txt, PointF pos, int align,
            int txtColor, boolean background) {
        // Draw label box
        resetPaint();
        _paint.setTextSize(_labelSize);
        _paint.setColor(txtColor);
        double ang = 0;
        if (pos instanceof PointA) {
            ang = CanvasHelper.deg360(((PointA) pos).angle);
            if (ang > 90 && ang <= 270)
                ang = CanvasHelper.deg360(ang + 180);
        }
        TextRect tBox = new TextRect(_paint, _pdLabelSize, txt);
        tBox.alignTo(align);
        _can.save();
        _can.translate(dr(pos.x), dr(pos.y));
        _can.rotate((float) ang);
        // Draw label
        if (background) {
            _paint.setColor(Color.argb(153, 0, 0, 0));
            _can.drawRoundRect(tBox, _pdLabelSize, _pdLabelSize, _paint);
            _paint.setColor(txtColor);
            tBox.draw(_can);
        } else
            tBox.draw(_can, _borderWidth, getThemeColor(Color.BLACK));
        _can.restore();
    }

    public void drawLabel(String txt, PointF pos) {
        drawLabel(txt, pos, TextRect.ALIGN_X_CENTER | TextRect.ALIGN_Y_CENTER,
                Color.WHITE, true);
    }

    public boolean shouldDrawLabel(MapItem item, String label) {
        return item != null && label != null && !label.isEmpty()
                && item.getMetaDouble("minRenderScale",
                        DEFAULT_MIN_RENDER_SCALE) <= _mapScale;
    }

    public synchronized boolean shouldDrawLabel(String label,
            PointF[] labelLine) {
        if (label == null || label.isEmpty())
            return false;
        if (labelLine != null && labelLine.length >= 2) {
            _paint.setTextSize(_labelSize);
            TextRect tBox = new TextRect(_paint, _pdLabelSize, label);
            double width = Math.hypot(labelLine[0].x - labelLine[1].x,
                    labelLine[0].y - labelLine[1].y) * getResolution();
            return tBox.width() < width;
        }
        return true;
    }

    public synchronized Bitmap loadBitmap(String uri) {
        return ATAKUtilities.getUriBitmap(uri);
    }

    public synchronized boolean inside(PointF p) {
        return p.x >= 0 && p.x <= _width
                && p.y >= 0 && p.y <= _height;
    }

    /**
     * Convert pixel to display points
     * _dp = smaller dimension of image / 360
     * @param pix Pixel value
     * @return Display point value
     */
    protected synchronized float dp(float pix) {
        return _dp * pix;
    }

    /**
     * Multiply pixel by drawing resolution
     * _drawRes = 1 if rendering in capture window, _capRes otherwise
     * @param pix Pixel value
     * @return Scaled draw res value
     */
    protected synchronized float dr(float pix) {
        return getResolution() * pix;
    }

    protected static int toInt(boolean b) {
        return b ? 1 : 0;
    }

    public abstract PointF forward(GeoPoint gp);

    public abstract GeoBounds getBounds();

    public abstract boolean drawElements(Canvas canvas);
}
