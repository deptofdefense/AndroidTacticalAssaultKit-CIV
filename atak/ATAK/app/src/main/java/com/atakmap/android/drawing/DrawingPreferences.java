
package com.atakmap.android.drawing;

import android.graphics.Color;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.preference.AtakPreferences;

/**
 * Preference helper for drawing objects
 */
public class DrawingPreferences extends AtakPreferences {

    public static final String SHAPE_COLOR = "shape_color";
    public static final String SHAPE_FILL_ALPHA = "shape_fill_alpha";
    public static final String STROKE_WEIGHT = "strokeWeight";
    public static final String STROKE_STYLE = "strokeStyle";

    public DrawingPreferences(MapView mapView) {
        super(mapView);
    }

    /**
     * Set the default shape color
     * @param color Shape color {@link Color}
     */
    public void setShapeColor(int color) {
        set(SHAPE_COLOR, color);
    }

    /**
     * Get the default shape color
     * @return Shape color {@link Color}
     */
    public int getShapeColor() {
        return get(SHAPE_COLOR, Color.WHITE);
    }

    /**
     * Set the default shape fill alpha
     * @param alpha Fill alpha (0 - 255)
     */
    public void setFillAlpha(int alpha) {
        set(SHAPE_FILL_ALPHA, alpha);
    }

    public int getFillAlpha() {
        return get(SHAPE_FILL_ALPHA, 150);
    }

    /**
     * Get the default fill color (shape_color + fill_alpha)
     * @return Fill color {@link Color}
     */
    public int getFillColor() {
        int baseColor = getShapeColor();
        int fillAlpha = getFillAlpha();
        return (fillAlpha << 24) | (0xFFFFFF & baseColor);
    }

    /**
     * Set the default shape stroke weight
     * @param strokeWeight Stroke weight
     */
    public void setStrokeWeight(double strokeWeight) {
        set(STROKE_WEIGHT, strokeWeight);
    }

    /**
     * Get the default stroke weight
     * @return Stroke weight
     */
    public double getStrokeWeight() {
        return get(STROKE_WEIGHT, 4d);
    }

    /**
     * Set the default shape line style
     * @param lineStyle Line style (see {@link Shape#setStrokeStyle(int)})
     */
    public void setStrokeStyle(int lineStyle) {
        set(STROKE_STYLE, lineStyle);
    }

    /**
     * Get the default stroke style
     * @return Stroke style (see {@link Shape#getStrokeStyle()})
     */
    public int getStrokeStyle() {
        return get(STROKE_STYLE, Shape.BASIC_LINE_STYLE_SOLID);
    }
}
