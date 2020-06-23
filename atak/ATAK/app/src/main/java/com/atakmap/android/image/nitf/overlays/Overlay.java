
package com.atakmap.android.image.nitf.overlays;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PathEffect;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;

import java.util.UUID;

/**
 * This class stores the data needed to represent an overlay
 * 
 *
 */
public abstract class Overlay {

    protected static final String TAG = "Overlay";

    public static final int LINE_TYPE_SOLID = 1;
    public static final int LINE_TYPE_DASH = 2;
    public static final int LINE_TYPE_DOT = 3;
    public static final int LINE_TYPE_DASH_DOT = 4;
    public static final int LINE_TYPE_DASH_DOT_DOT = 5;

    public enum LineStyle {
        SOLID(LINE_TYPE_SOLID, null),
        DASH(LINE_TYPE_DASH, new float[] {
                10.0f, 10.0f
        }),
        DOT(LINE_TYPE_DOT, new float[] {
                2.0f, 2.0f
        }),
        DASH_DOT(LINE_TYPE_DASH_DOT, new float[] {
                10.0f, 10.0f, 2.0f, 2.0f
        }),
        DASH_DOT_DOT(LINE_TYPE_DASH_DOT_DOT, new float[] {
                10.0f, 10.0f, 2.0f, 2.0f, 2.0f, 2.0f
        });

        public final int index;
        public final PathEffect effect;

        LineStyle(int index, float[] intervals) {
            this.index = index;
            this.effect = intervals != null ? new DashPathEffect(intervals, 0)
                    : null;
        }

        public static LineStyle get(int index) {
            for (LineStyle style : values()) {
                if (style.index == index)
                    return style;
            }
            return null;
        }
    }

    // An overlay has a color
    protected int color = Color.WHITE;
    protected int fillColor = 0;
    // size of line width
    protected int strokeWidth = 4;
    // line style
    protected int strokeStyle = LINE_TYPE_SOLID;
    // default font size
    protected int fontSize = 14;
    protected float dpiScale = 1;
    // every overlay has a uid;
    protected String uid;
    // name of graphic
    protected String name;
    // bounding box bounds
    protected RectF bounds = new RectF();

    /**
     * Create a new overlay
     * @param uid Overlay UID
     */
    protected Overlay(String name, String uid) {
        this.name = name;
        this.uid = uid;
    }

    protected Overlay(String name) {
        this(name, UUID.randomUUID().toString());
    }

    /**
     * Draw overlay on canvas
     * By default this sets up the paint with this overlay's attributes
     * @param canvas Canvas to draw onto
     * @param paint Paint object to use
     */
    public void draw(Canvas canvas, Paint paint) {
        paint.setColor(getColor());
        paint.setStrokeWidth(getStrokeWidth());
        LineStyle style = LineStyle.get(getStrokeStyle());
        paint.setPathEffect(style != null ? style.effect : null);
        paint.setTextSize(getFontSize() * dpiScale);
    }

    /**
     * Get all points that control this overlay
     * @return Array of points
     */
    public PointF[] getPoints() {
        return new PointF[0];
    }

    /**
     * Offset the entire overlay by a given x,y value
     * @param x X offset
     * @param y Y offset
     */
    public void offset(float x, float y) {
        for (PointF p : getPoints())
            p.offset(x, y);
        bounds.offset(x, y);
    }

    public void offset(PointF offset) {
        offset(offset.x, offset.y);
    }

    /**
     * Test if a point is within this overlay's boundaries
     * @param x Touch point X
     * @param y Touch point Y
     * @param tolerance Touch point tolerance
     * @return True if within bounds
     */
    public static boolean withinBounds(RectF bounds,
            float x, float y, float tolerance) {
        return x >= bounds.left - tolerance
                && x < bounds.right + tolerance
                && y >= bounds.top - tolerance
                && y < bounds.bottom + tolerance;
    }

    public boolean withinBounds(float x, float y, float tolerance) {
        return withinBounds(this.bounds, x, y, tolerance);
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setColor(int color) {
        // Make sure stroke color is always opaque
        this.color = (0xFFFFFF & color) | 0xFF000000;
    }

    public void setFillColor(int color) {
        this.fillColor = color;
    }

    public void setStrokeWidth(int inSize) {
        strokeWidth = inSize;
    }

    public int getStrokeWidth() {
        return strokeWidth;
    }

    public void setStrokeStyle(int style) {
        strokeStyle = style;
    }

    public int getStrokeStyle() {
        return strokeStyle;
    }

    public void setFontSize(int size) {
        fontSize = size;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setDpiScale(float scale) {
        dpiScale = scale;
    }

    public int getColor() {
        return (0xFFFFFF & this.color) | 0xFF000000;
    }

    public int getFillColor() {
        return this.fillColor;
    }

    public boolean isFilled() {
        return Color.alpha(getFillColor()) > 0;
    }

    public String getUID() {
        if (uid == null)
            return "";
        return uid;
    }

    public String getName() {
        return name;
    }

    public RectF getBounds() {
        return bounds;
    }

    public void getIntBounds(Rect b) {
        b.set((int) bounds.left, (int) bounds.top,
                (int) bounds.right, (int) bounds.bottom);
    }

    public Rect getIntBounds() {
        Rect r = new Rect();
        getIntBounds(r);
        return r;
    }
}
