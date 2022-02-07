
package com.atakmap.android.imagecapture;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.atakmap.android.gridlines.GridLinesMapComponent;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapItem.OnGroupChangedListener;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.PointMapItem.OnPointChangedListener;
import com.atakmap.android.tilecapture.imagery.ImageryCapturePP;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.Ellipsoid;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MGRSPoint;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.coremap.maps.coords.UTMPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.layer.AbstractLayer;
import com.atakmap.math.MathUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.text.DecimalFormat;

import com.atakmap.coremap.locale.LocaleUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * A customizable MGRS-aligned grid
 */

public class CustomGrid extends AbstractLayer implements Capturable {

    private static final String TAG = "CustomGrid";
    public static final String SPACING_PREF = "grg_grid_spacing";
    public static final String COLOR_PREF = "grg_grid_color";
    public static final String UNITS_PREF = "grg_grid_units";
    public static final String LABELS_PREF = "grg_grid_labels";

    private static final DecimalFormat LABEL_FMT = LocaleUtil
            .getDecimalFormat("00000");

    public static final int FLOOR = 0;
    public static final int ROUND = 1;
    public static final int CEIL = 2;

    private final MapView _mapView;
    private GeoPoint _topLeft, _topRight, _bottomRight, _bottomLeft;
    private Marker _centerMarker;
    private DoubleBuffer _points;
    private final MutableGeoBounds _bounds = new MutableGeoBounds(0, 0, 0, 0);
    private int _pointCount;
    private long _pointPtr;
    private String[] _labels;
    private double _mSpacing = 100;
    private float _strokeWeight = 2f;
    private int _placeRows, _placeCols;
    private int _xLines, _yLines;
    private int _color = Color.WHITE;
    private boolean _showLabels = true;
    private int _precision = 0;
    private boolean _visible = true;
    private final List<OnChangedListener> _listeners = new ArrayList<>();

    /**
     * Construct a custom grid overlay.
     * @param mapView the mapview
     * @param uid the uid
     */
    public CustomGrid(MapView mapView, String uid) {
        super(uid);
        _mapView = mapView;
    }

    /**
     * Sets the four corners of the customized grid.
     * @param corner1 upper left
     * @param corner2 lower right
     */
    public synchronized void setCorners(GeoPointMetaData corner1,
            GeoPointMetaData corner2) {
        alignCorners(corner1, corner2);
    }

    /**
     * Unset the coordinates for the layer.
     */
    public synchronized void clear() {
        setCorners(null, null);
    }

    /**
     * Return true if the coordinates for the layer are valid.
     * @return true if the coordinates are valid.
     */
    public synchronized boolean isValid() {
        return _topLeft != null && _topRight != null
                && _bottomLeft != null && _bottomRight != null;
    }

    @Override
    public synchronized void setVisible(boolean visible) {
        if (_visible != visible) {
            _visible = visible;
            onChanged(false);
        }
        if (_centerMarker != null)
            _centerMarker.setVisible(visible);
    }

    @Override
    public synchronized boolean isVisible() {
        return _visible;
    }

    /**
     * Sets the color of the grid lines used for the Custom grid
     * @param color the color as an integer value where the alpha is ignored.
     */
    public synchronized void setColor(int color) {
        if (Color.alpha(color) == 0)
            color |= 0xFF000000;
        if (_color != color) {
            _color = color;
            onChanged(false);
        }
    }

    /**
     * Returns the color used for the grid lines.   The color will not have any
     * alpha property.
     * @return the color
     */
    public synchronized int getColor() {
        return _color;
    }

    /**
     * Sets the stroke weight of the custom Grid
     * @param strokeWeight the weight
     */
    public void setStrokeWeight(float strokeWeight) {
        _strokeWeight = strokeWeight;
    }

    public float getStrokeWeight() {
        return _strokeWeight;
    }

    public synchronized void setShowLabels(boolean show) {
        if (_showLabels != show) {
            _showLabels = show;
            onChanged(false);
        }
    }

    public synchronized boolean showLabels() {
        return _showLabels;
    }

    public synchronized void setSpacing(double meters) {
        if (Double.compare(meters, _mSpacing) != 0 && meters > 0) {
            _mSpacing = meters;
            _precision = 0;
            onChanged(true);
        }
    }

    /**
     * Return grid spacing
     * @return Spacing between cells in meters
     */
    public synchronized double getSpacing() {
        return _mSpacing;
    }

    public synchronized int getVerticalLineCount() {
        return _xLines;
    }

    public synchronized int getHorizontalLineCount() {
        return _yLines;
    }

    public synchronized int getNumColumns() {
        return _xLines + 1;
    }

    public synchronized int getNumRows() {
        return _yLines + 1;
    }

    public synchronized GeoBounds getBounds() {
        if (!isValid())
            return null;
        _bounds.set(getCorners(), _mapView.isContinuousScrollEnabled());
        return _bounds;
    }

    public synchronized GeoPoint[] getCorners() {
        return new GeoPoint[] {
                _topLeft, _topRight, _bottomRight, _bottomLeft
        };
    }

    public GeoPoint getCenter() {
        GeoBounds bounds = getBounds();
        return bounds != null ? bounds.getCenter(null) : GeoPoint.ZERO_POINT;
    }

    public synchronized Marker getCenterMarker() {
        return _centerMarker;
    }

    /**
     * Define grid extends based on center and size
     * @param c Grid center
     * @param cols Number of grid columns
     * @param rows Number of grid rows
     * @return True if placement successful, false otherwise
     */
    public boolean place(GeoPoint c, int cols, int rows) {
        double s = getSpacing();
        _placeCols = cols;
        _placeRows = rows;
        double hWidth = (cols * s) / 2, hHeight = (rows * s) / 2;

        // Required in case we cross equator and northing doesn't
        // round equally into spacing
        double nGCF = gcf(10000000, s);

        // Calculate top left
        GeoPoint left = GeoCalculations.pointAtDistance(c, 270, hWidth);
        GeoPoint tl = GeoCalculations.pointAtDistance(left, 0, hHeight);
        UTMPoint tl_utm = UTMPoint.fromGeoPoint(tl);
        if (tl_utm.getZoneDescriptor() == null)
            return false;
        tl_utm = new UTMPoint(tl_utm.getZoneDescriptor(),
                round(tl_utm.getEasting(), s, ROUND),
                round(tl_utm.getNorthing(), nGCF, ROUND));
        tl = tl_utm.toGeoPoint();

        // Calculate top right - walk to point while maintaining alignment
        // Less efficient than a single computeDest call, but more accurate
        GeoPoint tr = tl;
        UTMPoint tr_utm = tl_utm;
        for (int i = 1; i <= cols; i++) {
            tr = GeoCalculations.pointAtDistance(tr, 90, s);
            tr_utm = UTMPoint.fromGeoPoint(tr);
            if (tr_utm.getZoneDescriptor() == null)
                return false;
            tr_utm = new UTMPoint(tr_utm.getZoneDescriptor(),
                    round(tr_utm.getEasting(), s, ROUND),
                    round(tr_utm.getNorthing(), nGCF, ROUND));
            tr = tr_utm.toGeoPoint();
        }

        // Calculate bottom right
        GeoPoint br = tr;
        UTMPoint br_utm = tr_utm;
        for (int i = 1; i <= rows; i++) {
            br = GeoCalculations.pointAtDistance(br, 180, s);
            br_utm = UTMPoint.fromGeoPoint(br);
            if (br_utm.getZoneDescriptor() == null)
                return false;
            br_utm = new UTMPoint(br_utm.getZoneDescriptor(),
                    round(br_utm.getEasting(), s, ROUND),
                    round(br_utm.getNorthing(), nGCF, ROUND));
            br = br_utm.toGeoPoint();
        }

        // Calculate bottom left
        GeoPoint bl = tl;
        UTMPoint bl_utm = tl_utm;
        for (int i = 1; i <= rows; i++) {
            bl = GeoCalculations.pointAtDistance(bl, 180, s);
            bl_utm = UTMPoint.fromGeoPoint(bl);
            if (bl_utm.getZoneDescriptor() == null)
                return false;
            bl_utm = new UTMPoint(bl_utm.getZoneDescriptor(),
                    round(bl_utm.getEasting(), s, ROUND),
                    round(bl_utm.getNorthing(), nGCF, ROUND));
            bl = bl_utm.toGeoPoint();
        }

        tr = tr_utm.toGeoPoint();
        bl = bl_utm.toGeoPoint();
        br = br_utm.toGeoPoint();

        // Check that grid isn't wrapping around the world
        if (!_mapView.isContinuousScrollEnabled()
                && (Math.abs(tl.getLongitude() - tr.getLongitude()) > 180
                        || tl.getLongitude() > 180 || tl.getLongitude() < -180
                        || tr.getLongitude() > 180 || tr.getLongitude() < -180
                        || bl.getLongitude() > 180 || bl.getLongitude() < -180
                        || br.getLongitude() > 180 || br.getLongitude() < -180))
            return false;

        updateCorners(tl, tr, bl, br);
        return true;
    }

    public boolean place(GeoPoint c) {
        int rows = _placeRows;
        if (rows <= 0)
            rows = getNumRows();
        int cols = _placeCols;
        if (cols <= 0)
            cols = getNumColumns();
        return rows > 0 && cols > 0 && place(c, cols, rows);
    }

    /**
     * Align corners to MGRS grid
     */
    private boolean alignCorners(GeoPointMetaData corner1,
            GeoPointMetaData corner2) {
        if (corner1 == null || corner2 == null) {
            // Grid cleared
            updateCorners(null, null, null, null);
            return false;
        }
        // Corner 1 is the top-left corner
        // Corner 2 is the bottom-right corner
        GeoBounds bounds = new GeoBounds(corner1.get(), corner2.get());
        double s = getSpacing();

        // Need to compute relative to center or everything is off
        corner1 = GeoPointMetaData
                .wrap(new GeoPoint(bounds.getNorth(), bounds.getWest()));
        corner2 = GeoPointMetaData
                .wrap(new GeoPoint(bounds.getSouth(), bounds.getEast()));

        UTMPoint tl = UTMPoint.fromGeoPoint(corner1.get()), br = UTMPoint
                .fromGeoPoint(corner2.get());

        if (tl.getZoneDescriptor() == null || br.getZoneDescriptor() == null)
            return false;

        tl = new UTMPoint(tl.getZoneDescriptor(),
                round(tl.getEasting(), s, FLOOR),
                round(tl.getNorthing(), s, CEIL));
        br = new UTMPoint(br.getZoneDescriptor(),
                round(br.getEasting(), s, CEIL),
                round(br.getNorthing(), s, FLOOR));
        if (tl.toString().equals(br.toString()))
            br = new UTMPoint(tl.getZoneDescriptor(),
                    tl.getEasting() + s, tl.getNorthing() + s);
        UTMPoint tr = new UTMPoint(br.getZoneDescriptor(),
                br.getEasting(), tl.getNorthing());
        UTMPoint bl = new UTMPoint(tl.getZoneDescriptor(),
                tl.getEasting(), br.getNorthing());
        _placeCols = _placeRows = 0;
        updateCorners(tl.toGeoPoint(), tr.toGeoPoint(),
                bl.toGeoPoint(), br.toGeoPoint());
        return true;
    }

    /**
     * Update corners and center marker
     * To be called after corners are MGRS-aligned
     * @param topLeft Top left corner point
     * @param bottomRight Bottom right corner point
     */
    private synchronized void updateCorners(GeoPoint topLeft,
            GeoPoint topRight,
            GeoPoint bottomLeft, GeoPoint bottomRight) {
        _topLeft = topLeft;
        _topRight = topRight;
        _bottomLeft = bottomLeft;
        _bottomRight = bottomRight;
        if (!isValid()) {
            Marker center = _centerMarker;
            _centerMarker = null;
            if (center != null)
                center.removeFromGroup();
            onChanged(true);
            return;
        } else {
            if (_centerMarker == null) {
                _centerMarker = new Marker(getName() + "_marker");
                _centerMarker.setMetaString("callsign", "MGRS Grid");
                _centerMarker.setMetaBoolean("addToObjList", false);
                _centerMarker.setMovable(true);
                _centerMarker.setMetaBoolean("removable", true);
                _centerMarker.setMetaString("iconUri", "icons/grid_center.png");
                _centerMarker.setMetaString("menu", "menus/grid_center.xml");
                _centerMarker
                        .addOnPointChangedListener(
                                new OnPointChangedListener() {
                                    @Override
                                    public void onPointChanged(
                                            PointMapItem item) {
                                        CustomGrid grid = GridLinesMapComponent
                                                .getCustomGrid();
                                        if (grid != null
                                                && item.getGroup() != null) {
                                            GeoPoint gp = item.getPoint();
                                            GeoPoint gridCenter = grid
                                                    .getCenter();
                                            if (gridCenter != null
                                                    && gp.distanceTo(
                                                            gridCenter) > 0.1) {
                                                if (!grid.place(gp)) {
                                                    Toast.makeText(MapView
                                                            .getMapView()
                                                            .getContext(),
                                                            R.string.grid_fail,
                                                            Toast.LENGTH_LONG)
                                                            .show();
                                                    item.setPoint(gridCenter);
                                                }
                                            }
                                        }
                                    }
                                });
                _centerMarker
                        .addOnGroupChangedListener(
                                new OnGroupChangedListener() {
                                    @Override
                                    public void onItemAdded(MapItem item,
                                            MapGroup group) {
                                    }

                                    @Override
                                    public void onItemRemoved(MapItem item,
                                            MapGroup group) {
                                        CustomGrid grid = GridLinesMapComponent
                                                .getCustomGrid();
                                        if (grid != null)
                                            grid.clear();
                                    }
                                });
                MapView.getMapView().getRootGroup().addItem(_centerMarker);
            }
            _centerMarker.setPoint(getCenter());
        }

        onChanged(true);
        getPointBuffer();
    }

    /**
     * Calculate the coordinates for each grid line segment
     * Order:
     * [1 - 4] = bounding corners
     * [5 - Nx] = vertical lines: line1p1, line1p2, ..., lineNp1, lineNp2
     * [Nx + 1, Ny] = horizontal lines: same idea as above
     * @return Double buffer containing the points
     */
    synchronized DoubleBuffer getPointBuffer() {
        if (!isValid() || !isVisible())
            return null;
        if (_points == null) {
            // Update attributes based on preferences
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(
                            MapView.getMapView().getContext());
            setColor(prefs.getInt(CustomGrid.COLOR_PREF, Color.WHITE));
            setSpacing(prefs.getFloat(CustomGrid.SPACING_PREF, 100f));
            setShowLabels(prefs.getBoolean(CustomGrid.LABELS_PREF, true));

            UTMPoint tl = UTMPoint.fromGeoPoint(_topLeft);
            UTMPoint tr = UTMPoint.fromGeoPoint(_topRight);
            UTMPoint bl = UTMPoint.fromGeoPoint(_bottomLeft);
            UTMPoint br = UTMPoint.fromGeoPoint(_bottomRight);

            float[] ra = new float[3];
            Location.distanceBetween(_topLeft.getLatitude(),
                    _topLeft.getLongitude(), _topLeft.getLatitude(),
                    _bottomRight.getLongitude(), ra);
            double xDist = ra[0];
            Location.distanceBetween(_topLeft.getLatitude(),
                    _topLeft.getLongitude(), _bottomRight.getLatitude(),
                    _topLeft.getLongitude(), ra);
            double yDist = -ra[0];

            xDist = round(xDist, _mSpacing, ROUND);
            if (Double.compare(xDist, 0) == 0)
                xDist = _mSpacing;
            yDist = round(yDist, _mSpacing, ROUND);
            if (Double.compare(yDist, 0) == 0)
                yDist = _mSpacing;

            _xLines = Math.max(0, Math.abs((int) (xDist / _mSpacing)) - 1);
            _yLines = Math.max(0, Math.abs((int) (yDist / _mSpacing)) - 1);
            _pointCount = 5 + _xLines * 2 + _yLines * 2;
            ByteBuffer buf = com.atakmap.lang.Unsafe.allocateDirect(
                    8 * 3 * _pointCount);
            buf.order(ByteOrder.nativeOrder());
            _points = buf.asDoubleBuffer();
            _pointPtr = Unsafe.getBufferPointer(_points);

            // Top-left corner
            addPoint(tl.toGeoPoint());
            addPoint(tr.toGeoPoint());
            addPoint(br.toGeoPoint());
            addPoint(bl.toGeoPoint());
            addPoint(tl.toGeoPoint());

            // MGRS label for each line + 4 for corners
            _labels = new String[_xLines + _yLines + 4];
            int l = 0;
            // Organize corners: vertical set then horizontal set
            _labels[l++] = getLabel(tl, true);
            _labels[_xLines + 1] = getLabel(tr, true);
            _labels[_xLines + 2] = getLabel(tl, false);
            _labels[_labels.length - 1] = getLabel(bl, false);

            // Vertical lines
            GeoPoint lastTop = _topLeft;
            GeoPoint lastBot = _bottomLeft;
            for (int x = 1; x <= _xLines; x++) {
                // Top line point
                GeoPoint top = GeoCalculations.pointAtDistance(lastTop, 90,
                        _mSpacing);
                UTMPoint tp = UTMPoint.fromGeoPoint(top);
                tp = new UTMPoint(tp.getZoneDescriptor(),
                        round(tp.getEasting(), _mSpacing, ROUND),
                        tl.getNorthing());
                top = tp.toGeoPoint();

                // Bottom line point
                GeoPoint bot = GeoCalculations.pointAtDistance(lastBot, 90,
                        _mSpacing);
                UTMPoint bp = UTMPoint.fromGeoPoint(bot);
                bp = new UTMPoint(bp.getZoneDescriptor(),
                        round(bp.getEasting(), _mSpacing, ROUND),
                        br.getNorthing());
                bot = bp.toGeoPoint();

                if (Double.compare(tp.getEasting(), bp.getEasting()) != 0 &&
                        FileSystemUtils.isEquals(tp.getZoneDescriptor(),
                                bp.getZoneDescriptor())) {
                    // Eastings have diverged - check which is closer to the
                    // last point and assume that's the more accurate value
                    double topDist = top.distanceTo(lastTop);
                    double botDist = bot.distanceTo(lastBot);
                    if (Math.abs(topDist - _mSpacing) < Math
                            .abs(botDist - _mSpacing)) {
                        bp = new UTMPoint(bp.getZoneDescriptor(),
                                tp.getEasting(), bp.getNorthing());
                        bot = bp.toGeoPoint();
                    } else {
                        tp = new UTMPoint(tp.getZoneDescriptor(),
                                bp.getEasting(), tp.getNorthing());
                        top = tp.toGeoPoint();
                    }
                }

                _labels[l++] = getLabel(tp, true);
                addPoint(top);
                addPoint(bot);
                lastTop = top;
                lastBot = bot;
            }
            // Horizontal lines
            l += 2;
            lastTop = _topLeft;
            lastBot = _topRight;
            double nGCF = gcf(10000000, _mSpacing);
            for (int y = 1; y <= _yLines; y++) {
                // Top line point
                lastTop = GeoCalculations.pointAtDistance(lastTop, 180,
                        _mSpacing);
                UTMPoint lp = UTMPoint.fromGeoPoint(lastTop);
                lp = new UTMPoint(lp.getZoneDescriptor(),
                        tl.getEasting(),
                        round(lp.getNorthing(), nGCF, ROUND));
                lastTop = lp.toGeoPoint();
                addPoint(lastTop);
                _labels[l++] = getLabel(lp, false);
                // Right line point
                lastBot = GeoCalculations.pointAtDistance(lastBot, 180,
                        _mSpacing);
                UTMPoint rp = UTMPoint.fromGeoPoint(lastBot);
                rp = new UTMPoint(rp.getZoneDescriptor(),
                        br.getEasting(),
                        round(rp.getNorthing(), nGCF, ROUND));
                lastBot = rp.toGeoPoint();
                addPoint(lastBot);
            }

            _points.flip();
        }
        return _points;
    }

    // Should only be called within above function
    private void addPoint(GeoPoint p) {
        _points.put(p.getLongitude());
        _points.put(p.getLatitude());
        _points.put(0); // To be populated by renderer
    }

    /**
     * Given a label index, return its corresponding position index
     * @param i Label index
     * @return Position index
     */
    synchronized int getLabelPositionIndex(int i) {
        if (i == 0 || i == _xLines + 2)
            // Top-left corner
            return 0;
        else if (i == _xLines + 1)
            // Top-right corner
            return 1;
        else if (i == _labels.length - 1)
            // Bottom-left corner
            return 3;
        else if (i > 0 && i <= _xLines)
            // Top row
            return 3 + (i * 2);
        else
            // Left column
            return 3 + (_xLines * 2) + (i - (_xLines + 2))
                    * 2;
    }

    /**
     * Test whether this grid is drawing it's labels
     * This is different than showLabels since it also depends on
     * the map zoom level
     * @param lat Draw latitude
     * @param mapRes Map resolution
     * @return True if drawing labels
     */
    public synchronized boolean isDrawingLabels(double lat, double mapRes) {
        if (!_showLabels || !isValid())
            return false;

        int precision = getLabelPrecision();
        double mercatorscale = Math
                .cos(lat / ConversionFactors.DEGREES_TO_RADIANS);
        if (mercatorscale < 0.0001)
            mercatorscale = 0.0001;
        double metersPerPixel = mapRes * mercatorscale;
        return ((metersPerPixel * precision) / _mSpacing) <= 0.05;
    }

    /**
     * Get the appropriate label value precision based on spacing
     * @return Value precision (i.e. 100m spacing = first 3 digits)
     */
    public synchronized int getLabelPrecision() {
        return _precision == 0 ? MathUtils.clamp(5 - (int) Math.floor(
                Math.log10(_mSpacing)), 1, 5) : _precision;
    }

    /**
     * Set the label precision manually
     * @param precision Precision from 0 to 5 (0 means use automatic)
     */
    public synchronized void setLabelPrecision(int precision) {
        if (_precision != precision) {
            _precision = MathUtils.clamp(precision, 0, 5);
            onChanged(false);
        }
    }

    private synchronized GeoPoint getPoint(int pointIdx) {
        return new GeoPoint(
                Unsafe.getDouble(_pointPtr + pointIdx * 24 + 8),
                Unsafe.getDouble(_pointPtr + pointIdx * 24));
    }

    /**
     * Get the list of grid labels (horizontal first then vertical)
     * @param trunc Truncate according to the default precision
     * @return List of labels
     */
    public synchronized String[] getLabels(boolean trunc) {
        if (_labels == null)
            return new String[0];
        String[] ret = new String[_labels.length];
        int precision = getLabelPrecision();
        double exp = Math.pow(10, 5 - precision);
        for (int i = 0; i < _labels.length; i++) {
            if (_labels[i] == null) {
                _labels[i] = "";
                ret[i] = "";
                continue;
            }
            if (trunc) {
                try {
                    int meters = Integer.parseInt(_labels[i]);
                    meters = (int) round(meters, exp, ROUND);
                    if (meters >= 100000)
                        meters -= 100000;
                    ret[i] = LABEL_FMT.format(meters).substring(0, precision);
                } catch (Exception e) {
                    ret[i] = _labels[i];
                }
            } else
                ret[i] = _labels[i];
        }
        return ret;
    }

    @Override
    public synchronized Bundle preDrawCanvas(CapturePP cap) {
        if (!isValid() || !isVisible())
            return null;
        Bundle data = new Bundle();

        // Grid lines
        PointF[] points = new PointF[5];

        // Bounding box
        int ind = 0, i = 0;
        for (; i < 5; i++)
            points[i] = cap.forward(getPoint(i));
        data.putSerializable("gridLine" + (ind++), points);

        // Vertical/horizontal lines
        int p = 0;
        for (i = 5; i < _pointCount; i++) {
            if (p == 0)
                points = new PointF[2];
            points[p++] = cap.forward(getPoint(i));
            if (p == 2) {
                data.putSerializable("gridLine" + (ind++), points);
                p = 0;
            }
        }
        data.putInt("gridLineCount", ind);

        if (isDrawingLabels(cap.getBounds().getSouth(),
                cap.getMapResolution())) {
            // Label points
            PointF[] lp = new PointF[_labels.length];
            for (i = 0; i < _labels.length; i++) {
                lp[i] = cap.forward(getPoint(getLabelPositionIndex(i)));
                boolean top = i < getVerticalLineCount() + 2;
                if (!top)
                    lp[i].y -= 2;
            }
            data.putSerializable("labelPoints", lp);
        }
        return data;
    }

    @Override
    public synchronized void drawCanvas(CapturePP cap, Bundle data) {
        if (!isValid() || !isVisible())
            return;
        Canvas can = cap.getCanvas();
        Paint paint = cap.getPaint();
        Path path = cap.getPath();
        float dr = cap.getResolution();
        float lineWeight = cap.getLineWeight();

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(cap.getThemeColor(getColor()));
        paint.setStrokeWidth(getStrokeWeight() * lineWeight);

        // Draw grid lines
        for (int i = 0; i < data.getInt("gridLineCount", 0); i++) {
            PointF[] line = (PointF[]) data.getSerializable("gridLine" + i);
            if (line == null || line.length < 2 ||
                    !CanvasHelper.intersecting(cap, line))
                continue;
            path.moveTo(dr * line[0].x, dr * line[0].y);
            for (PointF lp : line)
                path.lineTo(dr * lp.x, dr * lp.y);
            can.drawPath(path, paint);
            path.reset();
        }

        // Draw grid labels
        PointF[] lp = (PointF[]) data
                .getSerializable("labelPoints");
        if (lp == null)
            return;
        String[] labels = getLabels(true);
        if (labels == null || labels.length != lp.length)
            return;

        // Make sure label is visible over black background
        int labelColor = (getColor() & 0xFFFFFF) + 0xFF000000;
        float[] hsv = new float[3];
        Color.colorToHSV(labelColor, hsv);
        if (hsv[2] < 0.75f) {
            hsv[2] = 0.75f;
            labelColor = Color.HSVToColor(hsv);
        }
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] == null || lp[i] == null)
                continue;
            /*cap.drawLabel(labels[i], labelPoints[i],
                    i < getVerticalLineCount() + 2
                            ? ImageCapturePP.ALIGN_X_CENTER
                            | ImageCapturePP.ALIGN_BOTTOM
                            : ImageCapturePP.ALIGN_RIGHT
                            | ImageCapturePP.ALIGN_TOP, getColor(), false);*/

            boolean inside = lp[i].x > 0 && lp[i].x < cap.getWidth()
                    && lp[i].y > 0 && lp[i].y < cap.getHeight();
            int ind = getLabelPositionIndex(i);
            if (!inside) {
                PointF[] line;
                if (ind < 5) {
                    line = (PointF[]) data.getSerializable("gridLine0");
                    int ind2 = ind + 1;
                    if (ind == 0 && i == 0)
                        ind2 = 3;
                    else if (ind == 3)
                        ind2 = 2;
                    if (line != null)
                        line = new PointF[] {
                                line[ind], line[ind2]
                        };
                } else
                    line = (PointF[]) data.getSerializable("gridLine"
                            + (((ind - 5) / 2) + 1));
                if (line != null) {
                    if (!CanvasHelper.intersecting(cap, line))
                        continue;
                    // Fix label position
                    double m = (line[1].y - line[0].y)
                            / (line[1].x - line[0].x);
                    double b = line[0].y - (m * line[0].x);

                    float x = cap.getWidth(), y = cap.getHeight();
                    if (i < getVerticalLineCount() + 2)
                        lp[i] = new PointF(CanvasHelper.validate(
                                (float) ((y - b) / m), lp[i].x), y);
                    else
                        lp[i] = new PointF(x, CanvasHelper.validate(
                                (float) (b + x * m), lp[i].y));
                    CanvasHelper.clampToLine(lp[i], line);
                    /*Log.d(TAG, String.format(LocaleUtil.getCurrent(),
                            "%s (%d): Original = [%.1f, %.1f] -> [%.1f, %.1f], y = %.1fx + %.1f, Point = %.1f, %.1f",
                            _labels[i], i, line[0].x, line[0].y, line[1].x,
                            line[1].y, m, b, lp[i].x, lp[i].y));*/
                }
            }

            cap.drawLabel(labels[i], lp[i],
                    i < getVerticalLineCount() + 2
                            ? TextRect.ALIGN_X_CENTER
                                    | TextRect.ALIGN_BOTTOM
                            : TextRect.ALIGN_RIGHT
                                    | (inside ? TextRect.ALIGN_TOP
                                            : TextRect.ALIGN_Y_CENTER),
                    labelColor, true);
        }
    }

    /**
     * Draw fitted grid to canvas
     * @param cap Capture post-processor
     */
    public synchronized void drawFittedGrid(ImageryCapturePP cap) {
        if (!isValid() || !isVisible())
            return;
        Canvas can = cap.getCanvas();
        Paint paint = cap.getPaint();
        Path path = cap.getPath();
        float lineWeight = cap.getLineWeight();

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(cap.getThemeColor(getColor()));
        paint.setStrokeWidth(getStrokeWeight() * lineWeight);

        GeoBounds cb = cap.getBounds();
        GeoPoint bl = new GeoPoint(cb.getSouth(), cb.getWest());
        GeoPoint br = new GeoPoint(cb.getSouth(), cb.getEast());
        GeoPoint tl = new GeoPoint(cb.getNorth(), cb.getWest());
        double hRange = bl.distanceTo(br);
        double vRange = bl.distanceTo(tl);

        // Draw grid lines
        double rangeX = hRange / getSpacing();
        double rangeY = vRange / getSpacing();
        int colCount = (int) Math.ceil(rangeX);
        int rowCount = (int) Math.ceil(rangeY);
        float celSize = (float) (cap.getWidth() / rangeX);
        for (int i = 0; i <= colCount; i++) {
            float x = Math.min(i * celSize, cap.getWidth());
            path.moveTo(x, 0);
            path.lineTo(x, cap.getHeight());
        }
        for (int i = 0; i <= rowCount; i++) {
            float y = Math.min(i * celSize, cap.getHeight());
            path.moveTo(0, y);
            path.lineTo(cap.getWidth(), y);
        }
        can.drawPath(path, paint);
        path.reset();
    }

    public static double round(double val, double nearest, int roundType) {
        double sign = Math.signum(val);
        if (Double.compare(sign, 0.0) == 0)
            return 0;
        val = Math.abs(val) / nearest;
        switch (roundType) {
            case FLOOR:
                val = Math.floor(val);
                break;
            case ROUND:
                val = Math.round(val);
                break;
            case CEIL:
                val = Math.ceil(val);
                break;
        }
        return sign * val * nearest;
    }

    public static double gcf(double val1, double val2) {
        return (Double.compare(val1, 0) == 0 || Double.compare(val2, 0) == 0)
                ? val1 + val2
                : gcf(val2, val1 % val2);
    }

    private static String getLabel(MGRSPoint mgrs, boolean east) {
        return east ? mgrs.getEastingDescriptor()
                : mgrs.getNorthingDescriptor();
    }

    private static String getLabel(UTMPoint utm, boolean east) {
        return getLabel(new MGRSPoint(utm), east);
    }

    /**
     * Get the easting or northing of a point
     * @param lat Latitude of point
     * @param lon Longitude of point
     * @param east True to return easting, false for northing
     * @return The easting/northing descriptor (5-digit number)
     */
    private static String getLabel(double lat, double lon, boolean east) {
        return getLabel(MGRSPoint.fromLatLng(Ellipsoid.WGS_84,
                lat, lon, null), east);
    }

    public synchronized void onChanged(boolean recalcGrid) {
        if (recalcGrid)
            _points = null;
        for (OnChangedListener ocl : _listeners)
            ocl.onChanged(this);
    }

    public synchronized void addOnChangedListener(OnChangedListener ocl) {
        if (!_listeners.contains(ocl))
            _listeners.add(ocl);
    }

    public synchronized void removeOnChangedListener(OnChangedListener ocl) {
        _listeners.remove(ocl);
    }

    public interface OnChangedListener {
        void onChanged(CustomGrid grid);
    }
}
