
package com.atakmap.android.maps;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Bundle;

import com.atakmap.android.imagecapture.Capturable;
import com.atakmap.android.imagecapture.CapturePP;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Abstract base for any MapItem with shape-like properties
 *
 * @see Ellipse
 * 
 */
public abstract class Shape extends MapItem implements Capturable {

    public static final int STYLE_FILLED_MASK = 1;
    public static final int STYLE_STROKE_MASK = 2;

    private final ConcurrentLinkedQueue<OnStyleChangedListener> _onStyleChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnStrokeColorChangedListener> _onStrokeColorChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnFillColorChangedListener> _onFillColorChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnStrokeWeightChangedListener> _onStrokeWeightChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnPointsChangedListener> _onPointsChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Polyline.OnBasicLineStyleChangedListener> _onBasicLineStyleChanged = new ConcurrentLinkedQueue<>();

    private int basicLineStyle = Polyline.BASIC_LINE_STYLE_SOLID;
    private int _style = STYLE_STROKE_MASK;
    private int _strokeColor = Color.BLACK;
    private int _fillColor = Color.WHITE;
    private double _strokeWeight = 1d;

    /**
     * Basic line styles.
     */
    public static final int BASIC_LINE_STYLE_SOLID = 0;
    public static final int BASIC_LINE_STYLE_DASHED = 1;
    public static final int BASIC_LINE_STYLE_DOTTED = 2;
    public static final int BASIC_LINE_STYLE_OUTLINED = 3;

    public interface OnBasicLineStyleChangedListener {
        void onBasicLineStyleChanged(Shape p);
    }

    /**
     * Shape style property listener
     * 
     * 
     */
    public interface OnStyleChangedListener {
        void onStyleChanged(Shape s);
    }

    /**
     * Shape strokeColor property listener
     * 
     * 
     */
    public interface OnStrokeColorChangedListener {
        void onStrokeColorChanged(Shape s);
    }

    /**
     * Shape fillColor property listener
     * 
     * 
     */
    public interface OnFillColorChangedListener {
        void onFillColorChanged(Shape s);
    }

    /**
     * Shape strokeWeight property listener
     * 
     * 
     */
    public interface OnStrokeWeightChangedListener {
        void onStrokeWeightChanged(Shape s);
    }

    /**
     * Points property listener
     * 
     * 
     */
    public interface OnPointsChangedListener {
        void onPointsChanged(Shape s);
    }

    /**
     * Add a style property listener
     * 
     * @param listener the listener
     */
    public void addOnStyleChangedListener(OnStyleChangedListener listener) {
        _onStyleChanged.add(listener);
    }

    /**
     * Remove a style property listener
     * 
     * @param listener the listener
     */
    public void removeOnStyleChangedListener(OnStyleChangedListener listener) {
        _onStyleChanged.remove(listener);
    }

    /**
     * Add a strokeColor property listener
     * 
     * @param listener the listener
     */
    public void addOnStrokeColorChangedListener(
            OnStrokeColorChangedListener listener) {
        _onStrokeColorChanged.add(listener);
    }

    /**
     * Remove a strokeColor property listener
     * 
     * @param listener the listener
     */
    public void removeOnStrokeColorChangedListener(
            OnStrokeColorChangedListener listener) {
        _onStrokeColorChanged.remove(listener);
    }

    /**
     * Add a fillColor property listener
     * 
     * @param listener the listener
     */
    public void addOnFillColorChangedListener(
            OnFillColorChangedListener listener) {
        _onFillColorChanged.add(listener);
    }

    /**
     * Remove a fillColor property listener
     * 
     * @param listener the listener
     */
    public void removeOnFillColorChangedListener(
            OnFillColorChangedListener listener) {
        _onFillColorChanged.remove(listener);
    }

    /**
     * Add a strokeWeight property listener
     * 
     * @param listener the listener
     */
    public void addOnStrokeWeightChangedListener(
            OnStrokeWeightChangedListener listener) {
        _onStrokeWeightChanged.add(listener);
    }

    /**
     * Remove a strokeWeight property listener
     * 
     * @param listener the listener
     */
    public void removeOnStrokeWeightChangedListener(
            OnStrokeWeightChangedListener listener) {
        _onStrokeWeightChanged.remove(listener);
    }

    /**
     * Add a point changed property listener
     *
     * @param listener the listener
     */
    public void addOnPointsChangedListener(OnPointsChangedListener listener) {
        _onPointsChanged.add(listener);
    }

    /**
     * Remove a point changed property listener
     *
     * @param listener the listener
     */
    public void removeOnPointsChangedListener(
            OnPointsChangedListener listener) {
        _onPointsChanged.remove(listener);
    }

    protected Shape(final String uid) {
        this(MapItem.createSerialId(), new DefaultMetaDataHolder(), uid);
    }

    protected Shape(final long serialId, final MetaDataHolder metadata,
            final String uid) {
        super(serialId, metadata, uid);
        setAltitudeMode(AltitudeMode.ClampToGround);
    }

    @Override
    public String getTitle() {
        return getMetaString("shapeName", super.getTitle());
    }

    @Override
    public void setTitle(String title) {
        setMetaString("shapeName", title);
        super.setTitle(title);
    }

    /**
     * Get the style property value
     * 
     * @return a bitfield of any STYLE_*_MASK flags from Shape or specific subclass
     */
    public int getStyle() {
        return _style;
    }

    /**
     * Set the style property value
     * 
     * @param style a bitfield of any STYLE_*_MASK flags from Shape or specific subclass
     */
    public void setStyle(int style) {
        if (_style != style) {
            _style = style;
            onStyleChanged();
        }
    }

    public void addStyleBits(int styleBits) {
        setStyle(getStyle() | styleBits);
    }

    public void removeStyleBits(int styleBits) {
        setStyle(getStyle() & ~styleBits);
    }

    /**
     * Get the strokeColor property value
     * 
     * @return An argb packed {@link Color}
     */
    public int getStrokeColor() {
        return _strokeColor;
    }

    /**
     * Set the strokeColor property value
     * 
     * @param strokeColor An argb packed {@link Color}
     */
    public void setStrokeColor(int strokeColor) {
        if (_strokeColor != strokeColor) {
            _strokeColor = strokeColor;
            onStrokeColorChanged();
        }
    }

    /**
     * Get the fillColor property value
     * 
     * @return An argb packed {@link Color}
     */
    public int getFillColor() {
        return _fillColor;
    }

    /**
     * Set the fillColor property value
     * 
     * @param fillColor An argb packed {@link Color}
     */
    public void setFillColor(int fillColor) {
        if (_fillColor != fillColor) {

            // int color = ((Color.red(fillColor)<<16) | (Color.green(fillColor)<<8) |
            // Color.blue(fillColor));
            // int alpha = 85;
            // fillColor = color | (alpha << 24);

            _fillColor = fillColor;
            onFillColorChanged();
        }
    }

    /**
     * Set the alpha value for the fill color
     * @param alpha Color alpha (0 to 255)
     */
    public void setFillAlpha(int alpha) {
        setFillColor((alpha << 24) | (getFillColor() & 0xFFFFFF));
    }

    /**
     * Sets both the stroke and fill color while maintaining fill alpha
     *
     * @param color An argb packed {@link Color}
     * @param includeAlpha Include the color alpha in the fill color
     */
    public void setColor(int color, boolean includeAlpha) {
        int rgb = 0xFFFFFF & color;
        setStrokeColor(-16777216 + rgb);
        int alpha;
        if (includeAlpha)
            alpha = Color.alpha(color);
        else
            alpha = Color.alpha(getFillColor());
        setFillColor((alpha << 24) | rgb);

    }

    public void setColor(int color) {
        setColor(color, false);
    }

    /**
     * Set the stroke weight of this shape's lines
     * Minimum = 1.0, maximum = 6.0
     * 
     * @param strokeWeight a stroke line width
     */
    public void setStrokeWeight(final double strokeWeight) {
        double oldstroke = _strokeWeight;
        _strokeWeight = strokeWeight * MapView.DENSITY;
        if (Double.compare(oldstroke, _strokeWeight) != 0) {
            onStrokeWeightChanged();
        }
    }

    /**
     * Get the strokeWeight property value
     * 
     * @return the weight as a number scaled by density.
     */
    public double getStrokeWeight() {
        return _strokeWeight / MapView.DENSITY;
    }

    /**
     * Get the line style for this shape
     *
     * For now acts a redirect for {@link #setBasicLineStyle(int)} for the sake
     * of having a more intuitive API
     */
    public final void setStrokeStyle(final int lineStyle) {
        setBasicLineStyle(lineStyle);
    }

    /**
     * Get the line style for this shape
     *
     * For now acts a redirect for {@link #getBasicLineStyle()} for the sake
     * of having a more intuitive API
     *
     * @return Line style
     */
    public final int getStrokeStyle() {
        return getBasicLineStyle();
    }

    /**
     * Get the color this shape should use for its icon
     * Usually this is the stroke color or the "iconColor" meta int
     * @return Icon color
     */
    @Override
    public int getIconColor() {
        if (hasMetaValue("iconColor")) {
            try {
                return getMetaInteger("iconColor", Color.WHITE);
            } catch (Exception ignore) {
            }
        }
        return (getStrokeColor() & 0xFFFFFF) + 0xFF000000;
    }

    /**
     * Invoked when the style property changes
     */
    protected void onStyleChanged() {
        for (OnStyleChangedListener l : _onStyleChanged) {
            l.onStyleChanged(this);
        }
    }

    /**
     * Invoked when the strokeColor property changes
     */
    protected void onStrokeColorChanged() {
        for (OnStrokeColorChangedListener l : _onStrokeColorChanged) {
            l.onStrokeColorChanged(this);
        }

    }

    /**
     * Invoked when the fillColor property changes
     */
    protected void onFillColorChanged() {
        for (OnFillColorChangedListener l : _onFillColorChanged) {
            l.onFillColorChanged(this);
        }
    }

    /**
     * Invoked when the strokeWeight property changes
     */
    protected void onStrokeWeightChanged() {
        for (OnStrokeWeightChangedListener l : _onStrokeWeightChanged) {
            l.onStrokeWeightChanged(this);
        }
    }

    /**
     * Change the basic line style for the shape from either  SOLID, DASHED or DOTTED
     * @param basicLineStyle one of Shape.SOLID, Shape.DASHED or Shape.DOTTED
     */
    public void setBasicLineStyle(int basicLineStyle) {
        if (this.basicLineStyle != basicLineStyle) {
            this.basicLineStyle = basicLineStyle;
            onBasicLineStyleChanged();
            onStrokeStyleChanged();
        }
    }

    /**
     * Returns the current state of the Basic Line Style.
     * @return the basic line style for the shape (SOLID, DASHED or DOTTED).
     */
    public int getBasicLineStyle() {
        return basicLineStyle;
    }

    /**
     * Listen for when the style is changed for the basic line style.
     * @param listener the listener to be called when the basic style is changed.
     */
    public void addOnBasicLineStyleChangedListener(
            OnBasicLineStyleChangedListener listener) {
        if (!_onBasicLineStyleChanged.contains(listener))
            _onBasicLineStyleChanged.add(listener);
    }

    /**
     * Remove the listener for the basic line style change
     * @param listener the listener to be removed.
     */
    public void removeOnBasicLineStyleChangedListener(
            OnBasicLineStyleChangedListener listener) {
        _onBasicLineStyleChanged.remove(listener);
    }

    protected void onBasicLineStyleChanged() {
        for (Polyline.OnBasicLineStyleChangedListener l : _onBasicLineStyleChanged) {
            l.onBasicLineStyleChanged(this);
        }
    }

    /**
     * The line/stroke style for this shape has been changed
     */
    protected void onStrokeStyleChanged() {
    }

    protected void onPointsChanged() {
        for (OnPointsChangedListener l : _onPointsChanged)
            l.onPointsChanged(this);
    }

    /**
     * Provides the center point for the Shape, or null if an error has occurred.
     */
    public GeoPointMetaData getCenter() {
        final GeoPoint[] points = this.getPoints();

        // in case the implementation is nasty - just return null;
        if (points == null)
            return null;

        GeoPoint ctr = GeoCalculations.centerOfExtremes(points, 0,
                points.length,
                false);
        if (ctr == null)
            return null;

        return GeoPointMetaData.wrap(ctr,
                GeoPointMetaData.CALCULATED,
                GeoPointMetaData.CALCULATED);
    }

    /**
     * Get point metadata for this shape, usually at the center point
     *
     * @return Point metadata
     */
    public GeoPointMetaData getGeoPointMetaData() {
        GeoPointMetaData pm = getCenter();
        if (pm == null)
            return null;
        pm.setAltitudeSource(getMetaString(GeoPointMetaData.ALTITUDE_SOURCE,
                pm.getAltitudeSource()));
        pm.setGeoPointSource(getMetaString(GeoPointMetaData.GEOPOINT_SOURCE,
                pm.getGeopointSource()));
        return pm;
    }

    @Override
    public GeoPoint getClickPoint() {
        GeoPoint touchPoint = super.getClickPoint();
        return touchPoint == null ? getCenter().get() : touchPoint;
    }

    /**
     * @deprecated {@link #getClickPoint()}
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public GeoPoint findTouchPoint() {
        return getClickPoint();
    }

    /**
     * @deprecated {@link #setClickPoint(GeoPoint)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public void setTouchPoint(GeoPoint point) {
        setClickPoint(point);
    }

    /**
     * Whether to wrap shapes that span more than 180 degrees over the IDL
     * instead of over the prime meridian
     * @return True to wrap across IDL when longitudinal span >180 degrees
     */
    public boolean wrap180() {
        MapView mv = MapView.getMapView();
        return mv != null && mv.isContinuousScrollEnabled();
    }

    @Override
    public Bundle preDrawCanvas(CapturePP capture) {
        // Store forward returns for all points
        Bundle data = new Bundle();
        GeoPoint[] points = getPoints();
        PointF[] p = new PointF[points.length];
        int i = 0;
        for (GeoPoint gp : points)
            p[i++] = capture.forward(gp);
        data.putSerializable("points", p);
        return data;
    }

    @Override
    public void drawCanvas(CapturePP cap, Bundle data) {
        PointF[] p = (PointF[]) data.getSerializable("points");
        if (p == null || p.length < 2)
            return;
        Canvas can = cap.getCanvas();
        Paint paint = cap.getPaint();
        Path path = cap.getPath();
        float dr = cap.getResolution();
        float lineWeight = cap.getLineWeight();
        boolean closed = (getStyle() & Polyline.STYLE_CLOSED_MASK) > 0;
        boolean filled = (getStyle() & Polyline.STYLE_FILLED_MASK) > 0;
        path.moveTo(dr * p[0].x, dr * p[0].y);
        for (int j = 0; j < p.length; j++) {
            if (p[j] == null)
                continue;
            path.lineTo(dr * p[j].x, dr * p[j].y);
            if (j == p.length - 1 && closed)
                path.close();
        }
        if (filled) {
            int fillColor = getFillColor();
            paint.setColor(fillColor);
            paint.setAlpha(Color.alpha(fillColor));
            paint.setStyle(Paint.Style.FILL);
            can.drawPath(path, paint);
        }
        int strokeColor = getStrokeColor();
        paint.setColor(strokeColor);
        paint.setAlpha(Color.alpha(strokeColor));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth((float) getStrokeWeight() * lineWeight);
        can.drawPath(path, paint);
        path.reset();
    }

    /**
     * The array of points without any metadata
     * @return the points
     */
    public abstract GeoPoint[] getPoints();

    /**
     * Returns the Area of the shape in meters
     * @return the area in meters or double NaN is area is not implemented or in the case of an unclosed shape.
     */
    public double getArea() {
        return Double.NaN;
    }

    /**
     * Returns the perimeter of a shape if closed otherwise will return the length of
     * of the open shape from start to end.
     */
    public double getPerimeterOrLength() {
        return Double.NaN;
    }

    /**
     * The array of points that make up the shape with the corresponding metadata.
     * @return the points.
     */
    public abstract GeoPointMetaData[] getMetaDataPoints();

    /**
     * The bounds of the shape
     * @param bounds a preallocated bounds object
     * @return the upper left and lower right of the shape.
     */
    public abstract GeoBounds getBounds(MutableGeoBounds bounds);

}
