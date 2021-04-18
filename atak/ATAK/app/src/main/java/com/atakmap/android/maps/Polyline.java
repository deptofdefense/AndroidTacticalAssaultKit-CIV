
package com.atakmap.android.maps;

import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;

import com.atakmap.android.imagecapture.CanvasHelper;
import com.atakmap.android.imagecapture.CapturePP;
import com.atakmap.android.imagecapture.PointA;
import com.atakmap.android.maps.graphics.GLMapItem;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A Shape made up of GeoPoints connected by lines and optionally closed
 * 
 * 
 */
public class Polyline extends Shape {

    /**
     * The minimum line render resolution for a Polyline.
     */
    public final static double DEFAULT_MIN_LINE_RENDER_RESOLUTION = 0d;

    /**
     * The maximum line render resolution for a Polyline.
     */
    public final static double DEFAULT_MAX_LINE_RENDER_RESOLUTION = Double.MAX_VALUE;

    /**
     * Minimum label render resolution for a polyline that is inside the render window.
     */
    public final static double DEFAULT_MIN_LABEL_RENDER_RESOLUTION = 0d;

    /**
     * Maximum label render resolution for a polyline that is inside the render window.
     */
    public final static double DEFAULT_MAX_LABEL_RENDER_RESOLUTION = 10d;

    /**
     * A line will be drawn from the last point to the first and the STYLE_SHAPE_FILLED_MASK will
     * take effect (if exists)
     */
    public static final int STYLE_CLOSED_MASK = 4;

    /**
     * Adds a thin black outline on the stroke line which increases visual distinction. The outline
     * will retain the alpha value of the stroke color.
     */
    public static final int STYLE_OUTLINE_STROKE_MASK = 8;

    /**
     * Adds a halo around the polyline with the same color that was used in the stroke.
     */
    public static final int STYLE_OUTLINE_HALO_MASK = 16;

    /**
     * Basic line styles.
     */
    public static final int BASIC_LINE_STYLE_SOLID = 0;
    public static final int BASIC_LINE_STYLE_DASHED = 1;
    public static final int BASIC_LINE_STYLE_DOTTED = 2;

    /**
     * Height styles (bit masks)
     */
    public static final int HEIGHT_STYLE_NONE = 0, // Do not draw height in 3D
            HEIGHT_STYLE_POLYGON = 1, // Draw the 3D height polygon
            HEIGHT_STYLE_OUTLINE = 2, // Draw an outline representing the height
            HEIGHT_STYLE_OUTLINE_SIMPLE = 4; // Simplified height outline

    /**
     * Methods for how to extrude the shape's height (mutually exclusive)
     */
    public static final int HEIGHT_EXTRUDE_DEFAULT = 0, // Default based on shape properties
            HEIGHT_EXTRUDE_MIN_ALT = 1, // Extrude from the lowest point elevation
            HEIGHT_EXTRUDE_MAX_ALT = 2, // Extrude from the highest point elevation
            HEIGHT_EXTRUDE_CENTER_ALT = 3, // Extrude from the center elevation
            HEIGHT_EXTRUDE_PER_POINT = 4; // Extrude from each point's elevation

    private Map<String, Object> labels;

    private int _labelTextSize = MapView.getDefaultTextFormat().getFontSize();
    private Typeface _labelTypeface = Typeface.DEFAULT;

    private AltitudeMode altitudeMode = AltitudeMode.ClampToGround;

    // The maximum number of points per touch partition
    public static final int PARTITION_SIZE = 25;

    /*
     * public Polyline() { this(MapItem.createSerialId(), new DefaultMetaDataHolder()); }
     */

    public Polyline(final String uid) {
        super(uid);
    }

    public Polyline(final long serialId, final MetaDataHolder metadata,
            final String uid) {
        super(serialId, metadata, uid);
    }

    /**
     * Get the points property value
     * 
     * @param points the array to use on return if large enough (or not null)
     * @return GeoPoint array of points
     */
    synchronized public GeoPointMetaData[] getPoints(
            GeoPointMetaData[] points) {
        return _points.toArray(points == null ? _EMPTY_POINTS : points);
    }

    /**
     * Get the points property value
     * 
     * @return GeoPoint array of points
     */
    @Override
    public synchronized GeoPoint[] getPoints() {
        GeoPoint[] retval = new GeoPoint[_points.size()];
        for (int i = 0; i < retval.length; ++i)
            retval[i] = _points.get(i).get();
        return retval;
    }

    synchronized public GeoPoint[] getPoints(GeoPoint[] points) {
        GeoPoint[] retval = points;
        if (points == null || points.length < _points.size())
            retval = new GeoPoint[_points.size()];

        for (int i = 0; i < retval.length; ++i)
            retval[i] = _points.get(i).get();
        return retval;

    }

    /**
     * Get the points property value
     *
     * @return GeoPoint array of points
     */
    @Override
    public GeoPointMetaData[] getMetaDataPoints() {
        return getPoints((GeoPointMetaData[]) null);
    }

    /**
     * Set the points for the polyline.   These points have metadata.
     * 
     * @param points the array of points
     */
    public void setPoints(GeoPointMetaData[] points) {
        this.setPoints(points, 0, points.length);
    }

    /**
     * Set the points for the polyline.   These points do not have any metadata.
     *
     * @param points the array of points, cannot be null.
     */
    public void setPoints(GeoPoint[] points) {
        this.setPoints(GeoPointMetaData.wrap(points), 0, points.length);
    }

    /**
     * Given an array of points points, set the sub array defined by offset and length
     * as the points for the Polyine.
     * @param points lat/lon/alt
     * @param off the offset into the points array
     * @param len the length to be used within the points array.
     */
    public void setPoints(GeoPoint[] points, int off, int len) {
        this.setPoints(GeoPointMetaData.wrap(points), off, len);
    }

    /**
     * Given an array of metadata enhanced points, set the sub array defined by offset and length
     * as the points for the Polyine.
     * @param points metadata enhanced points
     * @param off the offset into the points array
     * @param len the length to be used within the points array.
     */
    synchronized public void setPoints(final GeoPointMetaData[] points,
            final int off, final int len) {

        // check to see if it is a duplicate set of points before actually firing the on points 
        // changed
        if (len == _points.size()) {
            boolean change = false;
            for (int i = off; i < off + len && !change; i++) {
                change = (!_points.get(i - off).equals(points[i]));
            }

            if (!change) {
                // if they are all equal, but are composed of MutableGeoPoints,
                // assume that the points have changed.
                this.onPointsChanged();
                return;

            }
        }

        _points.clear();
        final int lim = (off + len);
        _points.addAll(Arrays.asList(points).subList(off, lim));
        this.onPointsChanged();
    }

    @Override
    protected void onPointsChanged() {
        MapView mv = MapView.getMapView();
        this.minimumBoundingBox.set(
                GeoPointMetaData
                        .unwrap(_points.toArray(new GeoPointMetaData[0])),
                mv != null && mv.isContinuousScrollEnabled());
        super.onPointsChanged();
    }

    @Override
    public GeoPointMetaData getCenter() {
        return GeoPointMetaData.wrap(this.minimumBoundingBox.getCenter(null),
                GeoPointMetaData.CALCULATED, GeoPointMetaData.CALCULATED);
    }

    synchronized public double getTotalDistance() {
        if (_points.size() < 1)
            return 0.0d;
        double distance = 0.0d;
        Iterator<GeoPointMetaData> iter = _points.iterator();
        GeoPointMetaData last = iter.next();
        GeoPointMetaData current;
        while (iter.hasNext()) {
            current = iter.next();
            distance += DistanceCalculations.metersFromAtSourceTarget(
                    last.get(),
                    current.get());
            last = current;
        }
        return distance;
    }

    private static final GeoPointMetaData[] _EMPTY_POINTS = {};
    protected final ArrayList<GeoPointMetaData> _points = new ArrayList<>();
    protected final MutableGeoBounds minimumBoundingBox = new MutableGeoBounds(
            0, 0,
            0, 0);

    /**
     * Starts BasicLineStyle
     */

    private final ConcurrentLinkedQueue<OnBasicLineStyleChangedListener> _onBasicLineStyleChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnLabelsChangedListener> _onLabelsChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnLabelTextSizeChanged> _onLabelTextSizeChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnAltitudeModeChangedListener> _onAltitudeModeChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnHeightStyleChangedListener> _onHeightStyleChanged = new ConcurrentLinkedQueue<>();

    private int basicLineStyle = Polyline.BASIC_LINE_STYLE_SOLID;
    private int heightStyle = HEIGHT_STYLE_POLYGON | HEIGHT_STYLE_OUTLINE;
    private int extrudeMode = HEIGHT_EXTRUDE_DEFAULT;

    public interface OnLabelsChangedListener {
        void onLabelsChanged(Polyline p);
    }

    public interface OnBasicLineStyleChangedListener {
        void onBasicLineStyleChanged(Polyline p);
    }

    public interface OnLabelTextSizeChanged {
        void onLabelTextSizeChanged(Polyline p);
    }

    public interface OnAltitudeModeChangedListener {
        /**
         * Called when the altitude mode is changed for the specific Polyline.
         * @param altitudeMode the altitude mode that the polyline was set to.
         */
        void onAltitudeModeChanged(Feature.AltitudeMode altitudeMode);
    }

    public interface OnHeightStyleChangedListener {
        /**
         * Height style flag has been modified
         * This flag controls how 3D extruded height is drawn
         * @param p Polyline
         */
        void onHeightStyleChanged(Polyline p);
    }

    public void setLabels(final Map<String, Object> labels) {
        if (labels == null) {
            this.labels = null;
        } else {
            this.labels = new HashMap<>(labels);
        }
        onLabelsChanged();
    }

    public Map<String, Object> getLabels() {
        return labels;
    }

    public void addOnLabelTextSizeChangedListener(
            OnLabelTextSizeChanged listener) {
        if (!_onLabelTextSizeChanged.contains(listener)) {
            _onLabelTextSizeChanged.add(listener);
        }
    }

    public void removeOnLabelTextSizeChangedListner(
            OnLabelTextSizeChanged listener) {
        _onLabelTextSizeChanged.remove(listener);
    }

    public void addOnLabelsChangedListener(
            OnLabelsChangedListener listener) {
        if (!_onLabelsChanged.contains(listener))
            _onLabelsChanged.add(listener);
    }

    public void removeOnLabelsChangedListener(
            OnLabelsChangedListener listener) {
        _onLabelsChanged.remove(listener);
    }

    protected void onLabelsChanged() {
        for (Polyline.OnLabelsChangedListener l : _onLabelsChanged) {
            l.onLabelsChanged(this);
        }
    }

    protected void onLabelTextSizeChanged() {
        for (Polyline.OnLabelTextSizeChanged l : _onLabelTextSizeChanged) {
            l.onLabelTextSizeChanged(this);
        }
    }

    public void setBasicLineStyle(int basicLineStyle) {
        this.basicLineStyle = basicLineStyle;
        onBasicLineStyleChanged();
    }

    public int getBasicLineStyle() {
        return basicLineStyle;
    }

    public void addOnBasicLineStyleChangedListener(
            OnBasicLineStyleChangedListener listener) {
        if (!_onBasicLineStyleChanged.contains(listener))
            _onBasicLineStyleChanged.add(listener);
    }

    public void removeOnBasicLineStyleChangedListener(
            OnBasicLineStyleChangedListener listener) {
        _onBasicLineStyleChanged.remove(listener);
    }

    protected void onBasicLineStyleChanged() {
        for (Polyline.OnBasicLineStyleChangedListener l : _onBasicLineStyleChanged) {
            l.onBasicLineStyleChanged(this);
        }
    }

    public void addOnAltitudeModeChangedListener(
            OnAltitudeModeChangedListener listener) {
        _onAltitudeModeChanged.add(listener);
    }

    public void removeOnAltitudeModeChangedListener(
            OnAltitudeModeChangedListener listener) {
        _onAltitudeModeChanged.remove(listener);
    }

    protected void onAltitudeModeChanged() {
        for (OnAltitudeModeChangedListener l : _onAltitudeModeChanged) {
            l.onAltitudeModeChanged(altitudeMode);
        }
    }

    public void setAltitudeMode(AltitudeMode altitudeMode) {
        this.altitudeMode = altitudeMode;
        onAltitudeModeChanged();
    }

    public void addOnHeightStyleChangedListener(
            OnHeightStyleChangedListener l) {
        _onHeightStyleChanged.add(l);
    }

    public void removeOnHeightStyleChangedListener(
            OnHeightStyleChangedListener l) {
        _onHeightStyleChanged.remove(l);
    }

    protected void onHeightStyleChanged() {
        for (OnHeightStyleChangedListener l : _onHeightStyleChanged)
            l.onHeightStyleChanged(this);
    }

    /**
     * Set the height rendering style for this shape
     * Example: {@link #HEIGHT_STYLE_POLYGON} | {@link #HEIGHT_STYLE_OUTLINE}
     * @param heightStyle Height style bit flags
     */
    public void setHeightStyle(int heightStyle) {
        if (this.heightStyle != heightStyle) {
            this.heightStyle = heightStyle;
            onHeightStyleChanged();
        }
    }

    /**
     * Get the height rendering style for this shape
     * @return Height style bit flags
     */
    public int getHeightStyle() {
        return this.heightStyle;
    }

    /**
     * Add a single height style bit to this shape
     * Example: {@link #HEIGHT_STYLE_OUTLINE}
     * @param heightStyleBit Height style bit
     */
    public void addHeightStyle(int heightStyleBit) {
        setHeightStyle(heightStyle | heightStyleBit);
    }

    /**
     * Remove a single height style bit from this shape
     * @param heightStyleBit Height style bit
     */
    public void removeHeightStyle(int heightStyleBit) {
        setHeightStyle(heightStyle & ~heightStyleBit);
    }

    /**
     * Set how the height is extruded off the shape
     * Mode can be one of the following:
     * {@link #HEIGHT_EXTRUDE_DEFAULT} Extrude based on shape properties
     * {@link #HEIGHT_EXTRUDE_MIN_ALT} Extrude off the minimum point elevation
     * {@link #HEIGHT_EXTRUDE_MAX_ALT} Extrude off the maximum point elevation
     * {@link #HEIGHT_EXTRUDE_CENTER_ALT} Extrude off the center point elevation
     * {@link #HEIGHT_EXTRUDE_PER_POINT} Extrude off each point's elevation
     * @param mode Extrusion mode
     */
    public void setHeightExtrudeMode(int mode) {
        if (this.extrudeMode != mode) {
            this.extrudeMode = mode;
            onHeightStyleChanged();
        }
    }

    /**
     * Get the height extrusion mode
     * See {@link #setHeightExtrudeMode(int)}
     * @return The height extrusion mode
     */
    public int getHeightExtrudeMode() {
        return this.extrudeMode;
    }

    @Override
    public GeoBounds getBounds(MutableGeoBounds bounds) {
        if (bounds != null) {
            bounds.set(this.minimumBoundingBox);
            return bounds;
        } else {
            return new GeoBounds(this.minimumBoundingBox);
        }
    }

    /**
     * Returns the bounds relative to the center of the shape.
     * 
     * @param bounds    The bounds relative to the center of the shape.
     */
    public void getRelativeBounds(MutableGeoBounds bounds) {
        final GeoPoint center = this.getCenter().get();
        if (center != null) {
            bounds.set(
                    this.minimumBoundingBox.getNorth() - center.getLatitude(),
                    this.minimumBoundingBox.getWest() - center.getLongitude(),
                    this.minimumBoundingBox.getSouth() - center.getLatitude(),
                    this.minimumBoundingBox.getEast() - center.getLongitude());
        } else {
            bounds.set(this.minimumBoundingBox);
        }
    }

    /**
     * Sets the text size to use for the rendering label
     * convenience method for setting label size meta
     * @param labelTextSize the int value size to use on the default format
     */
    public void setLabelTextSize(int labelTextSize) {
        _labelTextSize = labelTextSize;
        onLabelTextSizeChanged();
    }

    /**
     * Gets the text size to use for the rendering label
     * convenience method for getting label size meta
     * @return default MapView font size if key not found in mapping
     */
    public int getLabelTextSize() {
        return _labelTextSize;
    }

    /**
     * Gets the current Altitude Mode for the polyline.
     * @return the altitude mode.
     */
    public AltitudeMode getAltitudeMode() {
        return altitudeMode;
    }

    /**
     * Sets the text size and typeface to use for the rendering label
     * @param labelTextSize the int value size to use on the default format
     * @param typeface the typeface graphic to use to draw the label text
     */
    public void setLabelTextSize(int labelTextSize, Typeface typeface) {
        _labelTextSize = labelTextSize;
        _labelTypeface = typeface;
        onLabelTextSizeChanged();
    }

    /**
     * Gets the text typeface to use for the rendering label
     * @param typeface the typeface graphic to use to draw the label text
     */
    public void setLabelTextTypeface(Typeface typeface) {
        _labelTypeface = typeface;
        onLabelTextSizeChanged();
    }

    /**
     * @return the current label drawing typeface for the polyline labels
     */
    public Typeface getLabelTypeface() {
        return _labelTypeface;
    }

    /**
     * Set the floating line label for this polyline
     * Not to be confused with individual segment labels
     *
     * @param label Line label
     */
    public void setLineLabel(String label) {
        setMetaString("polylineLabel", label);
    }

    public String getLineLabel() {
        return getMetaString("polylineLabel", null);
    }

    @Override
    public Bundle preDrawCanvas(CapturePP cap) {
        Bundle ret = super.preDrawCanvas(cap);

        // Calculate label points
        Map<String, Object> labels = getLabels();
        if (labels == null || labels.isEmpty())
            return ret;
        PointF[] p = (PointF[]) ret.getSerializable("points");
        if (p == null || p.length < 2)
            return ret;
        boolean closed = (getStyle() & Polyline.STYLE_CLOSED_MASK) > 0;
        PointF[] labelPoints = new PointF[labels.size()];
        int i = -1;
        float width = cap.getWidth(), height = cap.getHeight();
        RectF clipRect = new RectF(0, 0, width, height);
        for (Object o : labels.values()) {
            i++;
            if (!(o instanceof Map))
                continue;
            try {
                Map<String, Object> l = (Map<String, Object>) o;
                int index = (Integer) l.get("segment");
                if (index < 0 || index >= p.length
                        || index == p.length - 1 && !closed)
                    continue;
                PointF s = p[index];
                PointF e = index == p.length - 1 ? p[0] : p[index + 1];
                PointA c = new PointA((s.x + e.x) / 2, (s.y + e.y) / 2,
                        CanvasHelper.angleTo(s, e) + 90);

                boolean inside = c.x > 0 && c.x < width && c.y > 0
                        && c.y < height;
                if (!inside) {
                    PointF[] ip = GLMapItem._getIntersectionPoint(
                            clipRect, s, e);
                    if (ip[0] != null || ip[1] != null) {
                        if (ip[0] != null && ip[1] != null) {
                            c.set((ip[0].x + ip[1].x) / 2.0f,
                                    (ip[0].y + ip[1].y) / 2.0f);
                        } else {
                            PointF origin = s;
                            if (0 < e.x && e.x < width && 0 < e.y
                                    && e.y < height)
                                origin = e;

                            if (ip[0] != null)
                                c.set((ip[0].x + origin.x) / 2.0f,
                                        (ip[0].y + origin.y) / 2.0f);
                            else
                                c.set((ip[1].x + origin.x) / 2.0f,
                                        (ip[1].y + origin.y) / 2.0f);
                        }
                    }
                }
                labelPoints[i] = c;
            } catch (Exception ignore) {
            }
        }
        ret.putSerializable("labelPoints", labelPoints);
        return ret;
    }

    @Override
    public void drawCanvas(CapturePP cap, Bundle data) {
        Paint paint = cap.getPaint();
        if ((basicLineStyle & BASIC_LINE_STYLE_DASHED) > 0)
            paint.setPathEffect(cap.getDashed());
        else if ((basicLineStyle & BASIC_LINE_STYLE_DOTTED) > 0)
            paint.setPathEffect(cap.getDotted());
        super.drawCanvas(cap, data);

        // Draw line labels
        Map<String, Object> labels = getLabels();
        if (labels != null && !labels.isEmpty()) {
            PointF[] labelPoints = (PointF[]) data
                    .getSerializable("labelPoints");
            if (labelPoints == null)
                return;
            int l = -1;
            for (Object o : labels.values()) {
                l++;
                if (labelPoints[l] == null || !(o instanceof Map))
                    continue;
                try {
                    Map<String, Object> lMap = (Map<String, Object>) o;
                    String text = String.valueOf(lMap.get("text"));
                    if (FileSystemUtils.isEmpty(text))
                        continue;
                    cap.drawLabel(text, labelPoints[l]);
                } catch (Exception ignore) {
                }
            }
        }

        // Floating line label
        String label = getLineLabel();
        if (!FileSystemUtils.isEmpty(label) && hasMetaValue("labels_on")) {
            PointF[] p = (PointF[]) data.getSerializable("points");
            if (p == null || p.length < 2)
                return;
            // Find the best label line
            int bestRank = 0;
            PointF labelPos = null;
            for (int i = 0; i < p.length - 1; i++) {
                int rank = 0;
                rank += cap.inside(p[i]) ? 1 : 0;
                rank += cap.inside(p[i + 1]) ? 1 : 0;
                if (rank > bestRank && cap.shouldDrawLabel(label,
                        new PointF[] {
                                p[i], p[i + 1]
                        })) {
                    bestRank = rank;
                    labelPos = new PointA(
                            (p[i].x + p[i + 1].x) / 2,
                            (p[i].y + p[i + 1].y) / 2,
                            (float) Math.toDegrees(Math.atan2(p[i + 1].y
                                    - p[i].y, p[i + 1].x - p[i].x)));
                    if (rank == 2)
                        break;
                }
            }
            if (labelPos != null)
                cap.drawLabel(label, labelPos);
        }
    }

    /**
     * Convenience method for setting the minimum label render resolution.
     * @param d the double value set in resolution meters per pixel.
     */
    public void setMinLabelRenderResolution(final double d) {
        this.setMetaDouble("minLabelRenderResolution", d);
    }

    /**
     * Convenience method for setting the maximum label render resolution.
     * @param d the double value set in resolution meters per pixel.
     */
    public void setMaxLabelRenderResolution(final double d) {
        this.setMetaDouble("maxLabelRenderResolution", d);
    }

    /**
     * Convenience method for setting the minimum label render resolution.
     * @param d the double value set in resolution meters per pixel.
     */
    public void setMinRenderResolution(final double d) {
        this.setMetaDouble("minLineRenderResolution", d);
    }

    /**
     * Convenience method for setting the maximum label render resolution.
     * This should be used instead of the original "minRenderScale" because
     * scale is a value that is device dependent whereas resolution is device
     * independent.
     * @param d the double value set in resolution meters per pixel.
     */
    public void setMaxRenderResolution(final double d) {
        this.setMetaDouble("maxLineRenderResolution", d);
    }
}
