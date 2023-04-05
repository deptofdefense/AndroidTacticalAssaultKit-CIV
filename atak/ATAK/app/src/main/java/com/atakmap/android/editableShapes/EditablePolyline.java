
package com.atakmap.android.editableShapes;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.SparseArray;

import com.atakmap.android.gpx.GpxTrack;
import com.atakmap.android.gpx.GpxTrackSegment;
import com.atakmap.android.gpx.GpxWaypoint;
import com.atakmap.android.imagecapture.CapturePP;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.DefaultMetaDataHolder;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.MetaDataHolder;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.graphics.AbstractGLMapItem2;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.RouteGpxIO;
import com.atakmap.android.util.EditAction;
import com.atakmap.android.util.Undoable;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;

import com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.hittest.HitTestQueryParameters;
import com.atakmap.math.MathUtils;
import com.atakmap.spatial.file.export.GPXExportWrapper;
import com.atakmap.spatial.file.export.KMZFolder;
import com.atakmap.spatial.file.export.OGRFeatureExportWrapper;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.model.Coordinate;
import com.ekito.simpleKML.model.Data;
import com.ekito.simpleKML.model.ExtendedData;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;
import com.ekito.simpleKML.model.Geometry;
import com.ekito.simpleKML.model.IconStyle;
import com.ekito.simpleKML.model.LineString;
import com.ekito.simpleKML.model.LineStyle;
import com.ekito.simpleKML.model.Placemark;
import com.ekito.simpleKML.model.Point;
import com.ekito.simpleKML.model.PolyStyle;
import com.ekito.simpleKML.model.Polygon;
import com.ekito.simpleKML.model.Style;
import com.ekito.simpleKML.model.StyleSelector;

import org.gdal.ogr.ogr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A Polyline that supports modification of it's points via the GUI. Reports touch events complete
 * with which vertex or line the user touched, has the concept of an edit mode, and shows different
 * menus depending on what was touched and if edit mode is active.
 * <p/>
 * Combined with EditablePolyLineEditTool, EditablePolylineMoveTool, EditablePolyLineReceiver, and a
 * class like ShapeCreationTool or RouteCreationTool, closed shapes and line segments can be input
 * and modified by the user.
 * <p/>
 * When not being edited and not clickable, performance should be comparable to that of a Polyline.
 * <p/>
 * Some of the points may also be markers.
 * 
 * 
 */

// XXX - mark for improvement.
//   - work should be performed move the waypoint stuff back up into routes.
//   - make polyline use PointMapItem instead of geopoints instead of just GeoPoints 
//     which would at the cost of some memory, reduce complexity of the implementation
//   
public class EditablePolyline extends Polyline implements AnchoredMapItem,
        Exportable, MapItem.OnZOrderChangedListener,
        MapItem.OnGroupChangedListener {

    public static final String TAG = "EditablePolyline";

    private static final double DEFAULT_STROKE_WEIGHT = 3.0d;
    private static final double DEFAULT_MIN_RENDER_VERTS = (1.0d / 50000.0d);

    protected Marker _shapeMarker = null;
    private boolean _editable = false;
    private boolean _lockedZOrder = false;
    private boolean bulkOperationInProgress = false;
    private boolean initialBulkLoad = false; // do not need to worry about inserting points into an
    // existing line;

    private String _title;

    private Undoable _undo;

    private final GeoPointMetaData _avgAltitude = new GeoPointMetaData();
    private final GeoPointMetaData _maxAltitude = new GeoPointMetaData();
    private final GeoPointMetaData _minAltitude = new GeoPointMetaData();

    private int _linkColor = getStrokeColor();
    private boolean _closed = false;
    private boolean _filled;

    private final ConcurrentLinkedQueue<OnEditableChangedListener> _onEditableChanged = new ConcurrentLinkedQueue<>();
    private final MutableGeoBounds _bounds = new MutableGeoBounds(0, 0, 0, 0);

    protected final MapView mapView;
    protected final Context context;

    protected final Map<String, PointMapItem> controlPoints = new HashMap<>();
    protected final Map<PointMapItem, Integer> markerToIndex;
    protected final SortedMap<Integer, PointMapItem> indexToMarker;
    protected final SortedMap<Integer, PointMapItem> indexToMarker2; // only for control points

    protected ActionProviderInterface _actionProvider;

    /**
     * Editable state listener
     */
    public interface OnEditableChangedListener {
        void onEditableChanged(EditablePolyline polyline);
    }

    /**
     * Outside tools or classes that extend EditablePolyline or interact intimately with it may
     * choose to provide an implementation of this interface if they need to implement their own
     * EditActions, potentially overriding the default behaviors of the built in ones. One such
     * example is the Route class, which needs to shuttle navigation cues back and forth between
     * control points and way points.
     */
    public interface ActionProviderInterface {
        EditAction newExchangePointAction(int index, PointMapItem newItem,
                MapGroup addToOnSuccess);

        EditAction newInsertPointAction(PointMapItem item, int index,
                MapGroup addToOnSuccess);

        EditAction newMovePointAction(int index, GeoPointMetaData oldPoint,
                GeoPointMetaData newPoint);

        EditAction newRemovePointAction(int index);

        EditAction newRemoveMarkerAction(PointMapItem item);
    }

    public EditablePolyline(MapView mapView, final String uid) {
        this(mapView, MapItem.createSerialId(), new DefaultMetaDataHolder(),
                uid);

    }

    public EditablePolyline(MapView mapView, long serialId,
            MetaDataHolder metadata, String uid) {
        super(serialId, metadata, uid);

        this.mapView = mapView;
        this.context = mapView.getContext();
        addStyleBits(STYLE_STROKE_MASK);

        setMetaBoolean("removable", true);
        setMovable(true);

        super.setStrokeWeight(DEFAULT_STROKE_WEIGHT);

        this.indexToMarker = new TreeMap<>();
        this.indexToMarker2 = new TreeMap<>();
        this.markerToIndex = new IdentityHashMap<>();

        // if this object is removed, delete constituent parts
        this.addOnGroupChangedListener(this);
        this.addOnZOrderChangedListener(this);
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup newParent) {
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup oldParent) {
        if (item == this || item == getShapeMarker()) {
            // Do not delete contents when transferring groups
            if (getMetaBoolean("__groupTransfer", false))
                return;
            delete(item == getShapeMarker(), null);
        }
    }

    @Override
    public void onZOrderChanged(MapItem item) {
        // Keep center marker above shape
        final Marker center = getShapeMarker();
        if (center != null)
            center.setZOrder(item.getZOrder() - 1);
    }

    public void delete(MapGroup childItemGroup) {
        this.delete(true, childItemGroup);
    }

    private void delete(boolean self, MapGroup childItemGroup) {
        // remove deletion listeners for waypoints that are removed in general, and also the one
        // listening for this route specifically being removed
        removeOnGroupChangedListener(this);
        removeOnZOrderChangedListener(this);

        // clear waypoints
        clearPoints();

        // and marker
        final Marker marker = getMarker();
        if (marker != null)
            marker.removeFromGroup();

        // remove the route itself; has to happen, otherwise the route .cot file in storage
        // isn't removed. ATM removing the mapGroup it's in doesn't remove it.
        // this may have already happened if we're triggering from a delete event
        if (self) {
            MapGroup setParent = getGroup();
            if (setParent != null)
                setParent.removeItem(this);
        }

        if (childItemGroup != null) {
            final MapGroup parent = childItemGroup.getParentGroup();
            if (parent != null)
                parent.removeGroup(childItemGroup);
        }
    }

    /**
     * A Marker used to invoke the set's menu. Only visible when the set is editable.
     * 
     * @param marker the marker used for the shapes menu
     */
    protected void setShapeMarker(Marker marker) {
        _shapeMarker = marker;
        _shapeMarker.setMetaString("shapeName", getTitle());
        _shapeMarker.setMetaString("shapeUID", getUID());
        if (isClosed())
            _shapeMarker.setMetaBoolean("closed", true);
        else
            _shapeMarker.removeMetaData("closed");
        _shapeMarker.setPoint(getCenter());
        _shapeMarker.addOnGroupChangedListener(this);
        // _shapeMarker.setVisible(_editable);
    }

    public Marker getShapeMarker() {
        return _shapeMarker;
    }

    /**
     * Toggle the touchability of a route.
     */
    @Override
    public void setClickable(boolean state) {
        super.setClickable(state);
        final Set<PointMapItem> items = markerToIndex.keySet();
        for (final PointMapItem item : items)
            item.setClickable(state);
    }

    public void hideLabels(boolean state) {
        final Set<PointMapItem> items = markerToIndex.keySet();
        for (final PointMapItem item : items) {
            if (item instanceof Marker) {
                if (!state) {
                    ((Marker) item)
                            .setTextRenderFlag(Marker.TEXT_STATE_DEFAULT);
                } else {
                    ((Marker) item)
                            .setTextRenderFlag(Marker.TEXT_STATE_NEVER_SHOW);
                }
            }
        }
    }

    public void setLocked(boolean locked) {
        setClickable(!locked);
        final Set<PointMapItem> items = markerToIndex.keySet();
        for (final PointMapItem item : items) {
            if (locked) {
                // probably can be removed - but keep in just to make sure
                item.removeMetaData("movable");
                item.removeMetaData("removable");
            } else {
                //item.setMovable(true);
                //item.setMetaBoolean("removable", true);
            }
        }
    }

    /**
     * @deprecated Implementation moved to
     * {@link AbstractGLMapItem2#hitTest(MapRenderer3, HitTestQueryParameters)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    protected final int testPointsHit(MapView view, int startIdx, int endIdx,
            RectF hitRect, GeoBounds hitBox) {
        return 0;
    }

    /**
     * @deprecated Implementation moved to
     * {@link AbstractGLMapItem2#hitTest(MapRenderer3, HitTestQueryParameters)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    protected final GeoPoint testLinesHit(MapView view, int startIdx,
            int endIdx, Vector2D touch, GeoPoint gp) {
        return null;
    }

    /**
     * Provides a shape menu to be used when the shape is touched.  Can be overridden
     * by setting the "shapeMenu" metaString.
     * @return by default the drawing_shape_menu otherwise the metaString for "shapeMenu"
     */
    protected String getShapeMenu() {
        return getMetaString("shapeMenu", "menus/drawing_shape_menu.xml");
    }

    /**
     * Provides a shape menu to be used when the shape line is touched.  Can be overridden
     * by setting the "lineMenu" metaString.
     * @return by default the drawing_shape_line_menu otherwise the metaString for "lineMenu"
     */
    protected String getLineMenu() {
        return getMetaString("lineMenu", "menus/drawing_shape_line_menu.xml");
    }

    /**
     * Provides a shape menu to be used when the shape corner is touched.  Can be overridden
     * by setting the "cornerMenu" metaString.
     * @return by default the drawing_shape_corner_menu otherwise the metaString for "lineMenu"
     */
    protected String getCornerMenu() {
        return getMetaString("cornerMenu",
                "menus/drawing_shape_corner_menu.xml");
    }

    // TODO: fix for concave shapes?
    private GeoPointMetaData computeAveragePoint() {

        int size = _points.size();
        if (size == 0)
            return new GeoPointMetaData();

        // don't double count the first/last point for closed shapes
        if (_points.get(0).equals(_points.get(size - 1)))
            size--;

        GeoPoint avg = GeoCalculations
                .computeAverage(GeoPointMetaData.unwrap(_points.toArray(
                        new GeoPointMetaData[0])), 0, size, wrap180());

        // XXX - look up the altitude of the computed average point.
        return ElevationManager.getElevationMetadata(avg);
    }

    public final Marker getMarker() {
        return _shapeMarker;
    }

    public void setUndoable(Undoable undo) {
        _undo = undo;
    }

    public Undoable getUndoable() {
        return _undo == null ? _dummyUndo : _undo;
    }

    private final Undoable _dummyUndo = new Undoable() {
        @Override
        public void undo() {
        }

        @Override
        public boolean run(EditAction action) {
            return action.run();
        }
    };

    /**
     * Remove points that are removed from ATAK from editable polylines they're in.
     */
    private final MapItem.OnGroupChangedListener removedListener = new MapItem.OnGroupChangedListener() {
        @Override
        public void onItemAdded(MapItem item, MapGroup newParent) {
        }

        @Override
        public void onItemRemoved(MapItem item, MapGroup oldParent) {
            if (!(item instanceof PointMapItem))
                return;

            if (!EditablePolyline.this.markerToIndex
                    .containsKey(item))
                return;

            final PointMapItem toRemove = (PointMapItem) item;
            EditAction removeAction = null;
            if (_actionProvider != null) {
                removeAction = _actionProvider.newRemoveMarkerAction(toRemove);
            } else {
                RemoveMarkerAction ra = new RemoveMarkerAction(toRemove);
                if (ra.deletedFrom == null) {
                    ra.deletedFrom = oldParent;
                }

                removeAction = ra;
            }

            if (_undo != null)
                _undo.run(removeAction);
        }
    };

    public boolean isClosed() {
        return _closed;
    }

    protected void setClosed(boolean closed) {
        _closed = closed; // TODO: remove extra flag?

        Marker center = getShapeMarker();
        if (closed) {
            addStyleBits(STYLE_CLOSED_MASK);
            setMetaBoolean("closed", true);
            if (center != null)
                center.setMetaBoolean("closed", true);
        } else {
            removeStyleBits(STYLE_CLOSED_MASK);
            removeMetaData("closed");
            if (center != null)
                center.removeMetaData("closed");
        }
    }

    protected void setBulkOperation(boolean bulk) {
        //Log.d(TAG, "setBulkOperation = " + bulk, new Exception());
        bulkOperationInProgress = bulk;
    }

    public boolean isBulkOperation() {
        //Log.d(TAG, "isBulkOperation = " + bulkOperationInProgress, new Exception());
        return bulkOperationInProgress || hasMetaValue("dragInProgress");
    }

    /**
     * Move a closed EditablePolyline from the previous center point to a new point
     * 
     * @param oldPoint the old point to act as the center point
     * @param newPoint the new point to act as the center point, important to shift the entire polygon by a specific amount.
     */
    public void moveClosedSet(GeoPointMetaData oldPoint,
            GeoPointMetaData newPoint) {
        // XXX - Although it seems like we could just get the R&B between the
        // old and new point and use those values for every point, this doesn't
        // work well when moving a large shape around the globe; ends up causing
        // significant distortion. So while it's less efficient, the R&B between
        // the center point and each shape point is calculated
        synchronized (this) {
            final int size = getNumPoints();
            setBulkOperation(true);
            for (int i = 0; i < size; i++) {
                GeoPointMetaData p = _points.get(i);
                double d = GeoCalculations.distanceTo(oldPoint.get(), p.get());
                double a = GeoCalculations.bearingTo(oldPoint.get(), p.get());
                double alt = p.get().getAltitude();
                String altSrc = p.getAltitudeSource();
                p.set(GeoCalculations.pointAtDistance(newPoint.get(), a, d));
                if (altSrc.equals(GeoPointMetaData.USER)) {
                    // Keep manually entered altitudes (ATAK-8963)
                    p.set(new GeoPoint(p.get().getLatitude(),
                            p.get().getLongitude(), alt))
                            .setAltitudeSource(GeoPointMetaData.USER);
                }
                PointMapItem item = this.indexToMarker.get(i);
                if (item != null) {
                    item.setPoint(p.get());
                    item.copyMetaData(p.getMetaData());
                }
            }
            setBulkOperation(false);
        }
        // notify that the points have changed
        this.onPointsChanged();
    }

    /**
     * Returns the number of points for a given polyline.
     * @return the number, or zero if there are no points.
     */
    public synchronized int getNumPoints() {
        if (_points == null)
            return 0;
        else
            return _points.size();
    }

    /**
     * For a given index, return the point.   If the index falls outside of the bounds of the
     * number of points, return the closest point to either end of the list.
     * @param index the index
     * @return the point.
     */
    public synchronized GeoPointMetaData getPoint(int index) {
        index = MathUtils.clamp(index, 0, _points.size() - 1);
        return index >= 0 && index < _points.size() ? _points.get(index) : null;
    }

    /**
     * Given a point on a Polyline, return the index of the point or -1 if the point is not found.
     * @param point the point to find
     * @return the index of the point, -1 if not found.
     */
    public synchronized int getIndexOfPoint(GeoPointMetaData point) {
        return _points.indexOf(point);
    }

    /**
     * Given a MapItem on a Polyline, return the index of the point or -1 if the map item is not found.
     * @param pmi the map item to find
     * @return the index of the map item, -1 if not found.
     */
    public synchronized int getIndexOfPoint(PointMapItem pmi) {
        return pmi != null ? getIndexOfPoint(pmi.getGeoPointMetaData()) : -1;
    }

    /**
     * Given an index on the EditablePolyline, return the PointMapItem at that position or null if no 
     * PointMapItem exists.
     */
    public synchronized PointMapItem getMarker(int index) {
        return this.indexToMarker.get(index);
    }

    /**
     * Given an index on the EditablePolyline, return the PointMapItem at that position, regardless
     * of whether it is a way point or a control point.
     * @param index Position
     * @return PointMapItem at position index
     */
    public synchronized PointMapItem getPointMapItem(int index) {
        return getPointMapItemNoSync(index);
    }

    private PointMapItem getPointMapItemNoSync(int index) {
        PointMapItem result = null;

        // Check the map of way points first
        if (this.indexToMarker != null)
            result = this.indexToMarker.get(index);

        // Check map of control points, if necessary
        if (result == null) {
            if (this.indexToMarker2 != null)
                result = this.indexToMarker2.get(index);
        }

        return result;
    }

    /**
     * Gets a defensive copy of all of the PointMapItems contained in the line.
     *
     * @return
     */
    public synchronized List<PointMapItem> getPointMapItems() {
        return Arrays.asList(getPointMapItemArray());
    }

    public synchronized PointMapItem[] getPointMapItemArray() {
        int itemCount = getNumPoints();
        PointMapItem[] items = new PointMapItem[itemCount];
        for (int i = 0; i < itemCount; i++)
            items[i] = getPointMapItemNoSync(i);
        return items;
    }

    /**
     * Returns the index of a way point
     * For control points, see {@link #getIndexOfPoint(PointMapItem)}
     * @param point Way point
     * @return Way point index
     */
    public synchronized int getIndexOfMarker(PointMapItem point) {
        Integer retval = this.markerToIndex.get(point);
        if (retval == null)
            return -1;
        return retval;
    }

    // TODO: deprecate / remove these (rely on polyline's style / basiclinestyle instead)
    public synchronized int getColor() {
        return _linkColor;
    }

    @Override
    public void setColor(int color) {
        synchronized (this) {
            _linkColor = color;
            setStrokeColor(color);

            for (PointMapItem m : this.markerToIndex.keySet()) {
                // This is a map, so need to use _markers.values()
                // updateColor(_markers.get(i));
                updateColor(_linkColor, m);
            }
        }
        setMetaInteger("color", color);
        if (_shapeMarker != null)
            _shapeMarker.setColor(color);
        this.refresh(this.mapView.getMapEventDispatcher(), null,
                this.getClass());
    }

    @Override
    public void setStrokeColor(int strokeColor) {
        super.setStrokeColor(strokeColor);

        _linkColor = strokeColor;

        if (_shapeMarker != null)
            _shapeMarker.setColor(strokeColor);
    }

    @Override
    public void setStrokeWeight(double weight) {
        if (Double.compare(weight, getStrokeWeight()) != 0) {
            super.setStrokeWeight(weight);
            this.refresh(this.mapView.getMapEventDispatcher(), null,
                    this.getClass());
        }
    }

    /**
     * STYLE_SOLID = 0, STYLE_DOTTED = 1, STYLE_DASHED = 2 and STYLE_OUTLINED = 3
     */
    // @Deprecated
    public void setLineStyle(int style) {
        setBasicLineStyle(BASIC_LINE_STYLE_SOLID);
        removeStyleBits(STYLE_OUTLINE_STROKE_MASK);

        if (style == 1)
            setBasicLineStyle(BASIC_LINE_STYLE_DOTTED);
        else if (style == 2)
            setBasicLineStyle(BASIC_LINE_STYLE_DASHED);
        else if (style == 3) {
            setBasicLineStyle(BASIC_LINE_STYLE_DOTTED);
            addStyleBits(STYLE_OUTLINE_STROKE_MASK);
        }
    }

    // @Deprecated
    public int getLineStyle() {
        if (getBasicLineStyle() == BASIC_LINE_STYLE_DOTTED)
            return 1;
        else if (getBasicLineStyle() == BASIC_LINE_STYLE_DASHED)
            return 2;
        else if ((getStyle() & STYLE_OUTLINE_STROKE_MASK) != 0)
            return 3;

        return 0;
    }

    // @Deprecated
    public void setFilled(boolean filled) {
        _filled = filled;

        if (filled)
            addStyleBits(STYLE_FILLED_MASK);
        else
            removeStyleBits(STYLE_FILLED_MASK);

        this.refresh(this.mapView.getMapEventDispatcher(), null,
                this.getClass());
    }

    /**
     * Gets the current ActionProvider implementation if a non-default one has been specified.
     *
     * @return
     */
    public ActionProviderInterface getActionProvider() {
        return _actionProvider;
    }

    // @Deprecated
    public boolean getFilled() {
        return _filled;
    }

    @Override
    public PointMapItem getAnchorItem() {
        return _shapeMarker;
    }

    @Override
    public GeoPointMetaData getCenter() {
        GeoPointMetaData gp = computeAveragePoint();

        // Maintain user-entered center marker elevation
        if (_shapeMarker != null) {
            GeoPointMetaData prev = _shapeMarker.getGeoPointMetaData();
            if (prev.getAltitudeSource().equals(GeoPointMetaData.USER))
                gp = GeoPointMetaData.wrap(
                        new GeoPoint(gp.get().getLatitude(),
                                gp.get().getLongitude(),
                                prev.get().getAltitude(), gp.get().getCE(),
                                gp.get().getLE()),
                        gp.getGeopointSource(), prev.getAltitudeSource());
        }

        return gp;
    }

    public GeoPointMetaData getAvgAltitude() {
        if (!_avgAltitude.get().isAltitudeValid()) {
            updateAltitudeStatistics();
        }

        return _avgAltitude;
    }

    public GeoPointMetaData getMaxAltitude() {
        if (!_maxAltitude.get().isAltitudeValid()) {
            updateAltitudeStatistics();
        }

        return _maxAltitude;
    }

    public GeoPointMetaData getMinAltitude() {
        if (!_minAltitude.get().isAltitudeValid()) {
            updateAltitudeStatistics();
        }

        return _minAltitude;
    }

    @Override
    public void setFillColor(int color) {
        // Update filled state
        if (!getFilled() && Color.alpha(color) > 0)
            setFilled(true);
        else if (getFilled() && Color.alpha(color) <= 0)
            setFilled(false);
        super.setFillColor(color);
    }

    @Override
    protected void onVisibleChanged() {
        boolean visible = getVisible();
        synchronized (this) {
            // TODO: leave edit mode if you hide the route you were editing?

            // Make constituent waypoints visible only if at least one route they're in is still
            // visible
            for (PointMapItem point : this.markerToIndex.keySet())
                point.setVisible(visible);

            if (_shapeMarker != null)
                _shapeMarker.setVisible(visible);
        }
        super.onVisibleChanged();
    }

    @Override
    protected void onPointsChanged() {
        super.onPointsChanged();

        // If moving the entire EditablePolyline then don't listen to the changed points until the
        // end of the move
        if (!isBulkOperation()) {

            synchronized (this) {
                computeBounds(false);
                if (_bounds.crossesIDL())
                    computeBounds(true);

                // update the set's marker
                if (_shapeMarker != null)
                    _shapeMarker.setPoint(getCenter());
            }
            // Change in points might mean we have new / removed altitude values as well
            onAltChanged();

            // XXX - persist event is required for persistence, refresh
            // signals metadata change

            // let others know that we have changed.
            this.refresh(this.mapView.getMapEventDispatcher(), null,
                    this.getClass());
        }
    }

    private void computeBounds(boolean wrap180) {
        boolean continuousScrollEnabled = this.mapView
                .isContinuousScrollEnabled();
        _bounds.setWrap180(continuousScrollEnabled);
        if (_points.isEmpty()) {
            _bounds.clear();
            return;
        }
        GeoPoint p;
        double N, S, E, W;

        p = _points.get(0).get();
        N = S = p.getLatitude();
        E = W = p.getLongitude();
        if (wrap180 && p.getLongitude() < 0)
            E = W = p.getLongitude() + 360;
        double minAlt = GeoPoint.UNKNOWN;
        double maxAlt = GeoPoint.UNKNOWN;

        int numPoints = _points.size();
        if (!isClosed())
            numPoints--;
        for (int i = 0; i <= numPoints; ++i) {
            boolean lastPoint = i == numPoints;
            p = _points.get(isClosed() && lastPoint ? 0 : i).get();

            double lat = p.getLatitude();
            double lon = p.getLongitude();
            double alt = p.getAltitude();
            if (wrap180 && lon < 0)
                lon += 360;

            if (lat > N)
                N = lat;
            else if (lat < S)
                S = lat;
            if (lon > E)
                E = lon;
            else if (lon < W)
                W = lon;

            if (GeoPoint.isAltitudeValid(alt)) {
                if (!GeoPoint.isAltitudeValid(minAlt) || alt < minAlt)
                    minAlt = alt;
                if (!GeoPoint.isAltitudeValid(maxAlt) || alt > maxAlt)
                    maxAlt = alt;
            }
        }
        if (wrap180 && E > 180)
            E -= 360;
        _bounds.set(S, W, N, E);
        _bounds.setMinAltitude(minAlt);
        _bounds.setMaxAltitude(maxAlt);
    }

    private void onAltChanged() {
        _avgAltitude.set(GeoPoint.UNKNOWN_POINT);
        _minAltitude.set(GeoPoint.UNKNOWN_POINT);
        _maxAltitude.set(GeoPoint.UNKNOWN_POINT);
    }

    private static double getAltStatValue(double alt) {
        if (!GeoPoint.isAltitudeValid(alt))
            return 0.0d;
        // XXX - shouldn't we be checking to see the reference?
        return alt;
    }

    private synchronized void updateAltitudeStatistics() {
        // TODO: only calculating on demand so shouldn't have threading issues during route creation
        // any more. could still have threading issues if a survey obstacle is changed while
        // statistics
        // are being calculated though? should be far less likely though.
        int numPoints = getNumPoints();
        if (numPoints > 0) {
            GeoPointMetaData p;

            double sum;
            double v;
            double minAlt;
            GeoPointMetaData min;
            double maxAlt;
            GeoPointMetaData max;

            p = _points.get(0);
            v = getAltStatValue(p.get().getAltitude());
            minAlt = v;
            min = p;
            maxAlt = v;
            max = p;

            sum = v;
            final AltitudeReference altRef = p.get().getAltitudeReference();
            //final String altSrc = p.getAltitudeSource();

            for (int i = 1; i < numPoints; i++) {
                p = _points.get(i);
                v = getAltStatValue(p.get().getAltitude());
                if (v < minAlt) {
                    minAlt = v;
                    min = p;
                } else if (v > maxAlt) {
                    maxAlt = v;
                    max = p;
                }
                sum += v;
            }

            _avgAltitude.set(
                    new GeoPoint(Double.NaN, Double.NaN, sum / numPoints,
                            altRef, GeoPoint.UNKNOWN, GeoPoint.UNKNOWN))
                    .setAltitudeSource(GeoPointMetaData.CALCULATED);

            _minAltitude.set(min);
            _maxAltitude.set(max);
        }
    }

    /**
     * Reverse the entire polyline - mostly used in the case of turning an infil into an exfil.
     */
    public synchronized void reverse() {
        Collections.reverse(_points);

        final int maxIndex = _points.size() - 1;

        // Reverse markers too
        this.indexToMarker.clear();

        Iterator<Map.Entry<PointMapItem, Integer>> iter = this.markerToIndex
                .entrySet().iterator();
        Map.Entry<PointMapItem, Integer> entry;
        while (iter.hasNext()) {
            entry = iter.next();
            Integer v = entry.getValue();
            if (v != null) {
                v = maxIndex - v;
                this.indexToMarker.put(v, entry.getKey());
                entry.setValue(v);
            }
        }
    }

    private synchronized void updateColor(int color) {
        for (PointMapItem item : this.markerToIndex.keySet()) {
            updateColor(color, item);
        }
    }

    private static void updateColor(int color, PointMapItem point) {
        if (!(point instanceof Marker))
            return;
        final Marker marker = (Marker) point;
        if (marker.getIcon() == null
                || marker.getIcon().getImageUri(Icon.STATE_DEFAULT) == null)
            return;

        if (Route.WAYPOINT_TYPE.equals(point.getType())) {
            // TODO: when targets get the hostile icon, need to filter it out to not recolor it.

            // onIconChanged doesn't get fired off because the icon is still the same object!
            // Need to build a new one
            Icon.Builder b = new Icon.Builder();
            b.setImageUri(0, marker.getIcon().getImageUri(Icon.STATE_DEFAULT));
            b.setAnchor(16, 16);
            b.setColor(0, color);

            if (color != 0)
                marker.setIcon(b.build());

            // set color, otherwise another component will overwrite it
            point.setMetaInteger("color", color);
        }
    }

    /**
     * True if the point is in this route
     * 
     * @param item the map item to check against.
     * @return true if the route contains the map item.
     */
    public boolean hasMarker(final PointMapItem item) {
        // or do by uid?
        return this.markerToIndex.containsKey(item);
    }

    /**
     * Temporarily change the transparency of the route's color. This is not saved to the route's
     * cot event.
     * 
     * @param alpha New alpha value
     */
    public void setAlpha(int alpha) {
        int color = Color.argb(alpha, Color.red(_linkColor),
                Color.green(_linkColor),
                Color.blue(_linkColor));
        updateColor(color);
        setStrokeColor(color);
    }

    /**
     * Changes route's transparency back to it's normal value
     */
    public void resetAlpha() {
        int color = Color.argb(255, Color.red(_linkColor),
                Color.green(_linkColor),
                Color.blue(_linkColor));
        updateColor(color);
        setStrokeColor(color);
    }

    @Override
    public boolean getEditable() {
        return _editable;
    }

    /**
     * Styles this route to indicate that it's the currently editable route, and also makes it's
     * invisible handles visible so they can be interacted with.
     * 
     * @param editable Whether editable styling should be enabled or disabled.
     */
    @Override
    public void setEditable(boolean editable) {
        if (editable == _editable)
            return;

        _editable = editable;

        setMetaBoolean("drag", editable);
        if (!editable)
            setMetaString("menu", getShapeMenu());

        resetAlpha();

        if (editable) {
            this.setZOrder(this.getZOrder() - 50000);
            _lockedZOrder = true;
        } else {
            _lockedZOrder = false;
            this.setZOrder(this.getZOrder() + 50000);
        }

        // Inform listeners
        for (OnEditableChangedListener l : _onEditableChanged) {
            l.onEditableChanged(this);
        }
    }

    public boolean shouldDisplayVertices(double mapScale) {
        return mapScale >= DEFAULT_MIN_RENDER_VERTS;
    }

    @Deprecated
    private boolean forceAltitude = false;

    /**
     * Forces the polyline to be rendered at altitude independent of the height and also turns off
     * any extrusion.
     * @param forceAltitude true if we are going to force the polyline to be at altitude
     * @deprecated
     */
    @Deprecated
    public void setAbsoluteKMLElev(final boolean forceAltitude) {
        this.forceAltitude = forceAltitude;
    }

    @Override
    public void setZOrder(double zOrder) {
        // do not allow for the zorder to be modified if the 
        // polyline is currently in edit mode
        if (!_lockedZOrder) {
            super.setZOrder(zOrder);
            final Set<PointMapItem> items = markerToIndex.keySet();
            for (final PointMapItem item : items)
                item.setZOrder(zOrder - 1);
            this.refresh(this.mapView.getMapEventDispatcher(), null,
                    this.getClass());
        }
    }

    private final PointMapItem.OnPointChangedListener pointChanged = new PointMapItem.OnPointChangedListener() {

        @Override
        public void onPointChanged(PointMapItem item) {
            // Update the point list that draws the underlying shape
            synchronized (EditablePolyline.this) {
                Integer index = EditablePolyline.this.markerToIndex.get(item);
                if (index == null) {
                    Log.e(TAG, "Item with UID " + item.getUID()
                            + " not found in line");
                    return;
                }

                GeoPoint gp = item.getPoint();
                GeoPointMetaData gpm = GeoPointMetaData.wrap(gp)
                        .setAltitudeSource(item.getMetaString(
                                GeoPointMetaData.ALTITUDE_SOURCE,
                                GeoPointMetaData.UNKNOWN))
                        .setGeoPointSource(item.getMetaString(
                                GeoPointMetaData.GEOPOINT_SOURCE,
                                GeoPointMetaData.GEOPOINT_SOURCE));

                EditablePolyline.this._points.set(index, gpm);
            }
            onPointsChanged();
        }
    };

    public boolean removeMarker(PointMapItem item) {
        return removeMarker(item, true);
    }

    /**
     * Removes a waypoint from the route requested.
     * 
     * @param item the waypoint to be removed
     * @param notExchangingPoints False if we're about to add a point to replace the one we're now
     *            removing. Turns off behavior which will be made obsolete once the insertion
     *            occurs.
     * @return true if a point was removed
     */

    protected boolean removeMarker(PointMapItem item,
            boolean notExchangingPoints) {
        int index = getIndexOfMarker(item);

        if (index == -1)
            return false;

        removeControlPoint(item);

        return removeMarker(index, notExchangingPoints, false);
    }

    public boolean removeMarker(int index) {
        return removeMarker(index, true, false);
    }

    private synchronized void removeControlPoint(PointMapItem item) {
        if (item != null)
            controlPoints.remove(item.getUID());
    }

    /**
     * Removes a waypoint from the route requested.
     * 
     * @param index index of item to delete
     * @param notExchangingPoints False if we're about to add a point to replace the one we're now
     *            removing. Turns off behavior which will be made obsolete once the insertion
     *            occurs.
     * @return true if a point was removed
     */

    protected final synchronized boolean removeMarker(final int index,
            final boolean notExchangingPoints,
            final boolean partOfRemovePoint) {

        if (removeMarkerNoSync(index) == null)
            return false;

        // If called as part of removePoint, removePoint will call this
        // so we don't want to call it too!
        if (!partOfRemovePoint)
            onPointsChanged();

        // There are no points left, remove this route.
        // TODO: or just assume this never happens?
        if (getNumPoints() < 2 && notExchangingPoints)
            this.delete(true, null);

        return true;
    }

    protected PointMapItem removeMarkerNoSync(int index) {
        PointMapItem point = this.indexToMarker.remove(index);
        if (point == null)
            return null;

        // Remove it from our list
        this.markerToIndex.remove(point);
        this.removeListeners(point);

        // Remove waypoint from the map
        if (point.getType().equals(Route.WAYPOINT_TYPE))
            point.removeFromGroup();

        return point;
    }

    public synchronized void removePoint(int index) {
        // Remove marker as well if it exists
        if (getMarker(index) != null)
            removeMarker(index, true, true);

        // clear out the control point that may represent the index
        PointMapItem cp = indexToMarker2.remove(index);
        if (cp != null)
            removeControlPoint(cp);

        // remove marker may cause self deletion when there are fewer than 2
        // points e.g. user clicks undo immediately after starting a route
        if (_points.size() < 1)
            return;

        _points.remove(index);

        // Decrement marker indexes so they're still accurate
        SortedMap<Integer, PointMapItem> tail = this.indexToMarker
                .tailMap(index);
        SortedMap<Integer, PointMapItem> updatedTail = new TreeMap<>();
        Iterator<Map.Entry<Integer, PointMapItem>> entryIter = tail.entrySet()
                .iterator();
        Map.Entry<Integer, PointMapItem> entry;
        while (entryIter.hasNext()) {
            entry = entryIter.next();
            int ind = entry.getKey() - 1;
            this.markerToIndex.put(entry.getValue(), ind);
            updatedTail.put(ind, entry.getValue());
            entryIter.remove();
        }
        this.indexToMarker.putAll(updatedTail);

        // Decrement control marker indexes as well
        tail = this.indexToMarker2.tailMap(index);
        entryIter = tail.entrySet().iterator();
        updatedTail.clear();
        while (entryIter.hasNext()) {
            entry = entryIter.next();
            updatedTail.put(entry.getKey() - 1, entry.getValue());
            entryIter.remove();
        }
        this.indexToMarker2.putAll(updatedTail);

        onPointsChanged();
    }

    /**
     * Adds a marker to an existing route at the end.   This method requires that a route 
     * be placed on the map already or will be placed on the map in the future.
     * @param item the point map item to add.
     */
    public synchronized boolean addMarker(PointMapItem item) {
        final int pointIndex = this.getNumPoints();

        addPointNoSync(pointIndex, item.getGeoPointMetaData());
        setMarkerNoSync(pointIndex, item);
        return true;
    }

    /**
     * Adds a marker to an existing route at a specific index.   This method requires that a route 
     * be placed on the map already or will be placed on the map in the future.
     * @param pointIndex the position to add the marker after.
     * @param item the point map item to add.
     */
    public synchronized boolean addMarker(int pointIndex, PointMapItem item) {
        // Add point where the marker is to the line
        addPointNoSync(pointIndex, item.getGeoPointMetaData());
        setMarkerNoSync(pointIndex, item);
        return true;
    }

    /**
     * Bulk insert of waypoints.   Much more efficient then the corresponding addMarker.
     * Adds a set marker to an existing route at a specific index.   This method requires that a route 
     * be placed on the map already or will be placed on the map in the future.
     * @param pointIndex the position to add the marker after.
     * @param items the array of point map items to add.
     * 
     */
    public synchronized boolean addMarkers(int pointIndex,
            PointMapItem[] items) {

        // This is a completely empty route, and I am adding the very first million or so markers,
        // they are in order, so I can really skip the renumbering logic later to make sure marker 
        // indicies are accurate
        if (indexToMarker.size() == 0 && pointIndex == 0) {
            initialBulkLoad = true;
        }

        setBulkOperation(true);
        for (int i = 0; i < items.length; ++i) {
            if (items[i] != null)
                addMarker(pointIndex + i, items[i]);
        }
        setBulkOperation(false);

        initialBulkLoad = false;

        onPointsChanged();

        return true;

    }

    public synchronized void setMarker(int pointIndex,
            final PointMapItem item) {
        this.setMarkerNoSync(pointIndex, item);

    }

    /**
     * Control Points are special.
     */
    private synchronized PointMapItem getControlPoint(final String uid) {
        return controlPoints.get(uid);
    }

    protected void setMarkerNoSync(int pointIndex, final PointMapItem item) {

        // TODO: move b-m-p-w stuff to Route?
        // Make waypoints visible only if at least one route they're in is visible
        //final boolean visible = this.getVisible();
        final String type = item.getType();

        updateColor(_linkColor, item);

        // if editing, make sure that the marker is popped to the top when it is added
        if (_editable) {
            item.setZOrder(this.getZOrder() - 50000);
            item.setMovable(true);
            item.setMetaBoolean("removable", true);
        }

        // Remove existing way point if any
        removeMarkerNoSync(pointIndex);

        /** 
         * Only for Waypoints?
         */
        if (type != null && type.equals(Route.CONTROLPOINT_TYPE)) {
            // Add new control point
            this.indexToMarker2.put(pointIndex, item);
            controlPoints.put(item.getUID(), item);
        } else if (type != null && type.equals(Route.WAYPOINT_TYPE)) {

            item.setVisible(getVisible());

            // Add listeners
            if (!this.markerToIndex.containsKey(item))
                this.addListeners(item);

            // Remove existing control point if any
            if (this.indexToMarker2.containsKey(pointIndex)) {
                PointMapItem old = this.indexToMarker2.remove(pointIndex);
                controlPoints.remove(old.getUID());
            }

            // Add new way point
            this.indexToMarker.put(pointIndex, item);
            this.markerToIndex.put(item, pointIndex);

            // Refresh z-order so way point isn't below the shape
            setZOrder(getZOrder());

            // Make draggable if editing
            if (getEditable())
                item.setMetaBoolean("drag", true);

            // TODO: should this be done through some onPointsChanged listener?

            if (!isBulkOperation())
                this.refresh(this.mapView.getMapEventDispatcher(), null,
                        this.getClass());
        }
    }

    public boolean addPoint(GeoPointMetaData point) {
        return addPoint(getNumPoints(), point);
    }

    public synchronized boolean addPoint(int index, GeoPointMetaData point) {
        return addPointNoSync(index, point);
    }

    protected boolean addPointNoSync(int index, GeoPointMetaData point) {
        if (point == null) {
            Log.w(TAG, "Attempted to add null point to shape: " + getTitle(),
                    new Throwable());
            return false;
        }

        _points.add(index, point);

        if (initialBulkLoad) {
            return true;
        }

        // Increment marker indexes so they're still accurate
        SortedMap<Integer, PointMapItem> tail = this.indexToMarker
                .tailMap(index);
        SortedMap<Integer, PointMapItem> updatedTail = new TreeMap<>();
        Iterator<Map.Entry<Integer, PointMapItem>> entryIter = tail.entrySet()
                .iterator();
        Map.Entry<Integer, PointMapItem> entry;
        while (entryIter.hasNext()) {
            entry = entryIter.next();
            int ind = entry.getKey() + 1;
            this.markerToIndex.put(entry.getValue(), ind);
            updatedTail.put(ind, entry.getValue());
            entryIter.remove();
        }
        this.indexToMarker.putAll(updatedTail);

        // Increment control marker indexes as well
        tail = this.indexToMarker2.tailMap(index);
        entryIter = tail.entrySet().iterator();
        updatedTail.clear();
        while (entryIter.hasNext()) {
            entry = entryIter.next();
            updatedTail.put(entry.getKey() + 1, entry.getValue());
            entryIter.remove();
        }
        this.indexToMarker2.putAll(updatedTail);

        onPointsChanged();
        return true;
    }

    /**
     * Set a point in this polyline
     * @param index Point index
     * @param point Point location
     * @param skipIfEquals True to skip operation if the point hasn't changed
     * @return True if successful
     */
    public boolean setPoint(int index, GeoPointMetaData point,
            boolean skipIfEquals) {
        if (point == null)
            throw new NullPointerException();

        synchronized (this) {
            final int size = _points.size();
            if (index < 0 || index >= size)
                return true; // XXX - legacy always returns true
            final GeoPointMetaData p = _points.get(index);
            if (skipIfEquals && point.equals(p))
                return true; // XXX - legacy always returns true

            final PointMapItem pmi = this.indexToMarker.get(index);
            if (pmi != null) {
                pmi.setPoint(point);
            } else {
                final PointMapItem pmic = this.indexToMarker2.get(index);
                if (pmic != null) {
                    pmic.setPoint(point.get());
                    pmic.copyMetaData(point.getMetaData());
                }
                _points.set(index, point);
            }
        }

        this.onPointsChanged();

        return true;
    }

    public boolean setPoint(int index, GeoPointMetaData point) {
        return setPoint(index, point, true);
    }

    protected void removeListeners(PointMapItem item) {
        item.removeOnPointChangedListener(this.pointChanged);
        item.removeOnGroupChangedListener(this.removedListener);
    }

    protected void addListeners(PointMapItem item) {
        item.addOnPointChangedListener(this.pointChanged);
        item.addOnGroupChangedListener(this.removedListener);
    }

    protected void clearPoints() {
        this.clearPointsImpl(false);
    }

    public void clear() {
        this.clearPointsImpl(true);
    }

    /**
     * Used in very specific cases where this operation will be chained
     * with other operations that will ultimately call notify.   For 
     * standard usage of clear, call clear()
     */
    public void clearWithoutNotify() {
        this.clearPointsImpl(false);
    }

    protected void clearPointsImpl(boolean notifyPointsChanged) {
        List<PointMapItem> toRemove;
        synchronized (this) {
            // clear out the waypoints first...
            toRemove = new ArrayList<>(this.markerToIndex.keySet());
            _points.clear();
            this.markerToIndex.clear();
            this.indexToMarker.clear();
            this.indexToMarker2.clear();
        }

        for (PointMapItem item : toRemove) {
            // remove deletion listener
            removeListeners(item);

            // TODO: move to routes?
            if (item.getType().equals(Route.WAYPOINT_TYPE))
                item.removeFromGroup();
        }

        if (notifyPointsChanged)
            this.onPointsChanged();
    }

    /**
     * This should be considered read only for 99% of use cases, in order to allow common
     * functionality to work instead of needing to reimplement things like fine position of points
     * on an editable polyline for every subclass.
     * 
     * @return the uid key for an associated set
     */
    public static String getUIDKey() {
        return "assocSetUID";
    }

    /**
     * Returns the title of the editable polyline.
     * @return the title
     */
    @Override
    public String getTitle() {
        return _title;
    }

    @Override
    public void setTitle(String title) {
        super.setTitle(title);
        _title = title;
        if (_shapeMarker != null)
            _shapeMarker.setMetaString("shapeName", title);
        setLineLabel(title);
        this.refresh(this.mapView.getMapEventDispatcher(), null,
                this.getClass());
    }

    /**
     * Add a points property listener
     * 
     * @param listener the listener
     */
    public void addOnEditableChangedListener(
            OnEditableChangedListener listener) {
        if (!_onEditableChanged.contains(listener))
            _onEditableChanged.add(listener);

    }

    /**
     * Remove a points property listener
     * 
     * @param listener the listener
     */
    public void removeOnEditableChangedListener(
            OnEditableChangedListener listener) {
        _onEditableChanged.remove(listener);
    }

    /**
     * Produce a CoT message that represents the Route
     */
    protected CotEvent toCot() {

        CotEvent event = new CotEvent();
        event.setType(getType());

        CoordinatedTime time = new CoordinatedTime();
        event.setTime(time);
        event.setStart(time);
        event.setStale(time.addDays(1));

        if (getUID() == null || getUID().length() == 0) {
            Log.d(TAG, "XXX: empty or null uid encountered");
            event.setUID(UUID.randomUUID().toString());
        } else {
            event.setUID(getUID());
        }
        event.setVersion("2.0");
        event.setHow("h-e");

        //set the point to the first waypoint

        CotDetail detail = new CotDetail("detail");
        event.setDetail(detail);

        // For each of the nodes in the route, create a new link
        GeoPointMetaData[] points;
        PointMapItem[] markers;
        synchronized (this) {
            points = getMetaDataPoints();
            markers = new PointMapItem[points.length];
            for (int i = 0; i < points.length; i++)
                markers[i] = getPointMapItemNoSync(i);
        }
        CotDetail firstLink = null;
        for (int i = 0; i < points.length; i++) {
            GeoPointMetaData p = points[i];
            if (p == null)
                continue;
            final CotDetail link = new CotDetail("link");
            link.setAttribute("point", CotPoint.decimate(p.get()));

            if (firstLink == null)
                firstLink = link;

            PointMapItem m = markers[i];
            if (m != null) {
                final String uid = m.getUID();
                link.setAttribute("type", m.getType());
                link.setAttribute("uid", uid);
                link.setAttribute("relation", "c");
                link.setAttribute("callsign",
                        m.getMetaString("callsign", ""));
                link.setAttribute("remarks",
                        m.getMetaString("remarks", ""));
            }
            detail.addChild(link);
        }

        // If it's closed, represent that in CoT by making it physically closed
        if (isClosed())
            detail.addChild(firstLink);

        return event;
    }

    protected Folder toKml() {

        try {
            // style element
            Style style = new Style();
            IconStyle istyle = new IconStyle();
            istyle.setColor(KMLUtil.convertKmlColor(getStrokeColor()));

            if (isClosed()) {
                //set white pushpin and Google Earth will tint based on color above
                com.ekito.simpleKML.model.Icon icon = new com.ekito.simpleKML.model.Icon();
                String whtpushpin = context
                        .getString(R.string.whtpushpin);
                icon.setHref(whtpushpin);

                istyle.setIcon(icon);

            }
            style.setIconStyle(istyle);

            LineStyle lstyle = new LineStyle();
            lstyle.setColor(KMLUtil.convertKmlColor(getStrokeColor()));
            lstyle.setWidth((float) getStrokeWeight());
            style.setLineStyle(lstyle);

            PolyStyle pstyle = new PolyStyle();
            pstyle.setColor(KMLUtil.convertKmlColor(getFillColor()));
            pstyle.setFill(determineIfFilled());
            pstyle.setOutline(1);
            style.setPolyStyle(pstyle);

            String styleId = KMLUtil.hash(style);
            style.setId(styleId);

            // Folder element containing styles, shape and label
            Folder folder = new Folder();
            folder.setName(kmlFolderName());

            List<StyleSelector> styles = new ArrayList<>();
            styles.add(style);
            folder.setStyleSelector(styles);
            List<Feature> folderFeatures = new ArrayList<>();
            folder.setFeatureList(folderFeatures);

            List<Data> dataList = kmlDataList();
            ExtendedData edata = new ExtendedData();
            edata.setDataList(dataList);

            // Placemark element
            Placemark outerPlacemark = createOuterPlacemark(styleId);

            return createKmlGeometry(folder, outerPlacemark, edata, styleId,
                    folderFeatures);
        } catch (Exception e) {
            Log.e(TAG, "Export of " + this.getClass().getSimpleName() +
                    " to KML failed with Exception", e);
        }
        return null;
    }

    // helper for toKml()
    protected Placemark createOuterPlacemark(String styleId) {
        Placemark placemark = new Placemark();
        placemark.setId(getUID() + getTitle() + " outer");
        placemark.setName(getTitle());
        placemark.setStyleUrl("#" + styleId);
        placemark.setVisibility(getVisible() ? 1 : 0);
        return placemark;
    }

    // helper for toKml()
    protected int determineIfFilled() {
        // if fully transparent, then no fill, otherwise check fill mask
        int a = (getFillColor() >> 24) & 0xFF;
        if (a == 0) {
            return 0;
        } else {
            return 1;
        }
    }

    // helper for toKml()
    protected String kmlFolderName() {
        if (getGroup() != null
                && !FileSystemUtils.isEmpty(getGroup().getFriendlyName())) {
            return getGroup().getFriendlyName();
        } else {
            return getTitle();
        }
    }

    // helper for toKml()
    protected List<Data> kmlDataList() {

        List<Data> dataList = new ArrayList<>();
        Data data = new Data();
        data.setName("factory");
        data.setValue("u-d-f");
        dataList.add(data);
        return dataList;
    }

    // helper for toKml()
    protected Folder createKmlGeometry(Folder folder,
            Placemark outerPlacemark,
            ExtendedData edata,
            String styleId,
            List<Feature> folderFeatures) {

        List<Geometry> outerGeometries = new ArrayList<>();
        outerPlacemark.setGeometryList(outerGeometries);

        GeoPointMetaData textLoc;
        GeoPointMetaData[] pts = this.getMetaDataPoints();

        boolean clampToGroundKMLElevation = Double.isNaN(getHeight())
                || Double.compare(getHeight(), 0.0) == 0;

        // reintroduce legacy behavior
        if (forceAltitude)
            clampToGroundKMLElevation = false;

        if (this.isClosed()) {
            Polygon lr = KMLUtil.createPolygonWithLinearRing(pts,
                    this.getUID(), clampToGroundKMLElevation,
                    _bounds.crossesIDL(), getHeight());
            if (lr == null) {
                Log.w(TAG, "Unable to create KML Geometry");
                return null;
            }
            if (forceAltitude)
                lr.setExtrude(false);

            outerGeometries.add(lr);
            textLoc = this.getCenter();
        } else {
            LineString ls = KMLUtil.createLineString(pts, getUID(),
                    clampToGroundKMLElevation, _bounds.crossesIDL());
            if (ls == null) {
                Log.w(TAG, "Unable to create KML Geometry");
                return null;
            }
            if (forceAltitude)
                ls.setExtrude(false);

            outerGeometries.add(ls);

            // use the middle point if a line, center might not be on the line
            textLoc = this.getPoint(this.getNumPoints() / 2);
        }

        outerPlacemark.setExtendedData(edata);
        folderFeatures.add(outerPlacemark);

        Coordinate coord = KMLUtil.convertKmlCoord(textLoc, true);
        if (coord == null) {
            Log.w(TAG, "No center marker location set");
        } else {
            Point centerPoint = new Point();
            centerPoint.setCoordinates(coord);

            // icon for the middle of this drawing rectangle
            Placemark centerPlacemark = new Placemark();
            centerPlacemark.setId(getUID() + getTitle() + " center");
            centerPlacemark.setName(getTitle());
            centerPlacemark.setVisibility(getVisible() ? 1 : 0);
            centerPlacemark.setStyleUrl("#" + styleId);

            List<Geometry> centerGeometries = new ArrayList<>();
            centerGeometries.add(centerPoint);
            centerPlacemark.setGeometryList(centerGeometries);
            folderFeatures.add(centerPlacemark);
        }
        return folder;
    }

    protected KMZFolder toKmz() {
        Folder f = toKml();
        if (f == null)
            return null;
        return new KMZFolder(f);
    }

    private OGRFeatureExportWrapper toOgrGeomtry() {

        org.gdal.ogr.Geometry geometry = new org.gdal.ogr.Geometry(
                org.gdal.ogr.ogrConstants.wkbLineString);
        double unwrap = 0;
        if (_bounds.crossesIDL())
            unwrap = 360;
        GeoPoint[] points = getPoints();
        for (GeoPoint point : points)
            OGRFeatureExportWrapper.addPoint(geometry, point, unwrap);
        // if the polyline is closed, loop back to the first point
        if ((getStyle()
                & Polyline.STYLE_CLOSED_MASK) == Polyline.STYLE_CLOSED_MASK)
            OGRFeatureExportWrapper.addPoint(geometry, points[0], unwrap);

        String name = getTitle() + " lines";
        String groupName = name;
        if (getGroup() != null) {
            groupName = getGroup().getFriendlyName();
        }
        return new OGRFeatureExportWrapper(groupName, ogr.wkbLineString,
                new OGRFeatureExportWrapper.NamedGeometry(geometry, name));
    }

    protected GPXExportWrapper toGpx() {

        GpxTrack t = new GpxTrack();
        t.setName(getTitle());
        t.setDesc(getUID());

        List<GpxTrackSegment> trkseg = new ArrayList<>();
        t.setSegments(trkseg);
        GpxTrackSegment seg = new GpxTrackSegment();
        trkseg.add(seg);
        List<GpxWaypoint> trkpt = new ArrayList<>();
        seg.setPoints(trkpt);

        double unwrap = 0;
        if (_bounds.crossesIDL())
            unwrap = 360;

        GeoPoint[] points = getPoints();
        for (GeoPoint point : points)
            trkpt.add(RouteGpxIO.convertPoint(point, unwrap));

        // if the polyline is closed, loop back to the first point
        if ((getStyle()
                & Polyline.STYLE_CLOSED_MASK) == Polyline.STYLE_CLOSED_MASK)
            trkpt.add(RouteGpxIO.convertPoint(points[0], unwrap));

        return new GPXExportWrapper(t);
    }

    @Override
    public boolean isSupported(Class<?> target) {
        return CotEvent.class.equals(target) ||
                Folder.class.equals(target) ||
                KMZFolder.class.equals(target) ||
                MissionPackageExportWrapper.class.equals(target) ||
                GPXExportWrapper.class.equals(target) ||
                OGRFeatureExportWrapper.class.equals(target);
    }

    @Override
    public Object toObjectOf(Class<?> target, ExportFilters filters) {
        if (filters != null && filters.filter(this))
            return null;

        if (CotEvent.class.equals(target)) {
            return toCot();
        } else if (Folder.class.equals(target)) {
            return toKml();
        } else if (KMZFolder.class.equals(target)) {
            return toKmz();
        } else if (MissionPackageExportWrapper.class.equals(target)) {
            return Marker.toMissionPackage(this);
        } else if (GPXExportWrapper.class.equals(target)) {
            return toGpx();
        } else if (OGRFeatureExportWrapper.class.equals(target)) {
            return toOgrGeomtry();
        }

        return null;
    }

    @Override
    public Bundle preDrawCanvas(CapturePP capture) {
        // Store forward returns for all points
        Bundle data = super.preDrawCanvas(capture);
        // Also include center label position
        data.putParcelable("labelPoint", capture.forward(getCenter().get()));
        return data;
    }

    @Override
    public void drawCanvas(CapturePP cap, Bundle data) {
        super.drawCanvas(cap, data);
        PointF labelPoint = data.getParcelable("labelPoint");
        String label = getMetaString("centerPointLabel", "");
        if (cap.shouldDrawLabel(this, label))
            cap.drawLabel(label, labelPoint);
    }

    /**
     * Bulk operation for setting both the points and markers for a line
     * with as little overhead as possible
     * @param points Complete list of points
     * @param markers Sparse array (int map) of markers
     * @return True if line changed, false if the same
     */
    public synchronized boolean setPoints(List<GeoPointMetaData> points,
            SparseArray<PointMapItem> markers) {
        if (points.size() < 2) // Unacceptable - ignore
            return false;

        setBulkOperation(true);

        // Close if first point and last point are equal
        boolean closed = false;
        if (points.size() > 2 && points.get(0).equals(
                points.get(points.size() - 1))) {
            points.remove(points.size() - 1);
            closed = true;
        }

        // Check if any of the points have changed
        boolean changed = points.size() != _points.size();
        if (!changed) {
            for (int i = 0; i < points.size(); i++) {
                GeoPointMetaData oldP = _points.get(i);
                GeoPointMetaData newP = clampLat(points.get(i));
                if (oldP == null || !oldP.equals(newP)) {
                    changed = true;
                    break;
                }
            }
        }

        // Check if any of the markers have changed
        if (!changed)
            changed = markersChanged(this.indexToMarker, markers);
        if (!changed)
            changed = markersChanged(this.indexToMarker2, markers);

        // Refresh points and markers if changed
        if (changed) {
            // Clear first
            clearPoints();
            this.controlPoints.clear();

            // Then add new points/markers
            for (GeoPointMetaData p : points) {
                _points.add(clampLat(p));
            }
            for (int i = 0; i < markers.size(); i++) {
                int k = markers.keyAt(i);
                PointMapItem m = markers.valueAt(i);
                if (k >= 0 && k < _points.size()) {
                    GeoPointMetaData gpm = _points.get(k);
                    m.setPoint(gpm.get());
                    m.copyMetaData(gpm.getMetaData());
                }
                setMarkerNoSync(k, m);
            }
        }

        // Update closed state
        setClosed(closed);

        setBulkOperation(false);
        if (changed) {
            onPointsChanged();
            this.refresh(this.mapView.getMapEventDispatcher(), null,
                    this.getClass());
        }
        return changed;
    }

    /**
     * Clamp latitude values in a GeoPointMetadata to the n/s poles.
     * As an example, a point of latitude of 95 degrees is replaced
     * with a latitude of 85 degrees.
     * @param gpm GeoPointMetadata to inspect and clamp the latitude of
     * @return a GeoPointMetadata equivalent to the provided one, but with the latitude clampped
     */
    private GeoPointMetaData clampLat(GeoPointMetaData gpm) {
        double lat = gpm.get().getLatitude();
        boolean changed = false;
        // Handles only values from 90 -> 180 and -90 -> -180 presently.
        if (lat > 90) {
            lat = 90 - (lat - 90);
            changed = true;
        }
        if (lat < -90) {
            lat = -90 - (lat + 90);
            changed = true;
        }
        if (changed) {
            GeoPointMetaData ret = new GeoPointMetaData(gpm);
            ret.set(new GeoPoint(lat,
                    gpm.get().getLongitude(),
                    gpm.get().getAltitude(),
                    gpm.get().getAltitudeReference(),
                    gpm.get().getCE(),
                    gpm.get().getLE(),
                    gpm.get().isMutable() ? GeoPoint.Access.READ_WRITE
                            : GeoPoint.Access.READ_ONLY));
            return ret;
        }

        return gpm;
    }

    private static boolean markersChanged(Map<Integer, PointMapItem> oldMarkers,
            SparseArray<PointMapItem> newMarkers) {
        for (Map.Entry<Integer, PointMapItem> e : oldMarkers.entrySet()) {
            PointMapItem oldM = e.getValue();
            PointMapItem newM = newMarkers.get(e.getKey());
            if (oldM == null || newM == null)
                return true;
            String oldType = oldM.getType(), newType = newM.getType();
            String oldUID = oldM.getUID(), newUID = newM.getUID();
            String oldName = oldM.getMetaString("callsign", null);
            String newName = newM.getMetaString("callsign", null);
            if (!FileSystemUtils.isEquals(oldType, newType)
                    || !FileSystemUtils.isEquals(oldUID, newUID)
                    || !FileSystemUtils.isEquals(oldName, newName))
                return true;
        }
        return false;
    }

    /**
     * ******** UNDO Actions ***************
     */
    public class MovePointAction extends EditAction {
        final PointMapItem item_;
        final int index_;
        final GeoPointMetaData oldPoint_;
        final GeoPointMetaData newPoint_;

        public MovePointAction(PointMapItem item, GeoPointMetaData oldPoint,
                GeoPointMetaData newPoint) {
            this(getIndexOfMarker(item), item, oldPoint, newPoint);
        }

        public MovePointAction(int index, GeoPointMetaData newPoint) {
            this(index, getPoint(index), newPoint);
        }

        public MovePointAction(int index, GeoPointMetaData oldPoint,
                GeoPointMetaData newPoint) {
            this(index, getMarker(index), oldPoint, newPoint);
        }

        private MovePointAction(int index, PointMapItem item,
                GeoPointMetaData oldPoint, GeoPointMetaData newPoint) {
            item_ = item;
            oldPoint_ = oldPoint;
            newPoint_ = newPoint;
            index_ = index;
        }

        @Override
        public boolean run() {
            if (hasMetaValue("static_shape"))
                moveClosedSet(oldPoint_, newPoint_);
            else
                setPoint(index_, newPoint_);
            return true;
        }

        @Override
        public void undo() {
            if (hasMetaValue("static_shape"))
                moveClosedSet(newPoint_, oldPoint_);
            else
                setPoint(index_, oldPoint_);
        }

        @Override
        public String getDescription() {
            return null;
        }

    }

    public class InsertPointAction extends EditAction {
        MapGroup addToOnSuccess;
        PointMapItem _item;
        GeoPointMetaData _point;
        final int _index;

        public InsertPointAction(GeoPointMetaData p) {
            this(p, getNumPoints());
        }

        public InsertPointAction(GeoPointMetaData p, int index) {
            _point = p;
            _index = index;
        }

        public InsertPointAction(PointMapItem item) {
            this(item, getNumPoints(), null);
        }

        public InsertPointAction(PointMapItem item, int index) {
            this(item, index, null);
        }

        public InsertPointAction(PointMapItem item, int index,
                MapGroup addToOnSuccess) {
            _item = item;
            _index = index;
            this.addToOnSuccess = addToOnSuccess;
        }

        @Override
        public boolean run() {
            if (hasMetaValue("static_shape"))
                return false;
            if (_item != null) {
                final boolean retval = addMarker(_index, _item);
                if (retval) {
                    indexToMarker.put(_index, _item);
                    markerToIndex.put(_item, _index);
                    _item.refresh(mapView.getMapEventDispatcher(), null,
                            this.getClass());
                    if (this.addToOnSuccess != null)
                        this.addToOnSuccess.addItem(_item);
                }
                return retval;
            } else {
                return addPoint(_index, _point);
            }
        }

        @Override
        public void undo() {
            if (hasMetaValue("static_shape"))
                return;
            try {
                removePoint(_index);
                if (this.addToOnSuccess != null)
                    this.addToOnSuccess.removeItem(_item);
            } catch (Exception e) {
                Log.d(TAG,
                        "error occurred attempting to undo a point insertion: "
                                + _index,
                        e);
            }
        }

        @Override
        public String getDescription() {
            // TODO Auto-generated method stub
            return null;
        }

    }

    // an exchange is a delete followed by an insert!
    public class ExchangePointAction extends EditAction {
        final EditAction insert_;
        final EditAction delete_;

        public ExchangePointAction(int index, PointMapItem newItem,
                MapGroup addToOnSuccess) {
            Log.d(TAG, "ExchangePointAction");

            delete_ = new RemovePointAction(index);
            if (_actionProvider != null) {
                insert_ = _actionProvider.newInsertPointAction(newItem, index,
                        addToOnSuccess);
            } else {
                insert_ = new InsertPointAction(newItem, index, addToOnSuccess);
            }
        }

        @Override
        public boolean run() {
            return delete_.run() && insert_.run();
        }

        @Override
        public void undo() {
            insert_.undo();
            delete_.undo();
        }

        @Override
        public String getDescription() {
            // TODO Auto-generated method stub
            return null;
        }
    }

    /**
     * Removes a marker, does *not* delete the underlying point though.
     */
    public class RemoveMarkerAction extends EditAction {

        PointMapItem _item = null;
        MapGroup deletedFrom = null;
        Integer index;

        public RemoveMarkerAction(PointMapItem item) {
            Log.d(TAG,
                    "RemoveMarkerAction - "
                            + item.getMetaString("callsign", "unknown"));

            _item = item;
            deletedFrom = item.getGroup();
            if (deletedFrom == null)
                deletedFrom = EditablePolyline.this.getGroup();
            index = EditablePolyline.this.markerToIndex.get(item);
        }

        // public void setOldItemMapGroup(MapGroup group) {
        // deletedFrom = group;
        // }

        @Override
        public boolean run() {
            if (index != null)
                removeMarker(index);
            return true;
        }

        @Override
        public void undo() {
            if (deletedFrom != null && _item.getGroup() == null) {
                // slight hackish to allow undoing the removal of
                // non-waypoints to work fine
                deletedFrom.addItem(_item);

                // Save newly re-added item to storage
                _item.persist(mapView.getMapEventDispatcher(), null,
                        this.getClass());
            }
            if (index != null)
                setMarker(index, _item);
        }

        @Override
        public String getDescription() {
            // TODO Auto-generated method stub
            return null;
        }

    }

    // delete is the opposite of insert!
    public class RemovePointAction extends InsertPointAction {

        MapGroup deletedFrom = null;

        public RemovePointAction(int index) {
            super(getPoint(index), index);
            _item = getMarker(index);

            if (_item != null) {
                deletedFrom = _item.getGroup();
            }
        }

        @Override
        public boolean run() {
            super.undo();
            return true;
        }

        @Override
        public void undo() {
            if (deletedFrom != null && _item.getGroup() == null) {
                deletedFrom.addItem(_item); // slight hackish to allow undoing the removal of
                                            // non-waypoints to work fine

                // Save newly re-added item to storage
                _item.persist(mapView.getMapEventDispatcher(), null,
                        this.getClass());
            }
            super.run();
        }

    }

}
