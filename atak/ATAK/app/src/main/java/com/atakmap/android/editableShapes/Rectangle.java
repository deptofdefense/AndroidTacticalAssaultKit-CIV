
package com.atakmap.android.editableShapes;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;

import com.atakmap.android.drawing.DrawingPreferences;
import com.atakmap.android.drawing.mapItems.DrawingRectangle;
import com.atakmap.android.importexport.handlers.ParentMapItem;
import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.Association;
import com.atakmap.android.maps.AssociationSet;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.preference.UnitPreferences;
import com.atakmap.android.util.EditAction;
import com.atakmap.android.util.Undoable;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;

import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.ECEF;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;

import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.coremap.maps.coords.MutableUTMPoint;

import com.atakmap.coremap.maps.coords.Vector3D;
import com.atakmap.map.elevation.ElevationManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * An editable rectangle that can be built with 2P + D input
 * The rectangle itself is made of anchor markers, association lines, and
 * a shape fill
 *
 * See {@link DrawingRectangle} for the default drawing shape implementation
 */
public abstract class Rectangle extends AssociationSet
        implements AnchoredMapItem,
        ParentMapItem, OnSharedPreferenceChangeListener,
        MapItem.OnGroupChangedListener, PointMapItem.OnPointChangedListener {

    private static final double DEFAULT_STROKE_WEIGHT = 3.0d;
    private static final double EDITABLE_STROKE_WEIGHT = 4.0d;
    private static final double Z_ORDER_FILL = -0.01;
    private static final double Z_ORDER_LINES = -0.02;
    private static final double Z_ORDER_MARKERS = -0.03;

    private double preEditStrokeWeight = DEFAULT_STROKE_WEIGHT;

    private boolean _editable, _mirrorWidth;
    private int _ignore;
    private final Object _ignoreLock = new Object();
    private final ArrayList<PointMapItem> _points;
    private final ArrayList<Association> _lines;
    private double _avgAltitude;
    private double _width, _length;
    private Polyline _filledShape;
    private Marker _center;
    private final MapGroup _childMapGroup;
    private int _color, _fillColor;
    private boolean _tacticalOverlay;
    private boolean _showLines = true;
    private final UnitPreferences _unitPrefs;
    private boolean _ignoreCenterChange;
    protected final MutableGeoBounds _bounds = new MutableGeoBounds(0, 0, 0, 0);

    /************************ CONSTRUCTORS ****************************/

    protected Rectangle(MapGroup mapGroup, String uid) {
        super(uid);
        _points = new ArrayList<>(4);
        _lines = new ArrayList<>(4);
        _childMapGroup = mapGroup;
        _childMapGroup.setMetaString("shapeUID", uid);
        MapView mv = MapView.getMapView();
        _unitPrefs = new UnitPreferences(mv);
        this.setType(getCotType());
        this.setTitle(_childMapGroup.getFriendlyName());
        this.setMetaBoolean("editable", true);
        setMovable(true);
        DrawingPreferences drawPrefs = new DrawingPreferences(mv);
        this.setColor(drawPrefs.getShapeColor());
        this.setFillColor(drawPrefs.getFillColor());
        this.setStrokeWeight(drawPrefs.getStrokeWeight());
        this.setRadialMenu(getMenuPath());
        this.setMetaString("iconUri", "asset://icons/rectangle.png");
        this.setStyle(Shape.STYLE_STROKE_MASK | Shape.STYLE_FILLED_MASK
                | Polyline.STYLE_CLOSED_MASK);
    }

    /**
     * Creates a Rectangle with the points in counter-clockwise direction. (Note: this constructor
     * expects the points to already be in a GeoRectangle form).
     * 
     * @param childGroup Child MapGroup for the {@link Rectangle}
     * @param p0 First corner, in counter-clockwise direction.
     * @param p1 Second corner, in counter-clockwise direction.
     * @param p2 Third corner, in counter-clockwise direction.
     * @param p3 Fourth corner, in counter-clockwise direction.
     */
    public Rectangle(MapGroup childGroup, GeoPointMetaData p0,
            GeoPointMetaData p1, GeoPointMetaData p2,
            GeoPointMetaData p3, String uid) {
        this(childGroup, uid);
        this._createChildItems(p0, p1, p2, p3);
        this._initDimensions();
        this.setEditable(false);
        this.setMetaString("iconUri", "asset://icons/rectangle.png");
    }

    /**
     * Creates a Rectangle with the points in counter-clockwise direction. (Note: this constructor
     * expects the points to already be in a GeoRectangle form).
     * 
     * @param childGroup Child MapGroup for the {@link Rectangle}
     * @param uid UID for this Rectangle
     * @param p0 First corner, in counter-clockwise direction.
     * @param p1 Second corner, in counter-clockwise direction.
     * @param p2 Third corner, in counter-clockwise direction.
     * @param p3 Fourth corner, in counter-clockwise direction.
     */
    public Rectangle(MapGroup childGroup, String uid, GeoPointMetaData p0,
            GeoPointMetaData p1,
            GeoPointMetaData p2,
            GeoPointMetaData p3) {
        this(childGroup, p0, p1, p2, p3, uid);
    }

    /**
     * Creates a Rectangle with the points in counter-clockwise direction. (Note: this constructor
     * expects the points to already be in a GeoRectangular form).
     * 
     * @param childGroup Child MapGroup for the {@link Rectangle}
     * @param points GeoPoint array of the corners of the {@link Rectangle}
     */
    public Rectangle(MapGroup childGroup, GeoPointMetaData[] points,
            String uid) {
        this(childGroup, points[0], points[1], points[2], points[3], uid);
    }

    /**
     * Set rectangle point while maintaining user-entered altitude
     *
     * @param pmi Point map item to change
     * @param gp New point
     */
    public void setPoint(PointMapItem pmi, GeoPointMetaData gp) {
        if (pmi == null || gp == null)
            return;
        final GeoPoint p = gp.get();
        if (Double.isNaN(p.getLatitude()) || Double.isNaN(p.getLongitude()))
            return;
        GeoPointMetaData op = pmi.getGeoPointMetaData();
        if (op != null
                && op.getAltitudeSource().equals(GeoPointMetaData.USER)) {
            gp.set(new GeoPoint(gp.get().getLatitude(), gp.get().getLongitude(),
                    op.get().getAltitude()))
                    .setGeoPointSource(GeoPointMetaData.USER)
                    .setAltitudeSource(GeoPointMetaData.USER);
        }
        pmi.setPoint(gp);
    }

    /**
     * Method for setting a point without triggering the side/corner listeners
     * Should only be called internally
     *
     * @param pmi Rectangle marker
     * @param gp New point
     */
    protected void setPointNoRecalc(PointMapItem pmi, GeoPointMetaData gp) {
        int index = _points.indexOf(pmi);
        if (index == -1) {
            setPoint(pmi, gp);
            return;
        }
        synchronized (_ignoreLock) {
            _ignore++;
            setPoint(pmi, gp);
        }
    }

    /**
     * Move a rectangle by its center anchor
     *
     * @param oldPoint Old center point
     * @param newPoint New center point
     * @param manualEntry True if this change was triggered by manual
     *                    coordinate entry. If so then the center elevation
     *                    will be modified regardless of source.
     */
    public void move(GeoPointMetaData oldPoint, GeoPointMetaData newPoint,
            boolean manualEntry) {
        synchronized (_ignoreLock) {
            double[] da = new double[8];
            int i;
            GeoPointMetaData[] p = this.getMetaDataPoints();
            GeoPointMetaData[] p_copy = new GeoPointMetaData[] {
                    p[0], p[1], p[2], p[3]
            };
            for (i = 0; i < 4; i++) {
                da[i * 2] = GeoCalculations.distanceTo(
                        oldPoint.get(),
                        p[i].get());
                da[(i * 2) + 1] = GeoCalculations.bearingTo(
                        oldPoint.get(),
                        p[i].get());
                // i += 2;
            }
            _ignore += 8; // changing 8 points
            for (int j = 0; j < 4; j++) {
                GeoPointMetaData n = GeoPointMetaData.wrap(
                        GeoCalculations.pointAtDistance(newPoint.get(),
                                da[j * 2 + 1], da[j * 2]),
                        GeoPointMetaData.CALCULATED,
                        GeoPointMetaData.CALCULATED);
                setPoint(getPointAt(j), n);
            }
            // update the middle markers
            for (Association a : _lines)
                setPoint(a.getMarker(), _getActualCenter(a));

            // Update the center marker
            PointMapItem pmi = getAnchorItem();
            if (pmi != null) {
                _ignoreCenterChange = true;
                if (manualEntry)
                    pmi.setPoint(newPoint);
                else
                    setPoint(pmi, newPoint);
                _ignoreCenterChange = false;
            }

            this.onPointsChanged();
            this.onMoved(p_copy, p);
        }
    }

    public void move(GeoPointMetaData oldPoint, GeoPointMetaData newPoint) {
        move(oldPoint, newPoint, false);
    }

    @Override
    public GeoPointMetaData getCenter() {
        if (_center != null) {
            return _center.getGeoPointMetaData();
        } else {
            return computeCenter();
        }
    }

    /**
     * For the Rectangle, the notion of a center is fictional.   Based on the center point calculation,
     * derive obtain the actual altitude based on the location.  
     * XXX: Verify with Josh the correct behavior: average vs actual.
     */
    private GeoPointMetaData computeCenter() {
        GeoPointMetaData p = GeoPointMetaData.wrap(
                GeoCalculations.computeAverage(
                        this.getPoints(), wrap180()),
                GeoPointMetaData.CALCULATED, GeoPointMetaData.CALCULATED);
        return fillAltitude(p);
    }

    /**
     * Given a GeoPoint, construct a new GeoPoint with an altitude.
     */
    static private GeoPointMetaData fillAltitude(final GeoPointMetaData p) {
        return ElevationManager.getElevationMetadata(p.get());
    }

    /**
     * @return Child {@link MapGroup} that contains the Markers, Polyline, and Associations of this
     *         {@link Rectangle}
     */
    @Override
    public MapGroup getChildMapGroup() {
        return _childMapGroup;
    }

    @Override
    public GeoPointMetaData[] getMetaDataPoints() {
        return this.getGeoPoints();
    }

    @Override
    public GeoPoint[] getPoints() {
        return GeoPointMetaData.unwrap(getGeoPoints());
    }

    /**
     * Convienence method for constructing a rectangle based on a center, width, height and angle.
     * @param center  the center point to use for all of the calculations
     * @param width the width of the rectangle (must be 0 or greater)
     * @param height the height (depth really) of the rectangle (must be 0 or greater)
     * @param angle  the angle of rotation around the center
     * @return an array of length 4 representing each of the 4 corners or null if the values passed in 
     * where not valid.
     */
    public static GeoPointMetaData[] computeCorners(final GeoPoint center,
            final double width, final double height, final double angle) {
        if (center != null && height >= 0 && width >= 0) {

            final GeoPointMetaData[] corners = new GeoPointMetaData[4];

            final double shalfHeight = height / 2d;
            final double shalfWidth = width / 2d;

            final double[][] xycoords = {
                    {
                            shalfHeight, shalfWidth
                    },
                    {
                            -shalfHeight, shalfWidth
                    },
                    {
                            -shalfHeight, -shalfWidth
                    },
                    {
                            shalfHeight, -shalfWidth
                    }
            };

            final double angRad = -Math.toRadians(angle);

            final double angRad_cos = Math.cos(angRad);
            final double angRad_sin = Math.sin(angRad);

            final MutableUTMPoint centerUTM = new MutableUTMPoint(
                    center.getLatitude(), center.getLongitude());

            for (int i = 0; i < xycoords.length; i++) {

                double halfWidth = angRad_cos * xycoords[i][1] - angRad_sin
                        * xycoords[i][0];
                double halfHeight = angRad_sin * xycoords[i][1] + angRad_cos
                        * xycoords[i][0];

                centerUTM.offset(halfWidth, halfHeight);
                double[] cor1ll = centerUTM.toLatLng(null);
                corners[i] = GeoPointMetaData
                        .wrap(new GeoPoint(cor1ll[0], cor1ll[1]));
                centerUTM.offset(-halfWidth, -halfHeight);

            }

            return corners;
        }
        return null;
    }

    /**
     * NOTE: Should only be set when the points have been calculated to be in a rectangular shape,
     * or if coming from a CotEvent (which is the same thing).
     * 
     * @param p0
     * @param p1
     * @param p2
     * @param p3
     */
    public void setPoints(GeoPointMetaData p0, GeoPointMetaData p1,
            GeoPointMetaData p2,
            GeoPointMetaData p3) {
        GeoPointMetaData[] points = new GeoPointMetaData[] {
                p0, p1, p2, p3
        };
        synchronized (_ignoreLock) {
            boolean changed = false;
            for (int i = 0; i < points.length; i++) {
                if (!points[i].equals(_points.get(i).getGeoPointMetaData())) {
                    changed = true;
                    _ignore++;
                    setPoint(_points.get(i), points[i]);
                }
            }
            if (changed) {
                for (Association a : _lines) {
                    GeoPointMetaData p = _getActualCenter(a);
                    if (!p.get().equals(a.getMarker().getPoint())) {
                        _ignore++;
                        setPoint(a.getMarker(), p);
                    }
                }
                _initDimensions();
                this.onPointsChanged();
            }
        }
    }

    /**
     * Set the length of this rectangle
     * @param length Length in meters
     */
    public void setLength(double length) {
        double halfLength = length / 2;
        Association a = getAssociationAt(0);
        double angle = GeoCalculations.bearingTo(a.getFirstItem().getPoint(),
                a.getSecondItem().getPoint());
        GeoPoint topCenterPoint = GeoCalculations.pointAtDistance(
                _center.getPoint(), angle + 90, halfLength);
        GeoPoint bottomCenterPoint = GeoCalculations.pointAtDistance(
                _center.getPoint(), angle - 90, halfLength);

        getAssociationAt(0).getMarker().setPoint(topCenterPoint);
        getAssociationAt(2).getMarker().setPoint(bottomCenterPoint);
    }

    /**
     * Set the width of this rectangle
     * @param width Width in meters
     */
    public void setWidth(double width) {
        double halfWidth = width / 2;
        Association a = getAssociationAt(1);
        double angle = GeoCalculations.bearingTo(a.getFirstItem().getPoint(),
                a.getSecondItem().getPoint());
        GeoPoint topCenterPoint = GeoCalculations.pointAtDistance(
                _center.getPoint(), angle + 90, halfWidth);
        GeoPoint bottomCenterPoint = GeoCalculations.pointAtDistance(
                _center.getPoint(), angle - 90, halfWidth);

        getAssociationAt(1).getMarker().setPoint(topCenterPoint);
        getAssociationAt(3).getMarker().setPoint(bottomCenterPoint);
    }

    @Override
    public PointMapItem getAnchorItem() {
        return _center;
    }

    /**
     * Helper method to check if the center marker is a generic shape marker
     * as opposed to a marker explicitly set by the user
     * @return True if the center marker is a generic shape marker
     */
    public boolean isCenterShapeMarker() {
        Marker marker = getCenterMarker();
        return marker != null && marker.getType().equals(getCenterMarkerType());
    }

    /**
     * Set the center marker for this rectangle
     * @param marker Center marker or null to use the generic shape marker
     */
    public void setCenterMarker(Marker marker) {
        if (marker != null && _center == marker)
            return;

        if (_center != null) {
            _center.removeOnPointChangedListener(this);
            _center.removeOnGroupChangedListener(this);
            if (isCenterShapeMarker())
                _center.removeFromGroup();
            else if (getUID().equals(_center.getMetaString(
                    "shapeUID", "")))
                _center.removeMetaData("shapeUID");
        }
        if (marker == null) {
            // Create the default shape marker
            marker = _createCenterMarker();
            marker.setTitle(getTitle());
            marker.setVisible(getVisible());
        }
        _center = marker;
        if (isCenterShapeMarker()) {
            _center.setMetaString(getUIDKey(), getUID());
            if (_center.getGroup() == null)
                _childMapGroup.addItem(_center);
            _center.setHeight(getHeight());
        } else {
            setCenterPoint(_center.getGeoPointMetaData());
        }
        _center.addOnPointChangedListener(this);
        _center.addOnGroupChangedListener(this);
    }

    public Marker getCenterMarker() {
        return _center;
    }

    /**
     * Set the center point of this circle
     * This will move the anchor marker as well
     * @param point Center point
     */
    public void setCenterPoint(GeoPointMetaData point) {
        _ignoreCenterChange = true;

        // Update center marker position
        Marker center = getCenterMarker();
        if (center != null && !center.getPoint().equals(point.get()))
            center.setPoint(point);

        // Move rectangle to new position
        GeoPointMetaData oldPoint = computeCenter();
        if (!oldPoint.equals(point))
            move(oldPoint, point);

        _ignoreCenterChange = false;
    }

    @Override
    public void setZOrder(double zOrder) {
        if (Double.compare(zOrder, Double.NEGATIVE_INFINITY) == 0)
            // Need to make sure we have room to order properly
            zOrder = -1e8;

        // Top to bottom: Markers -> Lines -> Fill
        if (_filledShape != null)
            _filledShape.setZOrder(zOrder + Z_ORDER_FILL);
        for (Association a : _lines) {
            a.setZOrder(zOrder + Z_ORDER_LINES);
            if (a.getMarker() != null)
                a.getMarker().setZOrder(zOrder + Z_ORDER_MARKERS);
        }
        for (PointMapItem m : _points)
            m.setZOrder(zOrder + Z_ORDER_MARKERS);
        if (_center != null)
            _center.setZOrder(zOrder + Z_ORDER_MARKERS);
        super.setZOrder(zOrder);
    }

    /**
     * @return GeoPoints of the 4 corners of this Rectangle
     *             AND the "side points"
     */
    public GeoPointMetaData[] getGeoPoints() {
        GeoPointMetaData[] retval = new GeoPointMetaData[_points.size()];
        int idx = 0;
        GeoPointMetaData g;
        for (PointMapItem p : _points) {
            g = p.getGeoPointMetaData();
            if (g.get().isMutable())
                g.set(new GeoPoint(g.get(), GeoPoint.Access.READ_ONLY));
            retval[idx++] = g;
        }
        return retval;
    }

    public int getNumPoints() {
        if (_points != null)
            return _points.size();
        return 0;
    }

    /**
     * @return The average altitude of this {@link Rectangle}
     */
    public double getAvgAltitude() {
        return _avgAltitude;
    }

    @Override
    public void setTitle(String title) {
        super.setTitle(title);
        _childMapGroup.setFriendlyName(title);
        if (_center != null) {
            _center.setMetaString("shapeName", title);
            _center.setTitle(title);
            _center.refresh(MapView.getMapView().getMapEventDispatcher(), null,
                    this.getClass());
        }
        for (Association a : _lines) {
            a.setTitle(title);
            a.getMarker().setTitle(title);
            a.getFirstItem().setTitle(title);
            a.getSecondItem().setTitle(title);
        }
    }

    @Override
    public String getTitle() {
        return this.getMetaString("shapeName", "Rectangle");
    }

    /**
     * @param index
     * @return PointMapItem at the specified index.
     */
    public PointMapItem getPointAt(int index) {
        return _points.get(index);
    }

    /**
     * Get all the anchor markers this rectangle uses
     * @return List of anchor markers
     */
    public List<PointMapItem> getAnchorMarkers() {
        return new ArrayList<>(_points);
    }

    /**
     * @param p PointMapItem
     * @return Index of this PointMapItem, if this {@link Rectangle} does not contain p, returns -1.
     */
    public int getIndexOfPoint(PointMapItem p) {
        if (_points.contains(p)) {
            return _points.indexOf(p);
        } else {
            return -1;
        }
    }

    public boolean hasPoint(PointMapItem point) {
        if (point == getAnchorItem())
            return true;
        if (_points.contains(point))
            return true;
        for (Association a : _lines) {
            if (point.equals(a.getMarker())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getStrokeColor() {
        return _color;
    }

    @Override
    public void setStrokeColor(int strokeColor) {
        _color = strokeColor;
        if (!_tacticalOverlay) {
            for (Association a : _lines)
                a.setColor(strokeColor);
        }
        if (_center != null)
            _center.setColor(strokeColor);
        super.setStrokeColor(strokeColor);
    }

    @Override
    public void setBasicLineStyle(int basicLineStyle) {
        for (Association a : _lines)
            a.setBasicLineStyle(basicLineStyle);
        super.setBasicLineStyle(basicLineStyle);
    }

    /**
     * Same method as {@link #getStrokeColor()}
     */
    public int getColor() {
        return getStrokeColor();
    }

    /**
     * @param weight New stroke weight for the {@link Rectangle}'s lines
     */
    @Override
    public void setStrokeWeight(double weight) {
        for (Association a : _lines)
            a.setStrokeWeight(weight);
        super.setStrokeWeight(weight);
    }

    /**
     * STYLE_SOLID = 0, STYLE_DOTTED = 1, STYLE_DASHED = 2 and STYLE_OUTLINED
     */
    public void setLineStyle(int style) {
        setBasicLineStyle(style);
    }

    /**
     * @return Line style of the {@link Rectangle} <br>
     * <br>
     *         STYLE_SOLID = 0, STYLE_DOTTED = 1, STYLE_DASHED = 2 and STYLE_OUTLINED
     */
    public int getLineStyle() {
        return getBasicLineStyle();
    }

    /**
     * @param filled Remove or set a filled area for this {@link Rectangle}
     */
    public void setFilled(boolean filled) {
        if (filled) {
            GeoPointMetaData[] points = getMetaDataPoints();
            if (points.length < 2)
                return;
            if (_filledShape == null) {
                _filledShape = new Polyline(UUID.randomUUID().toString());
                _filledShape.setMetaBoolean("addToObjList", false);
                _filledShape.setClickable(false);
                _childMapGroup.addItem(_filledShape);
            }
            _filledShape.setPoints(points, 0, Math.min(4, points.length));
            _filledShape.setStyle(Shape.STYLE_FILLED_MASK
                    | Polyline.STYLE_CLOSED_MASK | Polyline.STYLE_STROKE_MASK);
            _filledShape.setFillColor(_fillColor);
            _filledShape.setStrokeColor(0);
            setStyle(getStyle() | Shape.STYLE_FILLED_MASK);
            _filledShape.setZOrder(getZOrder() + Z_ORDER_FILL);
            _filledShape.setHeight(getHeight());
            _filledShape.setVisible(getVisible());
        } else {
            if (_filledShape != null) {
                _childMapGroup.removeItem(_filledShape);
                _filledShape = null;
            }
            removeStyleBits(Shape.STYLE_FILLED_MASK);
        }
    }

    public boolean getFilled() {
        return Color.alpha(_fillColor) > 0;
    }

    @Override
    public void setFillColor(int fillColor) {
        _fillColor = fillColor;

        if (_filledShape != null)
            _filledShape.setFillColor(fillColor);
        else // Need to call this in order to initialize the stroke shape
            setFilled(getFilled());
    }

    @Override
    public int getFillColor() {
        return _fillColor;
    }

    private void setMirrorWidth(boolean mirrorWidth) {
        _mirrorWidth = mirrorWidth;
    }

    public boolean getMirrorWidth() {
        return _mirrorWidth;
    }

    @Override
    protected void onVisibleChanged() {
        boolean visible = getVisible();
        boolean linesVisible = _showLines && visible;
        for (Association a : _lines) {
            a.setVisible(linesVisible);
            a.getMarker().setVisible(_editable && linesVisible);
        }
        for (PointMapItem p : _points) {
            p.setVisible(_editable && visible);
        }

        if (_filledShape != null) {
            _filledShape.setVisible(visible);
            final GeoPointMetaData[] points = getGeoPoints();
            _filledShape.setPoints(points, 0, Math.min(4, points.length));
        }
        if (_center != null) {
            _center.setVisible(visible);
        }
        super.onVisibleChanged();
    }

    @Override
    public void setClickable(boolean state) {
        super.setClickable(state);
        for (Association a : _lines)
            a.setClickable(state);
        if (_center != null)
            _center.setClickable(state);
    }

    /**
     * @param m Side Marker of this {@link Rectangle}.
     * @return Index of the side that corresponds to this Marker. If not part of this
     *         {@link Rectangle}, returns -1.
     */
    public int getAssociationMarkerIndex(PointMapItem m) {
        for (int i = 0; i < _lines.size(); i++) {
            if (_lines.get(i).getMarker().equals(m)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * @return If this {@link Rectangle} is in edit mode.
     */
    @Override
    public boolean getEditable() {
        return _editable;
    }

    @Override
    public void setMovable(boolean movable) {
        super.setMovable(movable);
        if (_center != null)
            _center.setMovable(movable);
    }

    @Override
    public void setEditable(boolean editable) {
        if (_editable == editable)
            return;
        _editable = editable;
        // XXX - Do we want to enable drag on the center?
        // It would be consistent with the other markers.
        /*if (_center != null)
            _center.setMetaBoolean("drag", editable);*/
        this.setVisible(this.getVisible());
        for (Association a : _lines) {
            a.getMarker().setMetaBoolean("drag", editable);
        }
        for (PointMapItem p : _points) {
            p.setMetaBoolean("drag", editable);
        }
        if (editable) {
            preEditStrokeWeight = getStrokeWeight();
            setStrokeWeight(EDITABLE_STROKE_WEIGHT);
        } else {
            setStrokeWeight(preEditStrokeWeight);
        }
        onVisibleChanged();
    }

    @Override
    protected void onGroupChanged(boolean added, MapGroup group) {
        if (added) {
            _unitPrefs.registerListener(this);
        } else {
            _unitPrefs.unregisterListener(this);
            MapGroup childGroup = getChildMapGroup();
            if (childGroup != null) {
                MapGroup parent = childGroup.getParentGroup();
                if (parent != null)
                    parent.removeGroup(childGroup);
            }
        }
        super.onGroupChanged(added, group);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp,
            String key) {

        if (key == null)
            return;

        //if labels are shown, update units
        _recalculateLabels();
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        if (item == _center) {
            if (isCenterShapeMarker())
                removeFromGroup();
            else
                setCenterMarker(null);
        }
    }

    @Override
    public void onPointChanged(PointMapItem item) {
        if (item == _center && !_ignoreCenterChange)
            setCenterPoint(item.getGeoPointMetaData());
    }

    /**
     * Toggle tactical overlay colors on each side of the rectangle
     * White = front, Red = left, Green = right, Black = back
     * @param show
     */
    public void showTacticalOverlay(boolean show) {
        _tacticalOverlay = show;
        if (show) {
            Association front = _lines.get(0);
            Association right = _lines.get(1);
            Association back = _lines.get(2);
            Association left = _lines.get(3);
            GeoPoint frontPoint = front.getCenter().get();
            GeoPoint backPoint = back.getCenter().get();
            GeoPoint rightPoint = right.getCenter().get();
            GeoPoint leftPoint = left.getCenter().get();
            double heading = backPoint.bearingTo(frontPoint);
            double leftToFront = leftPoint.bearingTo(frontPoint) - heading;
            double rightToFront = rightPoint.bearingTo(frontPoint) - heading;
            leftToFront = (leftToFront % 360) + (leftToFront < 0 ? 360 : 0);
            rightToFront = (rightToFront % 360) + (rightToFront < 0 ? 360 : 0);
            front.setColor(Color.WHITE);
            back.setColor(Color.BLACK);
            if (leftToFront < 180 && rightToFront > 180) {
                left.setColor(Color.RED);
                right.setColor(Color.GREEN);
            } else {
                left.setColor(Color.GREEN);
                right.setColor(Color.RED);
            }
        } else {
            int strokeColor = getStrokeColor();
            for (Association a : _lines)
                a.setColor(strokeColor);
        }
    }

    public boolean showTacticalOverlay() {
        return _tacticalOverlay;
    }

    /**
     * Toggle the lines of the rectangle
     * @param showLines True to show the lines that make up the rectangle
     */
    public void showLines(boolean showLines) {
        _showLines = showLines;
        onVisibleChanged();
    }

    public boolean showLines() {
        return _showLines;
    }

    /**
     * SubClasses should override this function in order to mark each corner point with a
     * specialized parent UID Key, (e.g. {AirField} overrides it with
     * {@code return "airfieldUID";}
     * 
     * @return The UID Key for this {@link Rectangle}
     */
    protected abstract String getUIDKey();

    protected abstract String getAssocType();

    protected abstract String getCenterMarkerType();

    protected abstract String getMenuPath();

    protected abstract String getCotType();

    /**
     * SubClasses should override this function so that corner points have a specialized type, (e.g.
     * {AirField} overrides it with {@code return "airfield_corner";}
     * 
     * @return The corner point type for this {@link Rectangle}
     */
    public abstract String getCornerPointType();

    /**
     * SubClasses should override this function so that side points have a specialized type, (e.g.
     * {AirField} overrides it with {@code return "airfield_side";}
     * 
     * @return The side marker type for this {@link Rectangle}
     */
    protected abstract String getSideMarkerType();

    @Override
    protected void onPointsChanged() {
        GeoPointMetaData[] gp = getGeoPoints();
        int corners = Math.min(4, gp.length);
        if (_filledShape != null) {
            // XXX - per setPoints(...), it appears that the first 4 points
            // reflect the corners and the rest correspond to other stuff
            _filledShape.setPoints(gp, 0, corners);
        }
        // Recalculate average altitude based on corners
        double sum = 0;
        int valid = 0;
        for (int i = 0; i < corners; i++) {
            GeoPoint p = gp[i].get();
            double alt = ElevationManager.getElevation(p.getLatitude(),
                    p.getLongitude(), null);
            if (GeoPoint.isAltitudeValid(alt)) {
                sum += alt;
                valid++;
            }
        }
        this._avgAltitude = valid > 0 ? sum / valid : Double.NaN;

        _recalculateLabels();

        // Update bounds
        GeoPoint[] pts = new GeoPoint[gp.length];
        for (int i = 0; i < pts.length; i++)
            pts[i] = gp[i].get();
        _bounds.set(pts, wrap180());

        // Notify listeners that points have changed;
        super.onPointsChanged();
    }

    // Event when entire rectangle is moved (Rectangle.move)
    public interface OnMoveListener {
        void onMoved(Rectangle r, GeoPointMetaData[] oldPoints,
                GeoPointMetaData[] newPoints);
    }

    protected void onMoved(GeoPointMetaData[] oldPoints,
            GeoPointMetaData[] newPoints) {
        for (OnMoveListener l : _onMoveListeners)
            l.onMoved(this, oldPoints, newPoints);
    }

    public void addOnMovedListener(OnMoveListener l) {
        if (!_onMoveListeners.contains(l))
            _onMoveListeners.add(l);
    }

    public void removeOnMovedListener(OnMoveListener l) {
        _onMoveListeners.remove(l);
    }

    private final List<OnMoveListener> _onMoveListeners = new ArrayList<>();

    @Override
    public GeoBounds getBounds(MutableGeoBounds bnds) {
        if (bnds != null) {
            bnds.set(_bounds);
            return bnds;
        } else
            return new GeoBounds(_bounds);
    }

    /************************ PRIVATE METHODS ****************************/

    private GeoPointMetaData _getActualCenter(Association a) {
        return _getActualCenter(a.getFirstItem().getPoint(), a.getSecondItem()
                .getPoint());
    }

    private GeoPointMetaData _getActualCenter(GeoPoint p1, GeoPoint p2) {
        double[] rb = DistanceCalculations.computeDirection(p1, p2);
        return GeoPointMetaData.wrap(
                GeoCalculations.pointAtDistance(p1, rb[1], rb[0] * 0.5),
                GeoPointMetaData.CALCULATED,
                GeoPointMetaData.CALCULATED);
    }

    private void _onSideChanged(PointMapItem side) {
        synchronized (_ignoreLock) {
            //Set up edge indices
            int i = getAssociationMarkerIndex(side);
            int si1 = (i + 1) % 4;
            int opp = (i + 2) % 4;
            int si0 = (i + 3) % 4;

            //Set up edges
            Association a = getAssociationAt(i); //Dragged edge
            Association ao = getAssociationAt(opp); //Opposite edge
            Association as0 = getAssociationAt(si0); //Adjacent edge
            Association as1 = getAssociationAt(si1); //Adjacent edge

            //Set up significant vertices
            GeoPointMetaData prevSidePos = _getActualCenter(a); //Previous position of the dragged edge handle
            GeoPoint sidePos = side.getPoint(); //Current position of the dragged edge handle
            GeoPoint oppPos = ao.getMarker().getPoint(); //Opposite edge handle
            GeoPoint fulcrum = oppPos; //Pivot point

            // Determine the rotation angles for the dragged edge and opposite edge. For small
            // rectangles, rotS and rotO will be very close if not identical, but as size increases
            // they will diverge due to Earth's curvature.
            double[] rbSF0 = DistanceCalculations.computeDirection(
                    prevSidePos.get(),
                    fulcrum);
            double[] rbSF1 = DistanceCalculations.computeDirection(sidePos,
                    fulcrum);
            double rotS = rbSF1[1] - rbSF0[1];
            double[] rbFS0 = DistanceCalculations.computeDirection(fulcrum,
                    prevSidePos.get());
            double[] rbFS1 = DistanceCalculations.computeDirection(fulcrum,
                    sidePos);
            double rotO = rbFS1[1] - rbFS0[1];

            //Get the polar coordinates to the corners from the edge handles
            double[] rbS1 = DistanceCalculations.computeDirection(
                    prevSidePos.get(),
                    a.getFirstItem().getPoint());
            double[] rbS2 = DistanceCalculations.computeDirection(
                    prevSidePos.get(),
                    a.getSecondItem().getPoint());
            double[] rbO1 = DistanceCalculations.computeDirection(oppPos, ao
                    .getFirstItem().getPoint());
            double[] rbO2 = DistanceCalculations.computeDirection(oppPos, ao
                    .getSecondItem().getPoint());

            if (_mirrorWidth) {
                //Update the opposite edge position
                oppPos = GeoCalculations.pointAtDistance(fulcrum,
                        rbFS1[1] + 180, rbFS1[0]);
            }

            //Update the corner positions
            _ignore += 6; //Suppress corner/edge move events for 6 more updates
            setPoint(a.getFirstItem(),
                    GeoPointMetaData.wrap(
                            GeoCalculations.pointAtDistance(sidePos,
                                    rbS1[1] + rotS, rbS1[0]),
                            GeoPointMetaData.CALCULATED,
                            GeoPointMetaData.CALCULATED));
            setPoint(a.getSecondItem(),
                    GeoPointMetaData.wrap(
                            GeoCalculations.pointAtDistance(sidePos,
                                    rbS2[1] + rotS, rbS2[0]),
                            GeoPointMetaData.CALCULATED,
                            GeoPointMetaData.CALCULATED));

            setPoint(ao.getFirstItem(),
                    GeoPointMetaData.wrap(
                            GeoCalculations.pointAtDistance(oppPos,
                                    rbO1[1] + rotO, rbO1[0]),
                            GeoPointMetaData.CALCULATED,
                            GeoPointMetaData.CALCULATED));
            setPoint(ao.getSecondItem(),
                    GeoPointMetaData.wrap(
                            GeoCalculations.pointAtDistance(oppPos,
                                    rbO2[1] + rotO, rbO2[0]),
                            GeoPointMetaData.CALCULATED,
                            GeoPointMetaData.CALCULATED));

            if (_mirrorWidth) {
                _ignore += 1; //Suppress corner/edge move events for another update
                setPoint(ao.getMarker(), _getActualCenter(ao));
            }

            setPoint(as1.getMarker(), _getActualCenter(as1));
            setPoint(as0.getMarker(), _getActualCenter(as0));

            //Update the length and width
            _initDimensions();
            this.onPointsChanged();
        }
    }

    private void _onCornerChanged(PointMapItem corner) {
        int i = this.getIndexOfPoint(corner);
        int opp = (i + 2) % 4, cia = (i + 3) % 4, cib = (i + 1) % 4;
        PointMapItem ca = this.getPointAt(cia);
        PointMapItem cb = this.getPointAt(cib);
        PointMapItem co = this.getPointAt(opp);

        GeoPointMetaData center = _getActualCenter(ca.getPoint(),
                cb.getPoint());

        GeoPointMetaData edgeCenter = _getActualCenter(co.getPoint(),
                cb.getPoint());
        double[] rbCE = DistanceCalculations.computeDirection(center.get(),
                edgeCenter.get());
        double azi = rbCE[1] - 180;
        if (!_mirrorWidth) {
            center = _getActualCenter(co.getPoint(), corner.getPoint());
        }
        double[] rbCC = DistanceCalculations.computeDirection(center.get(),
                corner.getPoint());
        double ang = DistanceCalculations
                .calculateAngleDifference(azi, rbCC[1]);
        _ignore += 2;
        setPoint(ca,
                GeoPointMetaData
                        .wrap(GeoCalculations.pointAtDistance(center.get(), azi
                                - ang, rbCC[0])));
        setPoint(cb,
                GeoPointMetaData
                        .wrap(GeoCalculations.pointAtDistance(center.get(), azi
                                - ang + 180, rbCC[0])));
        if (_mirrorWidth) {
            _ignore += 1;
            setPoint(co,
                    GeoPointMetaData.wrap(GeoCalculations.pointAtDistance(
                            center.get(), azi + ang + 180, rbCC[0])));
        }

        _ignore += 4;
        for (Association a : this.getAssociations()) {
            setPoint(a.getMarker(), _getActualCenter(a));
        }
        _initDimensions();// calc the width and length after the move
        this.onPointsChanged();
    }

    private void _initDimensions() {
        _length = DistanceCalculations.computeDirection(this
                .getAssociationAt(0).getMarker()
                .getPoint(),
                this.getAssociationAt(2).getMarker().getPoint())[0];
        _width = DistanceCalculations.computeDirection(this.getAssociationAt(1)
                .getMarker()
                .getPoint(),
                this.getAssociationAt(3).getMarker().getPoint())[0];

        //Update the center point while we're here
        setPoint(_center, computeCenter());
        _bounds.set(getPoints(), wrap180());
    }

    private void _createChildItems(GeoPointMetaData p0, GeoPointMetaData p1,
            GeoPointMetaData p2,
            GeoPointMetaData p3) {
        PointMapItem m0 = this._createCornerMarker(p0);
        PointMapItem m1 = this._createCornerMarker(p1);
        PointMapItem m2 = this._createCornerMarker(p2);
        PointMapItem m3 = this._createCornerMarker(p3);
        _points.add(m0);
        _points.add(m1);
        _points.add(m2);
        _points.add(m3);
        _childMapGroup.addItem(m0);
        _childMapGroup.addItem(m1);
        _childMapGroup.addItem(m2);
        _childMapGroup.addItem(m3);
        Association a0 = this._createAssociation(m0, m1);
        Association a1 = this._createAssociation(m1, m2);
        Association a2 = this._createAssociation(m2, m3);
        Association a3 = this._createAssociation(m3, m0);
        _lines.add(a0);
        _lines.add(a1);
        _lines.add(a2);
        _lines.add(a3);
        _childMapGroup.addItem(a0);
        _childMapGroup.addItem(a1);
        _childMapGroup.addItem(a2);
        _childMapGroup.addItem(a3);
        Marker s0 = this._createSideMarker(_getActualCenter(a0));
        Marker s1 = this._createSideMarker(_getActualCenter(a1));
        Marker s2 = this._createSideMarker(_getActualCenter(a2));
        Marker s3 = this._createSideMarker(_getActualCenter(a3));
        _points.add(s0);
        _points.add(s1);
        _points.add(s2);
        _points.add(s3);
        m0.addOnPointChangedListener(cornerListener);
        m1.addOnPointChangedListener(cornerListener);
        m2.addOnPointChangedListener(cornerListener);
        m3.addOnPointChangedListener(cornerListener);
        s0.addOnPointChangedListener(sideListener);
        s1.addOnPointChangedListener(sideListener);
        s2.addOnPointChangedListener(sideListener);
        s3.addOnPointChangedListener(sideListener);
        a0.setMarker(s0);
        a1.setMarker(s1);
        a2.setMarker(s2);
        a3.setMarker(s3);
        _childMapGroup.addItem(s0);
        _childMapGroup.addItem(s1);
        _childMapGroup.addItem(s2);
        _childMapGroup.addItem(s3);
        Marker c = this._createCenterMarker();
        c.setTitle(_childMapGroup.getFriendlyName());
        setCenterMarker(c);
        setAssociations(_lines);
    }

    private Association _createAssociation(PointMapItem firstItem,
            PointMapItem secondItem) {
        Association a = new Association(UUID.randomUUID().toString());
        a.setFirstItem(firstItem);
        a.setSecondItem(secondItem);
        a.setColor(this.getColor());
        a.setStyle(Association.STYLE_SOLID);
        a.setStrokeWeight(_editable ? EDITABLE_STROKE_WEIGHT
                : DEFAULT_STROKE_WEIGHT);
        a.setZOrder(getZOrder() + Z_ORDER_LINES);
        a.setClampToGround(true);

        //Add labels here if the option is selected
        if (getMetaBoolean("labels_on", false)) {
            double dist = firstItem.getPoint()
                    .distanceTo(secondItem.getPoint());
            a.setText(SpanUtilities.formatType(_unitPrefs.getRangeSystem(),
                    dist, Span.METER));
        }

        a.setLink(Association.LINK_LINE);
        a.setClickable(false);
        a.setType(this.getAssocType());
        a.setRadialMenu(getMenuPath());
        a.setClickable(getClickable());
        a.setMetaBoolean("editable", getMetaBoolean("editable", true));
        a.setMetaString("shapeUID", getUID());
        a.setTitle(getTitle());
        return a;
    }

    private Marker _createCornerMarker(GeoPointMetaData point) {
        Marker m = new Marker(point, UUID.randomUUID().toString());
        m.setMetaString("entry", ""); // can't modify these via DTED because the extra calculations
                                      // mess with the ignoring of the onpointchangedlistener
        m.setMovable(getMovable());
        m.setType(getCornerPointType());
        m.setMetaString("how", "h-g-i-g-o");
        m.setMetaString(getUIDKey(), getUID());
        m.setMetaBoolean("addToObjList", false);
        m.setMetaBoolean("nevercot", true);
        m.setVisible(_editable);
        m.setMetaBoolean("removable", false);
        m.setZOrder(getZOrder() + Z_ORDER_MARKERS);
        m.setShowLabel(false);
        m.setTitle(getTitle());
        return m;
    }

    private Marker _createSideMarker(GeoPointMetaData point) {
        Marker m = new Marker(point, UUID.randomUUID().toString());
        m.setMetaString("entry", ""); // can't modify these via DTED because the extra calculations
                                      // mess with the ignoring of the onpointchangedlistener
        m.setMetaBoolean("editable", getMetaBoolean("editable", true));
        m.setMovable(getMovable());
        m.setType(getSideMarkerType());
        m.setMetaString("how", "h-g-i-g-o");
        m.setMetaString(getUIDKey(), getUID());
        m.setMetaBoolean("addToObjList", false);
        m.setMetaBoolean("nevercot", true);
        m.setVisible(_editable);
        m.setMetaBoolean("removable", false);
        m.setZOrder(getZOrder() + Z_ORDER_MARKERS);
        m.setShowLabel(false);
        m.setTitle(getTitle());
        return m;
    }

    private Marker _createCenterMarker() {
        final String uid = UUID.randomUUID().toString();
        Marker m = new Marker(
                GeoCalculations.computeAverage(
                        getPoints(), wrap180()),
                uid);
        m.setMetaString("entry", "user");
        m.setMetaBoolean("editable", getMetaBoolean("editable", true));
        m.setType(getCenterMarkerType());
        m.setMetaString("how", "h-g-i-g-o");
        m.setMetaString(getUIDKey(), getUID());
        m.setMetaBoolean("addToObjList", false);
        m.setMetaString("shapeName", getTitle());
        m.setMetaBoolean("nevercot", true);
        m.setMovable(getMovable());
        m.setRadialMenu(getMenuPath());
        m.setZOrder(getZOrder() + Z_ORDER_MARKERS);
        m.setColor(getColor());
        //m.refresh(mapView.getMapEventDispatcher(), null);

        return m;
    }

    private void _recalculateLabels() {
        if (hasMetaValue("labels_on")) {
            for (int i = 0; i < _lines.size(); i++) {
                Association a = _lines.get(i);
                double dist = a.getFirstItem().getPoint()
                        .distanceTo(a.getSecondItem().getPoint());
                a.setText(SpanUtilities.formatType(_unitPrefs.getRangeSystem(),
                        dist, Span.METER));
            }
        }
    }

    /************************ LISTENERS ****************************/

    public void setLabelVisibility(boolean visible) {
        toggleMetaData("labels_on", visible);
    }

    @Override
    public void toggleMetaData(String key, boolean on) {
        if (key.equals("labels_on")) {
            for (int i = 0; i < _lines.size(); i++) {
                Association a = _lines.get(i);
                if (on) {
                    double dist = a.getFirstItem().getPoint()
                            .distanceTo(a.getSecondItem().getPoint());
                    a.setText(SpanUtilities.formatType(
                            _unitPrefs.getRangeSystem(),
                            dist, Span.METER));
                } else
                    a.setText("");
            }
            if (_center != null)
                _center.toggleMetaData("labels_on", on);
        }
        super.toggleMetaData(key, on);
    }

    private final PointMapItem.OnPointChangedListener sideListener = new PointMapItem.OnPointChangedListener() {
        @Override
        public void onPointChanged(PointMapItem item) {
            synchronized (_ignoreLock) {
                if (_ignore == 0) {
                    _onSideChanged(item);
                }
                if (_ignore != 0)
                    _ignore--;// can never be less than 0

            }
        }
    };
    private final PointMapItem.OnPointChangedListener cornerListener = new PointMapItem.OnPointChangedListener() {
        @Override
        public void onPointChanged(PointMapItem item) {
            synchronized (_ignoreLock) {
                if (_ignore == 0) {
                    _onCornerChanged(item);
                }
                if (_ignore != 0)
                    _ignore--;// can never be less than 0

            }
        }
    };

    public double getWidth() {
        return _width;
    }

    public double getLength() {
        return _length;
    }

    @Override
    public void setHeight(double height) {
        super.setHeight(height);
        if (_filledShape != null)
            _filledShape.setHeight(height);
        if (_center != null)
            _center.setHeight(height);
    }

    /********************* UNDO ACTIONS ******************************/
    static protected class MovePointAction extends EditAction {

        final PointMapItem _item;
        final GeoPointMetaData _newPoint;
        final GeoPointMetaData[] _oldCorners;
        final Rectangle _rect;

        public MovePointAction(PointMapItem item, GeoPointMetaData newPoint,
                Rectangle r, GeoPointMetaData[] oldCorners) {
            _item = item;
            _newPoint = newPoint;
            if (oldCorners == null) {
                GeoPointMetaData[] points = r.getMetaDataPoints();
                oldCorners = new GeoPointMetaData[] {
                        points[0], points[1],
                        points[2], points[3]
                };
            }
            _oldCorners = oldCorners;
            _rect = r;
        }

        public MovePointAction(PointMapItem item, GeoPointMetaData newPoint,
                Rectangle r) {
            this(item, newPoint, r, null);
        }

        @Override
        public boolean run() {
            _rect.setPoint(_item, _newPoint);
            return true;
        }

        @Override
        public void undo() {
            // Can't rely on moving the marker back since it switches
            // position with opposite side marker after undo is saved
            //_item.setPoint(_oldPoint);

            // Reset corners
            _rect.setPoints(_oldCorners[0], _oldCorners[1],
                    _oldCorners[2], _oldCorners[3]);
        }

        @Override
        public String getDescription() {
            return null;
        }
    }

    /************************* BUILDER *********************************/

    /**
     * Allows the creation of a Rectangle. The subsidiary items of the Rectangle will display on the
     * map in the given Child MapGroup as the Rectangle is built. This Builder class should simplify
     * the implementation process for sub classes. NOTE: All sub classes should extend this Builder
     * and should override the protected Builder(Recangle, Mode) constructor, and the build
     * function.
     */
    public static class Builder implements Undoable {
        /**
         * @param r
         * @param mode
         */
        protected Builder(Rectangle r, Mode mode) {
            _r = r;
            _m = mode;
            _r._editable = true;
            switch (mode) {
                case START_END_WIDTH_MIRRORED:
                    _r.setMirrorWidth(true);
                    break;
                case START_END_WIDTH_UNMIRRORED:
                case START_END_WIDTH_SIDE:
                case START_END_CORNERS:
                    _r.setMirrorWidth(false);
                    break;
            }
        }

        public Builder setFirstPoint(GeoPointMetaData p) {
            switch (_m) {
                case START_END_WIDTH_MIRRORED:
                case START_END_WIDTH_UNMIRRORED:
                    _m0 = _r._createSideMarker(p);
                    _r.getChildMapGroup().addItem(_m0);
                    break;
                case THREE_POINTS:
                case START_END_CORNERS:
                case START_END_WIDTH_SIDE:
                    _m0 = _r._createCornerMarker(p);
                    _m0.addOnPointChangedListener(_r.cornerListener);
                    _r.getChildMapGroup().addItem(_m0);
                    break;
            }
            return this;
        }

        public Builder setThirdPoint(GeoPointMetaData p) {
            //Get the points in ECEF space
            ECEF e0 = ECEF.fromGeoPoint(_m0.getPoint());
            ECEF e1 = ECEF.fromGeoPoint(_m1.getPoint());
            ECEF e2 = ECEF.fromGeoPoint(p.get());
            Vector3D v0 = new Vector3D((float) e0.getX(), (float) e0.getY(),
                    (float) e0.getZ());
            Vector3D v1 = new Vector3D((float) e1.getX(), (float) e1.getY(),
                    (float) e1.getZ());
            Vector3D v2 = new Vector3D((float) e2.getX(), (float) e2.getY(),
                    (float) e2.getZ());
            //Get the normal of the plane between m0, m1, and the core of the Earth
            Vector3D n = (new Vector3D()).subtract(v0).cross(v1.subtract(v0))
                    .normalize();
            //Get the nearest point on the plane from p
            float dist = n.dot(v2);
            //Get the offset from the nearest point to p
            Vector3D offset = n.multiply(dist);
            //Vector3D nearest = v2.subtract(offset);
            //Find the center of the base line from m0 to m1
            Vector3D centerBase = v0.add(v1).multiply(0.5f);
            //Estimate the center of the rectangle to be the center of the base line + half the offset
            Vector3D center = centerBase.add(offset.multiply(0.5f));
            ECEF ec = new ECEF(center.x, center.y, center.z);
            GeoPoint gcenter = ec.toGeoPoint();
            //Get the offset from the center to the corners m0, m1 and the center of the base line in polar coordinates
            double[] rb0 = DistanceCalculations.computeDirection(gcenter,
                    _m0.getPoint());
            double[] rb1 = DistanceCalculations.computeDirection(gcenter,
                    _m1.getPoint());
            double[] rbcb = DistanceCalculations.computeDirection(gcenter,
                    (new ECEF(centerBase.x, centerBase.y, centerBase.z))
                            .toGeoPoint());
            //Average the radius and angle to each corner (they should be quite close if not exactly equal) to compute the far corners
            double radius = (rb0[0] + rb1[0]) * 0.5;
            double ang = DistanceCalculations.calculateAngleDifference(rb0[1],
                    rb1[1]) * 0.5;
            double azi = rbcb[1];
            //Compute the far corners
            GeoPointMetaData gp2 = GeoPointMetaData
                    .wrap(GeoCalculations.pointAtDistance(gcenter,
                            azi + 180 - ang, radius));
            GeoPointMetaData gp3 = GeoPointMetaData
                    .wrap(GeoCalculations.pointAtDistance(gcenter,
                            azi + 180 + ang, radius));

            _m2 = _r._createCornerMarker(gp2);
            _m2.addOnPointChangedListener(_r.cornerListener);
            _r.getChildMapGroup().addItem(_m2);

            Marker _m3 = _r._createCornerMarker(gp3);
            _m3.addOnPointChangedListener(_r.cornerListener);
            _r.getChildMapGroup().addItem(_m3);

            PointMapItem[] points = new PointMapItem[] {
                    _m0, _m1, _m2, _m3
            };
            Association[] assocs = _createAssociations(points);
            _addSideMarkers(assocs);

            //Save the data to the rectangle object
            _r._points.clear();
            _r._points.addAll(Arrays.asList(points));

            _r._lines.clear();
            for (Association a : assocs) {
                _r._points.add(a.getMarker());
                _r._lines.add(a);
                _r.getChildMapGroup().addItem(a);
            }

            _r.setAssociations(_r._lines);
            _r._initDimensions();

            return this;
        }

        public Builder setSecondPoint(GeoPointMetaData p) {
            switch (_m) {
                case THREE_POINTS:
                    _m1 = _r._createCornerMarker(p);
                    _m1.addOnPointChangedListener(_r.cornerListener);
                    _r.getChildMapGroup().addItem(_m1);
                    break;
                case START_END_WIDTH_MIRRORED:
                case START_END_WIDTH_UNMIRRORED:
                    _m1 = _r._createSideMarker(p);
                    _r.getChildMapGroup().addItem(_m1);
                    PointMapItem[] points = _createInvisibleCorners(
                            _m0.getGeoPointMetaData(), p);
                    Association[] assocs = _createAssociations(points);
                    assocs[0].setMarker(_m0);
                    assocs[2].setMarker(_m1);
                    _r._points.clear();
                    for (PointMapItem pmi : points) {
                        _r._points.add(pmi);
                        _r.getChildMapGroup().addItem(pmi);
                    }
                    _r._lines.clear();
                    for (Association a : assocs) {
                        _r._points.add(a.getMarker());
                        _r._lines.add(a);
                        _r.getChildMapGroup().addItem(a);
                    }
                    _r.setAssociations(_r._lines);
                    break;
                case START_END_WIDTH_SIDE:
                    _m1 = _r._createCornerMarker(p);
                    _m1.addOnPointChangedListener(_r.cornerListener);
                    _r.getChildMapGroup().addItem(_m1);
                    _a = _r._createAssociation(_m0, _m1);
                    _r.getChildMapGroup().addItem(_a);
                    break;
                case START_END_CORNERS:
                    _m1 = _r._createCornerMarker(p);
                    _m1.addOnPointChangedListener(_r.cornerListener);
                    _r.getChildMapGroup().addItem(_m1);
                    Marker c0 = _r._createCornerMarker(
                            GeoPointMetaData.wrap(new GeoPoint(_m0
                                    .getPoint().getLatitude(),
                                    _m1.getPoint().getLongitude())));
                    Marker c1 = _r._createCornerMarker(
                            GeoPointMetaData.wrap(new GeoPoint(_m1
                                    .getPoint().getLatitude(),
                                    _m0.getPoint().getLongitude())));
                    c0.addOnPointChangedListener(_r.cornerListener);
                    c1.addOnPointChangedListener(_r.cornerListener);
                    _r.getChildMapGroup().addItem(c0);
                    _r.getChildMapGroup().addItem(c1);
                    _r._points.add(0, _m0);

                    final double bearing = GeoCalculations.bearingTo(
                            _m0.getPoint(),
                            _m1.getPoint());
                    // Make sure to put the markers in counter-clockwise order
                    if ((bearing >= 270d && bearing <= 360d)
                            || (bearing >= 90d && bearing < 180d)) {
                        _r._points.add(1, c1);
                        _r._points.add(2, _m1);
                        _r._points.add(3, c0);
                    } else {
                        _r._points.add(1, c0);
                        _r._points.add(2, _m1);
                        _r._points.add(3, c1);
                    }
                    Association[] associations = _createAssociations(_r._points
                            .toArray(new PointMapItem[4]));
                    _addSideMarkers(associations);
                    for (int i = 0; i < 4; i++) {
                        _r._points.add(associations[i].getMarker());
                        _r._lines.add(i, associations[i]);
                        _r.getChildMapGroup().addItem(associations[i]);
                    }
                    _r.setAssociations(_r._lines);
                    break;
            }
            return this;
        }

        public Builder setWidth(double width) {
            final double bearing = GeoCalculations.bearingTo(_m0.getPoint(),
                    _m1.getPoint());
            switch (_m) {
                case START_END_WIDTH_MIRRORED:
                    double half = width / 2;
                    _r._points.get(0).setPoint(
                            GeoCalculations.pointAtDistance(_m0.getPoint(),
                                    bearing - 90d, half));
                    _r._points.get(1).setPoint(
                            GeoCalculations.pointAtDistance(_m0.getPoint(),
                                    bearing + 90d, half));
                    _r._points.get(2).setPoint(
                            GeoCalculations.pointAtDistance(_m1.getPoint(),
                                    bearing + 90d, half));
                    _r._points.get(3).setPoint(
                            GeoCalculations.pointAtDistance(_m1.getPoint(),
                                    bearing - 90d, half));
                    break;
                case START_END_WIDTH_UNMIRRORED:
                    break;
                case START_END_WIDTH_SIDE:
                    Marker s0 = _r._createCornerMarker(
                            GeoPointMetaData.wrap(GeoCalculations
                                    .pointAtDistance(_m0.getPoint(),
                                            bearing + 90d, width)));
                    Marker s1 = _r._createCornerMarker(
                            GeoPointMetaData.wrap(GeoCalculations
                                    .pointAtDistance(_m1.getPoint(),
                                            bearing + 90d, width)));
                    s0.addOnPointChangedListener(_r.cornerListener);
                    s0.addOnPointChangedListener(_r.cornerListener);
                    _r.getChildMapGroup().addItem(s0);
                    _r.getChildMapGroup().addItem(s1);
                    _r._points.add(0, _m1);
                    _r._points.add(1, _m0);
                    _r._points.add(2, s0);
                    _r._points.add(3, s1);
                    _r._lines.add(0, _a);
                    Association a1 = _r._createAssociation(_r._points.get(1),
                            _r._points.get(2));
                    Association a2 = _r._createAssociation(_r._points.get(2),
                            _r._points.get(3));
                    Association a3 = _r._createAssociation(_r._points.get(3),
                            _r._points.get(0));
                    _r._lines.add(1, a1);
                    _r.getChildMapGroup().addItem(a1);
                    _r._lines.add(2, a2);
                    _r.getChildMapGroup().addItem(a2);
                    _r._lines.add(3, a3);
                    _r.getChildMapGroup().addItem(a3);
                    for (Association a : _r._lines)
                        _r._points.add(a.getMarker());
                    _r.setAssociations(_r._lines);
                    break;
                case START_END_CORNERS:
                    break;
            }
            return this;
        }

        public Builder createCenterMarker() {
            Marker c = _r._createCenterMarker();
            c.setTitle(_r.getTitle());
            _r.getChildMapGroup().addItem(c);
            _r.setCenterMarker(c);
            return this;
        }

        public Builder createCenterMarker(String customTitle) {
            Marker c = _r._createCenterMarker();
            _r.setTitle(customTitle);
            c.setTitle(customTitle);
            _r.getChildMapGroup().addItem(c);
            _r.setCenterMarker(c);
            return this;
        }

        public Rectangle build() {
            _built = true;
            _r._initDimensions();
            _r.setEditable(false);
            _r.setFilled(_r.getFilled());
            return _r;
        }

        public Rectangle buildDR(double weight) {
            _built = true;
            _r._initDimensions();
            _r.setEditable(false);
            _r.setFilled(_r.getFilled());
            _r.setStrokeWeight(weight);
            return _r;
        }

        public void dispose() {
            // should remove all of the items
            MapGroup childGroup = _r.getChildMapGroup();
            MapGroup parentGroup = childGroup.getParentGroup();
            if (parentGroup != null)
                parentGroup.removeGroup(childGroup);
            _r.removeFromGroup();
        }

        public boolean built() {
            return _built;
        }

        private PointMapItem[] _createInvisibleCorners(GeoPointMetaData p0,
                GeoPointMetaData p1) {
            PointMapItem m0 = _r._createCornerMarker(p0);
            m0.setVisible(false);
            PointMapItem m1 = _r._createCornerMarker(p0);
            m1.setVisible(false);
            PointMapItem m2 = _r._createCornerMarker(p1);
            m2.setVisible(false);
            PointMapItem m3 = _r._createCornerMarker(p1);
            m3.setVisible(false);
            m0.addOnPointChangedListener(_r.cornerListener);
            m1.addOnPointChangedListener(_r.cornerListener);
            m2.addOnPointChangedListener(_r.cornerListener);
            m3.addOnPointChangedListener(_r.cornerListener);
            return new PointMapItem[] {
                    m0, m1, m2, m3
            };
        }

        private void _addSideMarkers(Association[] assocs) {
            for (Association a : assocs) {
                Marker s = _r._createSideMarker(_r._getActualCenter(a));
                _r.getChildMapGroup().addItem(s);
                a.setMarker(s);
                s.addOnPointChangedListener(_r.sideListener);
            }
        }

        private Association[] _createAssociations(PointMapItem[] points) {
            Association a0 = _r._createAssociation(points[0], points[1]);
            Association a1 = _r._createAssociation(points[1], points[2]);
            Association a2 = _r._createAssociation(points[2], points[3]);
            Association a3 = _r._createAssociation(points[3], points[0]);
            return new Association[] {
                    a0, a1, a2, a3
            };
        }

        @Override
        public boolean run(EditAction action) {
            return false;
        }

        @Override
        public void undo() {
            Marker m;
            if (_m == Mode.THREE_POINTS)
                m = _m1 != null ? _m1 : _m0;
            else
                m = _m0;
            if (m != null) {
                m.removeOnPointChangedListener(_r.cornerListener);
                m.removeFromGroup();
            }
        }

        protected final Rectangle _r;
        private final Mode _m;
        private Marker _m0, _m1, _m2;
        private Association _a;
        private boolean _built;

        public enum Mode {
            START_END_WIDTH_MIRRORED("start_end_width_mirrored"),
            START_END_WIDTH_UNMIRRORED("start_end_width_unmirrored"),
            START_END_WIDTH_SIDE("start_end_width_side"),
            START_END_CORNERS("start_end_corners"),
            THREE_POINTS("three_points");

            private final String _type;

            Mode(String type) {
                _type = type;
            }

            public String getType() {
                return _type;
            }
        }
    }

    @Override
    public double getArea() {
        return getLength() * getWidth();
    }

    @Override
    public double getPerimeterOrLength() {
        return 2 * getLength() + 2 * getWidth();
    }
}
