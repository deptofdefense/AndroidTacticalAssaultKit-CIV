
package com.atakmap.android.vehicle;

import android.graphics.Color;
import android.os.SystemClock;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.cot.detail.PrecisionLocationHandler;
import com.atakmap.android.editableShapes.EditablePolyline;
import com.atakmap.android.imagecapture.CapturePP;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.EditAction;
import com.atakmap.android.util.Undoable;
import com.atakmap.app.R;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Vehicle polyline marker
 */
public class VehicleShape extends EditablePolyline implements VehicleMapItem {

    private static final String TAG = "VehicleShape";
    private static final int DEFAULT_WEIGHT = 2;
    public static final int DEFAULT_STROKE = 0xFF00FF00;
    public static final int DEFAULT_FILL = 0x4000FF00;

    public static final String COT_TYPE = "u-d-v";

    private final MapGroup _mapGroup;
    private boolean _setup = false;
    private int _strokeColor = DEFAULT_STROKE;
    private int _fillColor = DEFAULT_FILL;

    public VehicleShape(MapView mapView, final String uid) {
        super(mapView, uid);
        _mapGroup = VehicleMapComponent.getVehicleGroup(mapView);
        setClosed(true);
        setMetaString("closed_line", "true");
        setMetaBoolean("static_shape", true);
        setMetaBoolean("editable", true);
        setMetaBoolean("archive", true);
        setMetaString("menu", getShapeMenu());
        setMetaString("iconUri", ATAKUtilities.getResourceUri(
                R.drawable.pointtype_aircraft));
        setType(COT_TYPE);
        setHeightStyle(HEIGHT_STYLE_NONE);
    }

    public void setup(String model, String title, GeoPointMetaData loc,
            double trueDeg,
            boolean fromUser) {
        if (_setup) {
            setVehicleModel(model);
            setAzimuth(trueDeg, NorthReference.TRUE);
            moveClosedSet(getCenter(), loc);
            return;
        }
        double[] dimen = VehicleBlock.getBlockDimensions(model);
        super.setTitle(title);
        setPoints(VehicleBlock.buildPolyline(model, loc.get(), trueDeg));
        setStrokeWeight(DEFAULT_WEIGHT);
        setStyle(Polyline.STYLE_STROKE_MASK | Polyline.STYLE_CLOSED_MASK
                | Polyline.STYLE_FILLED_MASK);
        setMetaString("vehicle_model", model);
        setMetaDouble("length", dimen[0]);
        setMetaDouble("width", dimen[1]);
        setHeight(dimen[2]);
        setMetaDouble("azimuth", trueDeg);
        if (fromUser)
            setMetaString("entry", "user");

        Marker m = new Marker(loc, UUID.randomUUID().toString());
        m.setTitle(title);
        m.setMetaString("shapeName", title);
        m.setType("shape_marker");
        m.setMetaBoolean("drag", false);
        m.setMetaBoolean("editable", true);
        m.setMetaBoolean("addToObjList", false);
        m.setMetaString("how", "h-g-i-g-o");
        m.setMetaString("menu", getMarkerMenu());
        m.setMetaDouble("minRenderScale", Double.MAX_VALUE);
        m.setMetaString(getUIDKey(), getUID());
        m.setMetaBoolean("nevercot", true);
        m.setMetaBoolean("archive", true);
        m.setMetaBoolean("vehicle_marker", true);
        String parentCallsign = MapView._mapView.getDeviceCallsign();
        String parentUID = MapView.getDeviceUid();
        m.setMetaString("parent_callsign", parentCallsign);
        m.setMetaString("parent_uid", parentUID);
        m.setMetaString(
                "parent_type",
                MapView._mapView.getMapData()
                        .getString("deviceType", "a-f-G"));
        m.setMetaString("offscreen_icon_uri", getMetaString("iconUri", null));
        m.setMetaString("production_time",
                new CoordinatedTime().toString());
        _mapGroup.addItem(m);
        setShapeMarker(m);
        // Fine adjustment fix
        m.addOnPointChangedListener(new PointMapItem.OnPointChangedListener() {
            @Override
            public void onPointChanged(final PointMapItem item) {
                GeoPointMetaData gp = item.getGeoPointMetaData();
                GeoPointMetaData center = getCenter();
                double dist = gp.get().distanceTo(center.get());
                if (dist > 0.01) {
                    moveClosedSet(getCenter(), gp);
                    save();
                }
            }
        });
        _setup = true;
        updateVisibility();
    }

    @Override
    public double getWidth() {
        return getMetaDouble("width", 0);
    }

    @Override
    public double getLength() {
        return getMetaDouble("length", 0);
    }

    @Override
    public void setAzimuth(double deg, NorthReference ref) {
        double trueDeg = deg;
        if (ref.equals(NorthReference.MAGNETIC))
            trueDeg = ATAKUtilities.convertFromMagneticToTrue(getCenter().get(),
                    deg);
        trueDeg = (trueDeg % 360) + (trueDeg < 0 ? 360 : 0);
        if (Double.compare(getAzimuth(NorthReference.TRUE), trueDeg) != 0) {
            setMetaDouble("azimuth", trueDeg);
            GeoPointMetaData loc = getCenter();
            setPoints(VehicleBlock.buildPolyline(getVehicleModel(), loc.get(),
                    trueDeg));
            moveClosedSet(getCenter(), loc);
            onAzimuthChanged();
        }
    }

    @Override
    public double getAzimuth(NorthReference ref) {
        double azi = getMetaDouble("azimuth", 0.0);
        if (ref.equals(NorthReference.MAGNETIC))
            return ATAKUtilities.convertFromTrueToMagnetic(getCenter().get(),
                    azi);
        return azi;
    }

    public void setVehicleModel(String model) {
        if (!model.equals(getVehicleModel())) {
            GeoPointMetaData loc = getCenter();
            double[] dimen = VehicleBlock.getBlockDimensions(model);
            setPoints(VehicleBlock.buildPolyline(model, loc.get(),
                    getAzimuth(NorthReference.TRUE)));
            setMetaString("vehicle_model", model);
            setMetaDouble("length", dimen[0]);
            setMetaDouble("width", dimen[1]);
            setHeight(dimen[2]);
            moveClosedSet(getCenter(), loc);
        }
    }

    @Override
    public boolean setPoint(int index, GeoPointMetaData gp) {
        if (!isBulkOperation()) {
            if (index >= 0 && index <= getNumPoints()) {
                // Move entire vehicle rather than single point
                GeoPointMetaData old = getPoint(index);
                moveClosedSet(getCenter(),
                        GeoPointMetaData.wrap(GeoCalculations.pointAtDistance(
                                getCenter().get(),
                                old.get().bearingTo(gp.get()),
                                old.get().distanceTo(gp.get()))));
                return true;
            }
            return false;
        }
        return super.setPoint(index, gp);
    }

    @Override
    public void moveClosedSet(GeoPointMetaData oldPoint,
            GeoPointMetaData newPoint) {
        super.moveClosedSet(oldPoint, newPoint);
        onPositionChanged(oldPoint, newPoint);
    }

    @Override
    public Undoable getUndoable() {
        return _saveOnRun;
    }

    // Used to persist after fine-adjust or coordinate entry
    private final Undoable _saveOnRun = new Undoable() {
        @Override
        public void undo() {
        }

        @Override
        public boolean run(EditAction action) {
            if (action.run()) {
                save();
                return true;
            }
            return false;
        }
    };

    public String getVehicleModel() {
        return getMetaString("vehicle_model", "");
    }

    /**
     * Update visibility of shape and marker
     * At lower zoom levels only the marker is visible
     * Note: this only hides (still clickable; doesn't affect getVisible())
     */
    void updateVisibility() {
        double res = ATAKUtilities.getMetersPerPixel(getCenter().get());
        int mask = res < 2 ? Color.WHITE : 0xFFFFFF;
        super.setStrokeColor(_strokeColor & mask);
        super.setFillColor(_fillColor & mask);
        final Marker center = getShapeMarker();
        if (center != null) {
            Icon ico = center.getIcon();
            if (ico != null) {
                Icon.Builder builder = ico.buildUpon();
                builder.setColor(Icon.STATE_DEFAULT,
                        _strokeColor & (res < 2 ? 0xFFFFFF : Color.WHITE));
                center.setIcon(builder.build());
            }
            center.setShowLabel(
                    res < 2 && hasMetaValue("showLabel") || res >= 2);
        }
    }

    public void updateOffscreenInterest() {
        Marker center = getMarker();
        if (center != null)
            center.setMetaLong("offscreen_interest",
                    SystemClock.elapsedRealtime());
    }

    @Override
    public void setColor(int color) {
        int color_solid = Color.rgb(
                Color.red(color), Color.green(color), Color.blue(color));
        _strokeColor = color_solid;
        _fillColor = color;
        //super.setColor(color_solid);
        //setFillColor(color);
        final Marker center = getShapeMarker();
        if (center != null) {
            center.setMetaInteger("color", color_solid);
            center.setMetaInteger("offscreen_icon_color", color_solid);
            center.refresh(mapView.getMapEventDispatcher(), null,
                    this.getClass());
        }
        updateVisibility();
    }

    @Override
    public void setFillColor(final int color) {
        _fillColor = color;
        updateVisibility();
    }

    /**
     * Get the stored stroke color (rather than what the renderer uses)
     * @param actual True for actual stroke color
     * @return Actual stroke color
     */
    public int getStrokeColor(final boolean actual) {
        if (actual)
            return _strokeColor;
        return super.getStrokeColor();
    }

    public int getFillColor(boolean actual) {
        if (actual)
            return _fillColor;
        return super.getFillColor();
    }

    @Override
    public void setTitle(final String title) {
        super.setTitle(title);

        final Marker center = getShapeMarker();
        if (center != null) {
            center.setTitle(getTitle());
            center.refresh(mapView.getMapEventDispatcher(), null,
                    this.getClass());
        }
        updateVisibility();
    }

    @Override
    protected String getShapeMenu() {
        return getMetaString("shapeMenu", "menus/vehicle_shape_menu.xml");
    }

    protected String getMarkerMenu() {
        return getMetaString("markerMenu",
                "menus/vehicle_shape_marker_menu.xml");
    }

    public void save() {
        if (getGroup() == null)
            _mapGroup.addItem(this);
        persist(mapView.getMapEventDispatcher(), null, this.getClass());
    }

    /**
     * Toggle the vehicle marker label
     * @param showLabel True to show the label
     */
    public void setShowLabel(boolean showLabel) {
        toggleMetaData("showLabel", showLabel);
        Marker m = getShapeMarker();
        if (m != null) {
            m.toggleMetaData("showLabel", showLabel);
            m.setMetaDouble("minRenderScale",
                    showLabel ? CapturePP.DEFAULT_MIN_RENDER_SCALE
                            : Double.MAX_VALUE);
        }
        updateVisibility();
    }

    public static boolean isVehicle(MapItem item) {
        return item != null && (item.getType().equals("u-d-v")
                || item.getMetaBoolean("vehicle_marker", false));
    }

    @Override
    protected CotEvent toCot() {

        CotEvent cotEvent = new CotEvent();

        CoordinatedTime time = new CoordinatedTime();
        cotEvent.setTime(time);
        cotEvent.setStart(time);
        cotEvent.setStale(time.addDays(1));

        cotEvent.setUID(getUID());
        cotEvent.setVersion("2.0");
        cotEvent.setHow("h-e");
        cotEvent.setType(COT_TYPE);
        Marker m = getMarker();
        GeoPointMetaData pointMD = m != null ? m.getGeoPointMetaData()
                : getCenter();
        cotEvent.setPoint(new CotPoint(pointMD.get()));

        CotDetail detail = new CotDetail("detail");
        cotEvent.setDetail(detail);

        CotDetail prec = PrecisionLocationHandler.toPrecisionLocation(pointMD);
        if (prec != null)
            detail.addChild(prec);

        if (hasMetaValue("showLabel")) {
            CotDetail showLabel = new CotDetail("showLabel");
            showLabel.setAttribute("value", "true");
            detail.addChild(showLabel);
        }

        CotDetail model = new CotDetail("model");
        model.setAttribute("value", getMetaString("vehicle_model", ""));
        detail.addChild(model);

        // Course (true degrees)
        CotDetail track = new CotDetail("track");
        detail.addChild(track);
        track.setAttribute("course",
                Double.toString(getAzimuth(NorthReference.TRUE)));

        CotDetailManager.getInstance().addDetails(this, cotEvent);

        return cotEvent;
    }

    /* LISTENERS */

    private final List<AzimuthChangedListener> _azimuthListeners = new ArrayList<>();

    public interface AzimuthChangedListener {
        void onChange(VehicleShape shape, double trueDeg);
    }

    public void addAzimuthChangedListener(AzimuthChangedListener acl) {
        if (!_azimuthListeners.contains(acl))
            _azimuthListeners.add(acl);
    }

    public void removeAzimuthChangedListener(AzimuthChangedListener acl) {
        if (!_azimuthListeners.contains(acl))
            _azimuthListeners.remove(acl);
    }

    protected void onAzimuthChanged() {
        for (AzimuthChangedListener acl : _azimuthListeners)
            acl.onChange(this, getAzimuth(NorthReference.TRUE));
    }

    private final List<PositionChangedListener> _posListeners = new ArrayList<>();

    public interface PositionChangedListener {
        void onChange(VehicleShape shape, GeoPoint oldPos, GeoPoint newPos);
    }

    public void addPositionChangedListener(PositionChangedListener acl) {
        if (!_posListeners.contains(acl))
            _posListeners.add(acl);
    }

    public void removePositionChangedListener(PositionChangedListener acl) {
        if (!_posListeners.contains(acl))
            _posListeners.remove(acl);
    }

    protected void onPositionChanged(GeoPointMetaData oldPos,
            GeoPointMetaData newPos) {
        for (PositionChangedListener acl : _posListeners)
            acl.onChange(this, oldPos.get(), newPos.get());
    }
}
