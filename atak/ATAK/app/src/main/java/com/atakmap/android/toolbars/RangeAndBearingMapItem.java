
package com.atakmap.android.toolbars;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.bloodhound.ui.BloodHoundHUD;
import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.app.R;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.gpx.GpxTrack;
import com.atakmap.android.gpx.GpxTrackSegment;
import com.atakmap.android.gpx.GpxWaypoint;
import com.atakmap.android.importexport.CotEventFactory;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.maps.Arrow;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher.OnMapEventListener;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.android.routes.RouteGpxIO;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.spatial.file.export.GPXExportWrapper;
import com.atakmap.spatial.file.export.KMZFolder;
import com.atakmap.spatial.file.export.OGRFeatureExportWrapper;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.model.Coordinates;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;
import com.ekito.simpleKML.model.Geometry;
import com.ekito.simpleKML.model.IconStyle;
import com.ekito.simpleKML.model.LineString;
import com.ekito.simpleKML.model.LineStyle;
import com.ekito.simpleKML.model.Placemark;
import com.ekito.simpleKML.model.Point;
import com.ekito.simpleKML.model.Style;
import com.ekito.simpleKML.model.StyleSelector;

import org.gdal.ogr.ogr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class RangeAndBearingMapItem extends Arrow implements
        PointMapItem.OnPointChangedListener, MapItem.OnGroupChangedListener,
        OnMapEventListener, OnSharedPreferenceChangeListener, Exportable {

    public static final String TAG = "RangeAndBearingMapItem";

    static final String META_SPEED = "self.speed";
    static final String META_USER_SPEED = "user.speed";
    static final String META_ETA = "eta";

    private PointMapItem _pt1;
    private PointMapItem _pt2;
    private boolean _reversed = false;
    private boolean _lengthLock = false;

    private NorthReference _northReference;
    private int _rangeUnits;
    private Angle _bearingUnits;

    private double _range; // meters
    private double _slantRange; // meters
    private double _depAngle; // degrees
    private double _bearing; // true

    /**
     * Used to ignore persist events during initialization
     */
    private boolean _initializing;

    /**
     * True for slant range, false for ground clamped
     */
    private boolean _displaySlantRange = false;

    private final SharedPreferences _prefs;

    /**
     * Provided a unique identifier look up a Range and Bearing Map item.
     * @param uid the uid describing the range and bearing map item
     * @return the associated range and bearing map item or null if one is not
     * found.
     */
    public static RangeAndBearingMapItem getRABLine(String uid) {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return null;
        RangeAndBearingMapItem rab = null;
        if (uid != null && !uid.isEmpty()) {
            MapItem item = mv.getRootGroup().deepFindUID(uid);
            if (item instanceof RangeAndBearingMapItem)
                rab = (RangeAndBearingMapItem) item;
        }
        return rab;
    }

    /**
     * Creates a persistent Range and Bearing map item.
     * @param uid the unique identifier for the range and bearing line
     * @param pmi1 the map item to attach to the start of the arrow
     * @param pmi2 the map item to attach to the end of the arrow.
     */
    public static RangeAndBearingMapItem createOrUpdateRABLine(String uid,
            PointMapItem pmi1, PointMapItem pmi2) {
        return createOrUpdateRABLine(uid, pmi1, pmi2, true);
    }

    /** 
     * Create a Range and Bearing map item, with a given uid and a start and end
     * point.  Additionally, this could be persistent (true) or transient (false)
     * @param uid the unique identifier for the range and bearing line
     * @param pmi1 the point map item for the start of the arrow
     * @param pmi2 the point map item for the end of the arrow
     * @param persist if the arrow is to persist after a restart.
     */
    public static RangeAndBearingMapItem createOrUpdateRABLine(String uid,
            PointMapItem pmi1, PointMapItem pmi2, boolean persist) {

        RangeAndBearingMapItem rab = getRABLine(uid);
        try {
            if (uid != null && !uid.isEmpty()
                    && pmi1 != null
                    && pmi2 != null) {
                Log.d(TAG, "wrap a rangeandbearingmapitem: " + uid);

                if (rab == null) {
                    rab = new RangeAndBearingMapItem(pmi1, pmi2,
                            MapView.getMapView(), uid, persist);

                    if (pmi1 instanceof RangeAndBearingEndpoint) {
                        RangeAndBearingEndpoint rabe = (RangeAndBearingEndpoint) pmi1;
                        rabe.setParent(rab);
                        rabe.setPart(RangeAndBearingEndpoint.PART_TAIL);
                    }
                    if (pmi2 instanceof RangeAndBearingEndpoint) {
                        RangeAndBearingEndpoint rabe = (RangeAndBearingEndpoint) pmi2;
                        rabe.setParent(rab);
                        rabe.setPart(RangeAndBearingEndpoint.PART_HEAD);
                    }
                    if (pmi1 instanceof DynamicRangeAndBearingEndpoint) {
                        DynamicRangeAndBearingEndpoint drabe = (DynamicRangeAndBearingEndpoint) pmi1;
                        if (drabe.getParent() == null) {
                            drabe.setParent(rab);
                            drabe.setPart(
                                    DynamicRangeAndBearingEndpoint.PART_TAIL);
                        }
                    }
                    if (pmi2 instanceof DynamicRangeAndBearingEndpoint) {
                        DynamicRangeAndBearingEndpoint drabe = (DynamicRangeAndBearingEndpoint) pmi2;
                        if (drabe.getParent() == null) {
                            drabe.setParent(rab);
                            drabe.setPart(
                                    DynamicRangeAndBearingEndpoint.PART_HEAD);
                        }
                    }

                    rab.setTitle(rab.getTitle());
                    rab.setMetaString("menu", "menus/rab_menu.xml");
                    rab.setMetaString("rabUUID", uid);
                    rab.setZOrder(-2d);

                    rab.onPointChanged(null);

                    rab.refresh(MapView.getMapView().getMapEventDispatcher(),
                            null, RangeAndBearingMapItem.class);

                } else if (pmi1 != null && pmi2 != null) {
                    rab.setPoint1(pmi1);
                    rab.setPoint2(pmi2);
                }
            } else {
                Log.w(TAG,
                        "One or more of the following required extras are missing: 'uid', 'pmi1', 'pmi2'");
            }
        } catch (Exception e) {
            Log.e(TAG, "error: ", e);
        }
        return rab;
    }

    /**
     * Return true if the given point map item is associated with the range and bearing
     * arrow
     * @param pmi the point map item to check
     * @return true if it is either attached to the start or the end of the range and
     * bearing arrow
     */
    public boolean containsEndpoint(PointMapItem pmi) {
        return pmi == _pt1 || pmi == _pt2;
    }

    /**
     * Dispatch the R+B Line, identified by it's UID, as CoT
     * @param uid  UID of the RangeAndBearingMapItem line to send as CoT
     * @return  Indicator of success: True - CoT dispatched, False - failure line likely not found
     */

    public static boolean sendAsCot(String uid) {
        RangeAndBearingMapItem line = getRABLine(uid);
        if (line != null) {
            CotEvent ce = CotEventFactory.createCotEvent(line);
            CotMapComponent.getExternalDispatcher().dispatch(ce);
            return true;
        }
        return false;
    }

    /**
     * Sets the map item for the start of the arrow.
     * @param pmi the map item to be associated with the start of the arrow.
     */
    public void setPoint1(PointMapItem pmi) {
        updateEndpoint(pmi, true);
    }

    /**
     * Sets the map item for the start of the arrow.
     * @param pmi the map item to be associated with the end of the arrow.
     */
    public void setPoint2(PointMapItem pmi) {
        updateEndpoint(pmi, false);
    }

    private void updateEndpoint(PointMapItem newPt, boolean isPoint1) {
        PointMapItem oldPt = isPoint1 ? _pt1 : _pt2;
        if (oldPt == newPt)
            return;

        if (oldPt != null)
            removeListenersFromPoint(oldPt);

        if (isPoint1)
            _pt1 = newPt;
        else
            _pt2 = newPt;

        addListenersToPoint(newPt);
        onPointChanged(newPt);
    }

    @Override
    public void setPoint1(GeoPointMetaData point) {
        super.setPoint1(point);
        _pt1.setPoint(point);
        onPointChanged(_pt1);
    }

    @Override
    public void setPoint2(GeoPointMetaData point) {
        super.setPoint2(point);
        _pt2.setPoint(point);
        onPointChanged(_pt2);
    }

    /**
     * Returns true if the arrow has been reversed.
     * @return true if reversed.
     */
    public boolean isReversed() {
        return _reversed;
    }

    /**
     * Reverse the direction of the arrow.
     */
    public void reverse() {
        _reversed = !_reversed;
        if (_pt1 instanceof RangeAndBearingEndpoint) {
            ((RangeAndBearingEndpoint) _pt1).reverse();
        }
        if (_pt2 instanceof RangeAndBearingEndpoint) {
            ((RangeAndBearingEndpoint) _pt2).reverse();
        }

        if (_pt1 instanceof DynamicRangeAndBearingEndpoint
                && _pt2 instanceof DynamicRangeAndBearingEndpoint) {
            String headName = _pt1.getTitle();
            if (FileSystemUtils.isEmpty(headName))
                headName = "R&B Head";
            _pt1.setTitle(_pt2.getTitle());
            _pt2.setTitle(headName);
        }

        PointMapItem temp = _pt1;
        _pt1 = _pt2;
        _pt2 = temp;
        onPointChanged(_pt1);
    }

    /**
     * Toggles the current state of the slant range display for the range and bearing arrow.
     * Slant range is the true distance (hypotenuse) where as range is just the distance
     * as the crow flies between two points on a flat model.
     */
    public void toggleSlantRange() {
        setDisplaySlantRange(!hasMetaValue("slantRange"));
    }

    /**
     * Toggle length lock for the range and bearing arrow
     * @return true if lock is enabled, false if the result is not locked.
     */
    public boolean toggleLock() {
        _lengthLock = !_lengthLock;
        if (_lengthLock) {
            _pt1.setMetaBoolean("distanceLocked", true);
            _pt2.setMetaBoolean("distanceLocked", true);
            setMetaBoolean("distanceLocked", true);
        } else {
            _pt1.removeMetaData("distanceLocked");
            _pt2.removeMetaData("distanceLocked");
            removeMetaData("distanceLocked");
        }
        return _lengthLock;
    }

    /**
     * Returns true if the range and bearring arrow is length locked.
     * @return true if length locked
     */
    public boolean isLocked() {
        return _lengthLock;
    }

    /**
     * Set this R&B MapItem's bearing units display option<br>
     * <br>
     *
     * @param bearingUnits - The constant int associated with the bearing units preference.
     */
    public void setBearingUnits(Angle bearingUnits) {
        _bearingUnits = bearingUnits;

        updateLabel();

        this.persist(_mapView.getMapEventDispatcher(), null, this.getClass());
    }

    /**
     * Set this R&B MapItem's range units display option
     *
     * @param rangeUnits - The constant int associated with the range units preference.
     *                   Can be one of Span.METRIC, Span.ENGLISH, or Span.NM
     * @throws IllegalStateException if the range unit is not one of the following
     */
    public void setRangeUnits(int rangeUnits) {
        if (rangeUnits != Span.ENGLISH &&
                rangeUnits != Span.NM &&
                rangeUnits != Span.METRIC) {
            //throw new IllegalStateException("invalid range unit passed in");
            Log.d(TAG,
                    "soft exception - starting in 4.8 this will be a Illegal State Exception",
                    new IllegalStateException("invalid range unit passed in"));
            return;
        }
        _rangeUnits = rangeUnits;
        updateLabel();
        this.persist(_mapView.getMapEventDispatcher(), null, this.getClass());
    }

    /**
     * Set this R&B MapItem's north reference display option<br>
     * <br>
     * NorthReference.TRUE = 0 (deg T)<br>
     * NorthReference.MAGNETIC = 1 (deg M)<br>
     * NorthReference.GRID = 2 (deg G)<br>
     *
     * @param northReference - The constant int associated with the north reference preference.
     */
    public void setNorthReference(NorthReference northReference) {
        _northReference = northReference;

        updateLabel();

        this.persist(_mapView.getMapEventDispatcher(), null, this.getClass());
    }

    /**
     * Sets the speed associated with the range and bearing arrow for the purposes of
     * bloodhound computation.
     * @param speed the speed in meters per second.
     */
    void setSpeed(final double speed) {
        setMetaDouble(META_USER_SPEED,
                isSpeedValid(speed) ? speed : Double.NaN);
        updateLabel();
    }

    /**
     * Allows for the toggle of the slant range and is not actually the toggle of
     * the slant range
     * @param state the state togglable or not.
     */
    public void allowSlantRangeToggle(boolean state) {
        if (state) {
            removeMetaData("disable_slant");
            setDisplaySlantRange(hasMetaValue("slantRange"));
        } else {
            setMetaBoolean("disable_slant", true);
        }
        setDisplaySlantRange(hasMetaValue("slantRange"));
    }

    /**
     * Sets the state of the slant range display to either true or false
     * @param bSlantRange true shows the distance as slant range.
     */
    public void setDisplaySlantRange(boolean bSlantRange) {
        if (bSlantRange) {
            setMetaBoolean("slantRange", true);
        } else {
            removeMetaData("slantRange");
        }
        _displaySlantRange = bSlantRange;

        // force a localized low impact change to never allow for 
        // slant range for a specific range and bearing line.
        if (hasMetaValue("disable_slant"))
            _displaySlantRange = false;

        updateLabel();
        this.persist(_mapView.getMapEventDispatcher(), null, this.getClass());
    }

    /**
     * Returns the inclination of the range and bearing arrow.
     * @return the inclination in degrees
     */
    public double getInclination() {
        return _depAngle;
    }

    /**
     * Returns the bearing units for the range and bearing arrow
     * @return the bearing units
     */
    public Angle getBearingUnits() {
        return _bearingUnits;
    }

    /**
     * Returns the range units for the range and bearing arrow
     * @return the range unit as one of Span.METRIC, Span.ENGLISH, or Span.NM
     */
    public int getRangeUnits() {
        return _rangeUnits;
    }

    /**
     * Returns the north reference for the range and bearing arrow
     * @return the north reference
     */
    public NorthReference getNorthReference() {
        return _northReference;
    }

    /**
     * Default persistent Range And Bearing Map Item.
     */
    public RangeAndBearingMapItem(PointMapItem pt1, PointMapItem pt2,
            MapView mapView, String title, String uid, int rangeUnits,
            Angle bearingUnits, NorthReference northRef, int color) {
        this(pt1, pt2, mapView, title, uid, rangeUnits, bearingUnits, northRef,
                color, true);
    }

    /**
     * Range And Bearing Map Item with the ability to make it persist in the database.
     * @param pt1 the start point for the arrow
     * @param pt2 the end point for the arrow
     * @param mapView the map view
     * @param title the title or label of the arrow
     * @param uid the unique identifier
     * @param rangeUnits the range units as one of Span.ENGLISH, Span.METRIC, Span.NM
     * @param bearingUnits the bearing units
     * @param northRef the north reference
     * @param color the color of the arrow
     * @param persist true if it will persist past application restarts
     */
    public RangeAndBearingMapItem(PointMapItem pt1, PointMapItem pt2,
            MapView mapView,
            String title, String uid, int rangeUnits,
            Angle bearingUnits, NorthReference northRef, int color,
            boolean persist) {

        this(pt1, pt2, mapView, "u-rb-a", uid, persist);

        _initializing = true;

        _northReference = northRef;
        _bearingUnits = bearingUnits;
        try {
            setRangeUnits(rangeUnits);
        } catch (IllegalStateException ise) {
            Log.e(TAG,
                    "error setting the range units for a range and bearing arrow: "
                            + uid,
                    ise);
        }
        onPointChanged(_pt1);

        setStrokeColor(color);
        setStrokeWeight(3f);
        setTitle(title);
        updateLabel();

        _initializing = false;

        if (persist)
            this.persist(_mapView.getMapEventDispatcher(), null,
                    this.getClass());
    }

    /**
     * Range And Bearing Map Item with the ability to make it persist in the database.
     * @param pt1 the start point for the arrow
     * @param pt2 the end point for the arrow
     * @param mapView the map view
     * @param uid the unique identifier
     */
    public RangeAndBearingMapItem(PointMapItem pt1, PointMapItem pt2,
            MapView mapView, final String uid) {
        this(pt1, pt2, mapView, "u-rb-a", uid, true);
    }

    /**
     * Range And Bearing Map Item with the ability to make it persist in the database.
     * @param pt1 the start point for the arrow
     * @param pt2 the end point for the arrow
     * @param mapView the map view
     * @param uid the unique identifier
     * @param persist true if it will persist past application restarts
     */
    public RangeAndBearingMapItem(PointMapItem pt1, PointMapItem pt2,
            MapView mapView, final String uid, boolean persist) {
        this(pt1, pt2, mapView, "u-rb-a", uid, persist);
    }

    public RangeAndBearingMapItem(PointMapItem pt1, PointMapItem pt2,
            MapView mapView, String type, final String uid, boolean persist) {
        super(uid);

        _initializing = true;

        if (!persist)
            setMetaBoolean("nevercot", true);
        else
            setMetaBoolean("archive", true);
        toggleMetaData("removable", true);

        _mapView = mapView;
        _pt1 = pt1;
        if (!(_pt1 instanceof RangeAndBearingEndpoint))
            setMetaBoolean("anchorAvailable", true);
        _pt2 = pt2;
        if (!(_pt2 instanceof RangeAndBearingEndpoint))
            setMetaBoolean("radiusAvailable", true);
        setMetaBoolean("distanceLockAvailable", true);
        if (hasMetaValue("anchorAvailable") && hasMetaValue("radiusAvailable"))
            removeMetaData("distanceLockAvailable"); //Turn off distance lock if both endpoints are anchored
        _point1 = _pt1.getGeoPointMetaData();
        _point2 = _pt2.getGeoPointMetaData();

        _pt1.setMetaString("rabUUID", uid);
        _pt2.setMetaString("rabUUID", uid);

        addListenersToPoint(_pt1);
        addListenersToPoint(_pt2);

        this.setType(type);

        _prefs = PreferenceManager.getDefaultSharedPreferences(_mapView
                .getContext());
        _prefs.registerOnSharedPreferenceChangeListener(this);

        _northReference = NorthReference.findFromValue(Integer.parseInt(_prefs
                .getString("rab_north_ref_pref",
                        String.valueOf(NorthReference.MAGNETIC.getValue()))));

        _bearingUnits = Angle.findFromValue(Integer.parseInt(_prefs.getString(
                "rab_brg_units_pref",
                String.valueOf(Angle.DEGREE.getValue()))));

        // ATAK-10776 AngleUtilities NPE
        if (_bearingUnits == null) {
            _prefs.edit().putString("rab_brg_units_pref",
                    String.valueOf(Angle.DEGREE.getValue())).apply();
            _bearingUnits = Angle.DEGREE;
        }

        try {
            setRangeUnits(
                    Integer.parseInt(_prefs.getString("rab_rng_units_pref",
                            String.valueOf(Span.METRIC))));
        } catch (Exception ise) {
            Log.e(TAG,
                    "error setting the range units for a range and bearing arrow: "
                            + uid,
                    ise);
            setRangeUnits(Span.METRIC);
        }
        setDisplaySlantRange(_prefs.getString("rab_dist_slant_range",
                "clamped").equals("slantrange"));

        onPointChanged(_pt1);
        int _color = Color.RED;
        try {
            _color = Color
                    .parseColor(_prefs.getString("rab_color_pref", "red"));
        } catch (IllegalArgumentException ignored) {
        }
        setStrokeColor(_color);
        setStrokeWeight(3f);
        setTitle("R&B " + RangeAndBearingImporter.getInstanceNum());
        setMetaString("iconUri", ATAKUtilities.getResourceUri(
                R.drawable.pairing_line_white));

        addOnGroupChangedListener(this);

        _initializing = false;
    }

    void setInitializing(boolean initializing) {
        _initializing = initializing;
    }

    @Override
    public void setTitle(String title) {
        if (title == null)
            title = "";
        super.setTitle(title);

        // Update endpoint titles
        Resources r = _mapView.getResources();
        if (_pt1 instanceof DynamicRangeAndBearingEndpoint)
            _pt1.setTitle(title + " " + r.getString(R.string.tail));
        else if (_pt1 instanceof RangeAndBearingEndpoint)
            _pt1.setTitle(title + " Endpoint");
        if (_pt2 instanceof DynamicRangeAndBearingEndpoint)
            _pt2.setTitle(title + " " + r.getString(R.string.head));
        else if (_pt2 instanceof RangeAndBearingEndpoint)
            _pt2.setTitle(title + " Endpoint");
    }

    @Override
    public void persist(MapEventDispatcher dispatcher, Bundle extras,
            Class<?> clazz) {
        // We shouldn't persist the line before it's finished being constructed
        if (_initializing)
            return;
        super.persist(dispatcher, extras, clazz);
    }

    private void killEndpoints() {
        List<RangeAndBearingMapItem> users;
        if (_pt1 instanceof RangeAndBearingEndpoint) {
            users = getUsers(_pt1);
            users.remove(this);
            if (users.isEmpty()) {
                _mapView.getRootGroup().findMapGroup("Range & Bearing")
                        .removeItem(_pt1);
                if (_pt1 instanceof RangeAndBearingEndpoint) {
                    _mapView.getMapEventDispatcher().removeMapItemEventListener(
                            _pt1,
                            (RangeAndBearingEndpoint) _pt1);
                }
                _pt1.dispose();
            }
        }
        if (_pt2 instanceof RangeAndBearingEndpoint) {
            users = getUsers(_pt2);
            users.remove(this);
            if (users.isEmpty()) {
                _mapView.getRootGroup().findMapGroup("Range & Bearing")
                        .removeItem(_pt2);
                if (_pt2 instanceof RangeAndBearingEndpoint) {
                    _mapView.getMapEventDispatcher().removeMapItemEventListener(
                            _pt2,
                            (RangeAndBearingEndpoint) _pt2);
                }
                _pt2.dispose();
            }
        }

        _pt1 = null;
        _pt2 = null;
    }

    private void removeListeners() {
        if (_prefs != null)
            _prefs.unregisterOnSharedPreferenceChangeListener(this);
        if (_pt1 != null)
            removeListenersFromPoint(_pt1);
        if (_pt2 != null)
            removeListenersFromPoint(_pt2);
        removeOnGroupChangedListener(this);
    }

    /**
     * Return the start point for the range and bearing arrow
     * @return
     */
    public PointMapItem getPoint1Item() {
        return _pt1;
    }

    /**
     * Return the end point for the range and bearing arrow
     * @return
     */
    public PointMapItem getPoint2Item() {
        return _pt2;
    }

    @Override
    public synchronized void dispose() {
        super.dispose();
        removeListeners();
        killEndpoints();
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        dispose();
    }

    private void addListenersToPoint(PointMapItem item) {
        item.addOnPointChangedListener(this);

        if (item instanceof RangeAndBearingEndpoint) {
            _mapView.getMapEventDispatcher().addMapItemEventListener(item,
                    (RangeAndBearingEndpoint) item);
        } else if (!(item instanceof DynamicRangeAndBearingEndpoint)) {
            item.addOnGroupChangedListener(_removeListener);
        }
    }

    private void removeListenersFromPoint(PointMapItem item) {
        item.removeOnPointChangedListener(this);

        if (!(item instanceof RangeAndBearingEndpoint)) {
            item.removeOnGroupChangedListener(_removeListener);
        } else {
            List<RangeAndBearingMapItem> users = getUsers(item);
            users.remove(this);
            if (users.isEmpty()) {
                // Remove listeners if no other parent candidates
                _mapView.getMapEventDispatcher().removeMapItemEventListener(
                        item,
                        (RangeAndBearingEndpoint) item);

                // Remove the endpoint from the group so it isn't orphaned
                item.removeFromGroup();
            } else
                // Otherwise set parent to next candidate
                ((RangeAndBearingEndpoint) item).setParent(users.get(0));
        }
    }

    /**
     * Retrieve users of an endpoint
     *
     * @param point The point to check for users
     * @return userList the list of range and bearing items that contain the given
     * point
     */
    public static List<RangeAndBearingMapItem> getUsers(PointMapItem point) {
        Collection<MapItem> rabItems;
        rabItems = RangeAndBearingCompat.getGroup().getItems();
        List<RangeAndBearingMapItem> rabMapItems = new ArrayList<>();
        RangeAndBearingMapItem other;
        for (MapItem m : rabItems) {
            if (m instanceof RangeAndBearingMapItem) {
                other = (RangeAndBearingMapItem) m;
                if ((other.getPoint1Item() == point
                        || other.getPoint2Item() == point))
                    rabMapItems.add((RangeAndBearingMapItem) m);
            }
        }
        return rabMapItems;
    }

    private void updateRadial() {
        setMetaBoolean("degs_t_bool", _bearingUnits == Angle.DEGREE
                && _northReference == NorthReference.TRUE);
        setMetaBoolean("mils_t_bool", _bearingUnits == Angle.MIL
                && _northReference == NorthReference.TRUE);
        setMetaBoolean("degs_m_bool", _bearingUnits == Angle.DEGREE
                && _northReference == NorthReference.MAGNETIC);
        setMetaBoolean("mils_m_bool", _bearingUnits == Angle.MIL
                && _northReference == NorthReference.MAGNETIC);
        setMetaBoolean("degs_g_bool", _bearingUnits == Angle.DEGREE
                && _northReference == NorthReference.GRID);
        setMetaBoolean("mils_g_bool", _bearingUnits == Angle.MIL
                && _northReference == NorthReference.GRID);
        setMetaBoolean("ftmi_bool", _rangeUnits == Span.ENGLISH);
        setMetaBoolean("kmm_bool", _rangeUnits == Span.METRIC);
        setMetaBoolean("nm_bool", _rangeUnits == Span.NM);
    }

    private void updateLabel() {

        // since I have no way to reproduce the playstore issue, going to make sure that
        // _bearingUnits is not null when updating the label.

        if (_bearingUnits == null) {
            _bearingUnits = Angle.DEGREE;
        }

        // This just updates the radial menu - probably shouldn't be here
        updateRadial();

        String bs;
        if (_northReference == NorthReference.GRID) {
            double gridConvergence = ATAKUtilities
                    .computeGridConvergence(_point1.get(), _point2.get());
            bs = AngleUtilities.format(AngleUtilities.wrapDeg(
                    _bearing - gridConvergence), _bearingUnits) + "G";
        } else if (_northReference == NorthReference.MAGNETIC) {
            double bearingMag = ATAKUtilities.convertFromTrueToMagnetic(
                    _point1.get(), _bearing);
            bs = AngleUtilities.format(bearingMag, _bearingUnits) + "M";
        } else {
            bs = AngleUtilities.format(_bearing, _bearingUnits) + "T";
        }

        String direction = "\u2192";
        if (_bearing > 180 && _bearing < 360)
            direction = "\u2190";

        //start out with direction
        String text = direction + " ";

        //be sure slant range was computed
        if (!Double.isNaN(_slantRange)) {
            if (_displaySlantRange) {
                text += bs + "   "
                        + SpanUtilities.formatType(_rangeUnits, _slantRange,
                                Span.METER);
            } else {
                //user pref selected ground clamped direction instead of slant range
                text += bs + "   "
                        + SpanUtilities.formatType(_rangeUnits, _range,
                                Span.METER);
            }

            //Depression angle is computed based on slant range
            //Only display if ETA is not displayed
            if (_displaySlantRange
                    && !getMetaBoolean("displayBloodhoundEta", false)
                    && _point1.get().isAltitudeValid()
                    && _point2.get().isAltitudeValid()) {
                text += "   "
                        + AngleUtilities.format(Math.abs(_depAngle),
                                _bearingUnits, false);
                if (_depAngle > 0) {
                    text += "\u2191"; //Up arrow
                } else if (_depAngle < 0) {
                    text += "\u2193"; //Down arrow
                }
            }
        } else {
            //failed to compute slant range, set ground clamped direction
            text += bs + "   "
                    + SpanUtilities.formatType(_rangeUnits, _range, Span.METER);
        }

        if (this.getMetaBoolean("displayBloodhoundEta", false)) {
            //attempt to display bloodhound ETA
            if (_pt1 != null && _pt1.hasMetaValue("bloodhoundEta")) {
                double remainingEta = _pt1.getMetaDouble("bloodhoundEta",
                        Double.NaN);
                if (!Double.isNaN(remainingEta)) {
                    String remainingEtaString = BloodHoundHUD
                            .formatTime(remainingEta);
                    text += "  " + remainingEtaString;
                }
            } else if (_pt2 != null && _pt2.hasMetaValue("bloodhoundEta")) {
                double remainingEta = _pt2.getMetaDouble("bloodhoundEta",
                        Double.NaN);
                if (!Double.isNaN(remainingEta)) {
                    String remainingEtaString = BloodHoundHUD
                            .formatTime(remainingEta);
                    text += "  " + remainingEtaString;
                }
            }
        } else {
            // Show ETA if:
            //  1) Bloodhound hasn't taken it first
            //  2) The user has selected to see it
            //  3) Another tool isn't overriding the label
            if (_prefs.getBoolean("rab_preference_show_eta", false)
                    && !getMetaBoolean("override_label", false)) {
                // prefer speed from ownship
                double speed = Double.NaN;
                Marker ownship = MapView.getMapView().getSelfMarker();
                if (ownship != null) {
                    speed = ownship.getMetaDouble("Speed", Double.NaN);
                    setMetaDouble(META_SPEED, speed);
                }

                // then check for user supplied speed
                if (!isSpeedValid(speed)) {
                    speed = getMetaDouble(META_USER_SPEED, Double.NaN);
                    setMetaDouble(META_USER_SPEED, speed);
                }

                // compute eta
                // Double.NaN if speed is NaN or 0
                double etaInSeconds = computeEta(speed);
                if (!Double.isNaN(etaInSeconds)) {
                    // then we got something valid back
                    String etaString = formatEta(etaInSeconds);
                    setMetaString(META_ETA, etaString);
                    text += "  " + etaString;
                } else {
                    setMetaString(META_ETA, null);
                }
            }
        }

        // Store the generated label
        setMetaString("label", text);

        // Label is being overriden by another tool - do not show
        if (getMetaBoolean("override_label", false))
            return;

        // Show the label on the map
        setText(text);
    }

    @Override
    public void onPointChanged(PointMapItem item) {
        if (item != null) {
            final PointMapItem lPt1 = _pt1;
            final PointMapItem lPt2 = _pt2;
            if (lPt1 == null || lPt2 == null) {
                Log.w(TAG, "Called onPointChanged with null points.");
                return;
            }
            _point1 = lPt1.getGeoPointMetaData();
            _point2 = lPt2.getGeoPointMetaData();
            _range = _point1.get().distanceTo(_point2.get());
            _slantRange = DistanceCalculations.calculateSlantRange(
                    lPt1.getPoint(), lPt2.getPoint());

            if (!Double.isNaN(_slantRange)) {
                // CAH 
                // cos(theta) = Adjacent / Hypotenuse will give you the angle offset 
                // from NADIR, then offset from 90

                double offset = -1;
                if (EGM96.getHAE(_point1.get()) <= EGM96
                        .getHAE(_point2.get())) {
                    offset = 1;
                }

                _depAngle = offset * ((Math.acos(_range / _slantRange) *
                        ConversionFactors.DEGREES_TO_RADIANS));
            }

            _bearing = _point1.get().bearingTo(_point2.get());

            updateLabel();

        }
        update();

        if (!hasMetaValue("nevercot"))
            this.persist(_mapView.getMapEventDispatcher(), null,
                    this.getClass());
    }

    @Override
    public void onMapItemMapEvent(MapItem item, MapEvent event) {
        if (item == this && event.getType().equals(MapEvent.ITEM_CLICK)) {
            Intent focus = new Intent();
            focus.setAction("com.atakmap.android.maps.FOCUS");
            focus.putExtra("uid", getUID());
            focus.putExtra("point", _touchPoint.toString());

            Intent showMenu = new Intent();
            showMenu.setAction("com.atakmap.android.maps.SHOW_MENU");
            showMenu.putExtra("uid", getUID());
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp,
            String key) {

        if (key == null)
            return;

        switch (key) {
            case "rab_color_pref":
                setStrokeColor(Color.parseColor((sp.getString(key,
                        String.valueOf(Color.RED)))));
                break;
            case "rab_north_ref_pref":
                setNorthReference(NorthReference
                        .findFromValue(Integer.parseInt(sp.getString(key,
                                String.valueOf(
                                        NorthReference.MAGNETIC.getValue())))));
                break;
            case "rab_brg_units_pref":
                setBearingUnits(
                        Angle.findFromValue(Integer.parseInt(sp.getString(
                                key,
                                String.valueOf(Angle.DEGREE.getValue())))));
                break;
            case "rab_rng_units_pref":
                try {
                    setRangeUnits(Integer.parseInt(sp.getString(key,
                            String.valueOf(Span.METRIC))));
                } catch (IllegalStateException ise) {
                    Log.e(TAG,
                            "error setting the range units for a range and bearing arrow: "
                                    + getUID(),
                            ise);
                }
                break;
            case "rab_dist_slant_range":
                setDisplaySlantRange(_prefs.getString("rab_dist_slant_range",
                        "clamped")
                        .equals("slantrange"));
                break;
            case "rng_feet_display_pref":
                int ftToMi = 5280;
                try {
                    ftToMi = Integer.parseInt(sp.getString(key,
                            String.valueOf(5280)));
                } catch (NumberFormatException ignored) {
                }
                SpanUtilities.setFeetToMileThreshold(ftToMi);
                updateLabel();
                break;
            case "rng_meters_display_pref":
                int mToKm = 2000;
                try {
                    mToKm = Integer.parseInt(sp.getString(key,
                            String.valueOf(2000)));
                } catch (NumberFormatException ignored) {
                }
                SpanUtilities.setMetersToKilometersThreshold(mToKm);
                updateLabel();
                break;
            case "rab_preference_show_eta":
                updateLabel();
                break;
        }
    }

    private final MapItem.OnGroupChangedListener _removeListener = new MapItem.OnGroupChangedListener() {
        @Override
        public void onItemAdded(MapItem item, MapGroup oldParent) {
        }

        @Override
        public void onItemRemoved(MapItem item, MapGroup oldParent) {
            if (item == _pt1 || item == _pt2) {
                removePoint(item);
            } else {
                Log.d(TAG, "error " +
                        getMetaString("callsign", "[unnamed]") + ":" + getUID()
                        +
                        " does not contain " +
                        item.getMetaString("callsign", "[unnamed]") + ":"
                        + item.getUID(),
                        new Exception());
            }
        }

    };

    /**
     * Given a point, remove it from the range and bearing arrow.
     * @param endpoint the point to remove
     */
    public void removePoint(MapItem endpoint) {
        RangeAndBearingEndpoint newPoint;
        //turn on disance lock option
        if (endpoint == _pt1) {
            removeListenersFromPoint(_pt1);

            newPoint = new RangeAndBearingEndpoint(_pt1.getGeoPointMetaData(),
                    UUID
                            .randomUUID().toString());
            _point1 = newPoint.getGeoPointMetaData();

            if (!_reversed)
                newPoint.setPart(RangeAndBearingEndpoint.PART_TAIL);
            else
                newPoint.setPart(RangeAndBearingEndpoint.PART_HEAD);

            _pt1 = newPoint;

            removeMetaData("anchorAvailable");
        } else {
            removeListenersFromPoint(_pt2);

            newPoint = new RangeAndBearingEndpoint(_pt2.getGeoPointMetaData(),
                    UUID
                            .randomUUID().toString());
            _point2 = newPoint.getGeoPointMetaData();

            if (!_reversed)
                newPoint.setPart(RangeAndBearingEndpoint.PART_HEAD);
            else
                newPoint.setPart(RangeAndBearingEndpoint.PART_TAIL);
            _pt2 = newPoint;

            this.removeMetaData("radiusAvailable");
        }
        setMetaBoolean("distanceLockAvailable", true); //turn on disance lock option

        addListenersToPoint(newPoint);
        newPoint.setParent(RangeAndBearingMapItem.this);
        newPoint.setClickable(true);
        newPoint.setMetaString("menu", "menus/rab_endpoint_menu.xml");
        newPoint.setMetaString("rabUUID", getUID());
        RangeAndBearingMapComponent.getGroup().addItem(newPoint);
        this.persist(_mapView.getMapEventDispatcher(), null, this.getClass());
    }

    /**
     * Return the type of point currently used as the start point.
     * @return the type for the point.
     */
    public String getPoint1CotType() {
        return _pt1.getType();
    }

    /**
     * Return the type of point currently used as the end point.
     * @return the type for the point.
     */
    public String getPoint2CotType() {
        return _pt2.getType();
    }

    /**
     * Return the uid of point currently used as the start point.
     * @return the uid for the point.
     */
    public String getPoint1UID() {
        String uid = _pt1.getUID();

        if (uid != null && !uid.contentEquals("")) {
            return uid;
        } else {
            return _pt1.getUID();
        }
    }

    /**
     * Return the uid of point currently used as the end point.
     * @return the uid for the point.
     */
    public String getPoint2UID() {

        String uid = _pt2.getUID();

        if (uid != null && !uid.contentEquals("")) {
            return uid;
        } else {
            return _pt2.getUID();
        }

    }

    private double computeEta(double speed) {
        return isSpeedValid(speed)
                ? _range / speed // m / m/s = s
                : Double.NaN;
    }

    private String formatEta(double eta) {
        return BloodHoundHUD.formatTime(eta);
    }

    private boolean isSpeedValid(double speed) {
        return !Double.isNaN(speed) && speed > 0;
    }

    /****************************** Serializers and Deserializers **************************/

    private CotEvent toCot() {

        // If either marker is missing then this line is invalid
        final PointMapItem pt1 = _pt1, pt2 = _pt2;
        if (pt1 == null || pt2 == null)
            return null;

        CotEvent event = new CotEvent();

        CoordinatedTime time = new CoordinatedTime();
        event.setTime(time);
        event.setStart(time);
        event.setStale(time.addDays(1));

        event.setUID(getUID());
        event.setVersion(CotEvent.VERSION_2_0);
        event.setHow("h-e");

        event.setType("u-rb-a");

        CotDetail detail = new CotDetail("detail");
        event.setDetail(detail);

        event.setPoint(new CotPoint(pt1.getPoint()));

        double range = GeoCalculations.distanceTo(pt1.getPoint(),
                pt2.getPoint());
        double bearing = GeoCalculations.bearingTo(pt1.getPoint(),
                pt2.getPoint());

        CotDetail rangeDt = new CotDetail("range");
        rangeDt.setAttribute("value", String.valueOf(range));
        detail.addChild(rangeDt);

        CotDetail bearingDt = new CotDetail("bearing");
        bearingDt.setAttribute("value", String.valueOf(bearing));
        detail.addChild(bearingDt);

        CotDetail inclination = new CotDetail("inclination");
        inclination.setAttribute("value",
                Double.toString(getInclination()));
        detail.addChild(inclination);

        if (isEndpointSerializable(pt2)) {
            CotDetail rangeUID = new CotDetail("rangeUID");
            rangeUID.setAttribute("value", getEndpointUID(pt2));
            detail.addChild(rangeUID);
        }

        if (isEndpointSerializable(pt1)) {
            CotDetail anchorUID = new CotDetail("anchorUID");
            anchorUID.setAttribute("value", getEndpointUID(pt1));
            detail.addChild(anchorUID);
        }

        CotDetail rangeUnits = new CotDetail("rangeUnits");
        CotDetail bearingUnits = new CotDetail("bearingUnits");
        CotDetail northRef = new CotDetail("northRef");

        rangeUnits.setAttribute("value", Integer.toString(getRangeUnits()));

        bearingUnits.setAttribute("value",
                Integer.toString(getBearingUnits().getValue()));

        northRef.setAttribute("value",
                Integer.toString(getNorthReference().getValue()));

        detail.addChild(rangeUnits);
        detail.addChild(bearingUnits);
        detail.addChild(northRef);

        // XXX - Trigger the color detail handler
        setMetaInteger("color", getStrokeColor());
        CotDetailManager.getInstance().addDetails(this, event);

        return event;
    }

    /**
     * Check if an endpoint is serializable when serializing the R&B line to CoT
     * @param pmi End point
     * @return True if serializable
     */
    private static boolean isEndpointSerializable(PointMapItem pmi) {
        return !(pmi instanceof RangeAndBearingEndpoint
                || pmi.getType().equals("self")
                || pmi.getType().equals("b-m-p-s-p-i")
                || pmi.hasMetaValue("atakRoleType")
                || pmi.hasMetaValue("emergency")
                || pmi.hasMetaValue("nevercot"));
    }

    /**
     * Get the point UID
     * If the point is a shape marker, use the shape UID instead
     * @param pmi Point item
     * @return Point/shape UID
     */
    private static String getEndpointUID(PointMapItem pmi) {
        if (pmi.getType().equals("shape_marker")
                || pmi.getType().equals("center_u-d-r")
                || pmi.hasMetaValue("nevercot")) {
            MapItem shp = ATAKUtilities.findAssocShape(pmi);
            if (shp instanceof AnchoredMapItem
                    && ((AnchoredMapItem) shp).getAnchorItem() == pmi)
                return shp.getUID();
        }
        return pmi.getUID();
    }

    private Folder toKml() {

        if (_point1 == null || _point2 == null) {
            Log.w(TAG, "Unable to create KML Folder without 2 points");
            return null;
        }

        try {
            // style inner ring
            Style style = new Style();
            LineStyle lstyle = new LineStyle();
            lstyle.setColor(KMLUtil.convertKmlColor(getStrokeColor()));
            lstyle.setWidth(2F);
            style.setLineStyle(lstyle);
            IconStyle istyle = new IconStyle();
            istyle.setColor(KMLUtil.convertKmlColor(getStrokeColor()));
            style.setIconStyle(istyle);

            String styleId = KMLUtil.hash(style);
            style.setId(styleId);

            // Folder element containing styles, shape and label
            Folder folder = new Folder();
            folder.setName(getText());
            List<StyleSelector> styles = new ArrayList<>();
            styles.add(style);
            folder.setStyleSelector(styles);
            List<Feature> folderFeatures = new ArrayList<>();
            folder.setFeatureList(folderFeatures);

            // line between the two points
            Placemark linePlacemark = new Placemark();
            linePlacemark.setName(getText());
            linePlacemark.setDescription(getText());
            linePlacemark.setId(getUID() + getText());
            linePlacemark.setStyleUrl("#" + styleId);
            linePlacemark.setVisibility(getVisible() ? 1 : 0);

            GeoPointMetaData[] pts = {
                    _point1, _point2
            };
            MapView mv = MapView.getMapView();
            boolean idlWrap180 = mv != null && mv.isContinuousScrollEnabled()
                    && GeoCalculations.crossesIDL(pts, 0, pts.length);
            Coordinates coordinates = new Coordinates(KMLUtil.convertKmlCoords(
                    pts, true, idlWrap180));
            LineString lineString = new LineString();
            lineString.setCoordinates(coordinates);
            lineString.setAltitudeMode("clampToGround");

            List<Geometry> geomtries = new ArrayList<>();
            geomtries.add(lineString);
            linePlacemark.setGeometryList(geomtries);
            folderFeatures.add(linePlacemark);

            Placemark pointPlacemark = new Placemark();
            pointPlacemark.setName("start");
            pointPlacemark.setId(getUID() + getText() + "start");
            pointPlacemark.setStyleUrl("#" + styleId);
            pointPlacemark.setVisibility(getVisible() ? 1 : 0);

            Point point = new Point();
            point.setAltitudeMode("clampToGround");
            point.setCoordinates(KMLUtil.convertKmlCoord(_point1, true));
            geomtries = new ArrayList<>();
            geomtries.add(point);
            pointPlacemark.setGeometryList(geomtries);
            folderFeatures.add(pointPlacemark);

            pointPlacemark = new Placemark();
            pointPlacemark.setName("end");
            pointPlacemark.setId(getUID() + getText() + "end");
            pointPlacemark.setStyleUrl("#" + styleId);
            pointPlacemark.setVisibility(getVisible() ? 1 : 0);

            point = new Point();
            point.setAltitudeMode("clampToGround");
            point.setCoordinates(KMLUtil.convertKmlCoord(_point2, true));
            geomtries = new ArrayList<>();
            geomtries.add(point);
            pointPlacemark.setGeometryList(geomtries);
            folderFeatures.add(pointPlacemark);

            return folder;
        } catch (Exception e) {
            Log.e(TAG, "Export of DrawingCircle to KML failed", e);
        }

        return null;
    }

    private KMZFolder toKmz() {
        Folder f = toKml();
        if (f == null)
            return null;
        return new KMZFolder(f);
    }

    protected OGRFeatureExportWrapper toOgrGeomtry() {
        org.gdal.ogr.Geometry geometry = new org.gdal.ogr.Geometry(
                org.gdal.ogr.ogrConstants.wkbLineString);

        double unwrap = 0;
        MapView mv = MapView.getMapView();
        if (mv != null && mv.isContinuousScrollEnabled()
                && GeoCalculations.crossesIDL(new GeoPoint[] {
                        _point1.get(), _point2.get()
                }))
            unwrap = 360;

        OGRFeatureExportWrapper.addPoint(geometry, _pt1.getPoint(), unwrap);
        OGRFeatureExportWrapper.addPoint(geometry, _pt2.getPoint(), unwrap);

        String name = getText();
        String groupName = name;
        if (getGroup() != null) {
            groupName = getGroup().getFriendlyName();
        }
        return new OGRFeatureExportWrapper(groupName, ogr.wkbLineString,
                new OGRFeatureExportWrapper.NamedGeometry(geometry, name));
    }

    protected GPXExportWrapper toGpx() {
        GpxTrack t = new GpxTrack();
        t.setName(getText());
        t.setDesc(getUID());

        double unwrap = 0;
        MapView mv = MapView.getMapView();
        if (mv != null && mv.isContinuousScrollEnabled()
                && GeoCalculations.crossesIDL(new GeoPoint[] {
                        _point1.get(), _point2.get()
                }))
            unwrap = 360;

        List<GpxTrackSegment> trkseg = new ArrayList<>();
        t.setSegments(trkseg);
        GpxTrackSegment seg = new GpxTrackSegment();
        trkseg.add(seg);
        List<GpxWaypoint> trkpt = new ArrayList<>();
        seg.setPoints(trkpt);
        trkpt.add(RouteGpxIO.convertPoint(_point1.get(), unwrap));
        trkpt.add(RouteGpxIO.convertPoint(_point2.get(), unwrap));

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

    /**
     * Code snippet to add a listener to watch for changes to meta booleans for the R+B mapitem.
     */

    private DropDownReceiver _onBooleanChanged = null;

    public void addMetaBooleanListener(DropDownReceiver listener) {
        _onBooleanChanged = listener;
    }

    @Override
    public void setMetaBoolean(String key, boolean value) {
        boolean changed = super.getMetaBoolean(key, !value) != value;
        super.setMetaBoolean(key, value);
        if (changed && _onBooleanChanged != null) {
            RangeAndBearingCompat.updateDropdownUnits(_onBooleanChanged);
        }
    }

    /**
     * End code snippet
     */
}
