
package com.atakmap.android.coordoverlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PointF;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.graphics.Typeface;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapMode;
import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MetaMapPoint;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.menu.MenuLayoutWidget;
import com.atakmap.android.selfcoordoverlay.SelfCoordOverlayUpdater;
import com.atakmap.android.util.SpeedFormatter;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.PointMapItem.OnPointChangedListener;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.user.SpecialPointButtonTool;
import com.atakmap.android.user.TLECategory;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.AltitudeUtilities;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MapWidget.OnLongPressListener;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.android.widgets.TextWidget;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.conversions.Area;
import com.atakmap.coremap.conversions.AreaUtilities;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.conversion.EGM96;

import com.atakmap.coremap.maps.coords.ErrorCategory;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.map.CameraController;
import com.atakmap.map.elevation.ElevationData;
import com.atakmap.map.elevation.ElevationManager;

import java.util.Arrays;

import androidx.annotation.NonNull;

/**
 * TODO: Outline all expected behavior and rewrite this entire class
 */
public class CoordOverlayMapReceiver extends BroadcastReceiver implements
        OnPointChangedListener, MapGroup.OnItemListChangedListener,
        SharedPreferences.OnSharedPreferenceChangeListener,
        Marker.OnTrackChangedListener, MapWidget.OnClickListener {

    MenuLayoutWidget mw = MapMenuReceiver.getMenuWidget();

    private final static int MAX_LINE_COUNT = 12;

    private final static ElevationManager.QueryParameters DSM_FILTER = new ElevationManager.QueryParameters();
    static {
        DSM_FILTER.elevationModel = ElevationData.MODEL_SURFACE;
    }

    private final static ElevationManager.QueryParameters DTM_FILTER = new ElevationManager.QueryParameters();
    static {
        DTM_FILTER.elevationModel = ElevationData.MODEL_TERRAIN;
    }

    public static final String TAG = "CoordOverlayMapReceiver";

    public static final String SHOW_DETAILS = "com.atakmap.android.maps.SHOW_DETAILS";
    protected static final String COORD_COPY = "com.atakmap.android.maps.COORD_COPY";
    protected static final String COORD_ENLARGE = "com.atakmap.android.maps.COORD_ENLARGE";

    //TODO why does this class have _pointItem and _activeMarker?
    private PointMapItem _pointItem;
    protected PointMapItem self;

    private final MapView _mapView;
    protected TextWidget _positionText;
    private String _savedText;
    protected boolean _marker;
    protected String _activeMarkerUID;
    protected Marker _activeMarker;
    private GeoPointMetaData _activePoint;
    protected SharedPreferences _prefs;

    protected Angle bearingUnits;
    protected int rangeSystem;
    protected NorthReference northRef;
    protected Area areaRef;
    protected Span altUnits;

    private final SpeedFormatter speedFormatter;
    private final String[] speedUnits;

    private String coordString;
    private Marker radialFocus;

    private TextWidget selfCoordText;
    private TextWidget.OnTextChangedListener selfCoordChangedListener;

    public CoordOverlayMapReceiver(final MapView mapView) {
        _mapView = mapView;
        _marker = false;

        speedFormatter = SpeedFormatter.getInstance();

        speedUnits = mapView.getContext().getResources()
                .getStringArray(R.array.speed_units_display);

        RootLayoutWidget root = (RootLayoutWidget) _mapView.getComponentExtra(
                "rootLayoutWidget");
        final LinearLayoutWidget trLayout = root.getLayout(
                RootLayoutWidget.TOP_RIGHT);
        _positionText = new TextWidget("", 2);
        _positionText.setName("Position Text");
        _positionText.setMargins(0f, 16f, 0f, 0f);
        _positionText.addOnClickListener(this);
        _positionText.setVisible(false);
        trLayout.addChildWidgetAt(0, _positionText);

        _prefs = PreferenceManager.getDefaultSharedPreferences(_mapView
                .getContext());
        _prefs.registerOnSharedPreferenceChangeListener(this);
        _mapView.getRootGroup().addOnItemListChangedListener(this);

        self = _mapView.getSelfMarker();
        if (self != null)
            self.addOnPointChangedListener(this);

        AtakBroadcast.DocumentedIntentFilter intentFilter = new AtakBroadcast.DocumentedIntentFilter();
        intentFilter.addAction(COORD_ENLARGE);
        intentFilter.addAction(COORD_COPY);
        for (MapMode mode : MapMode.values())
            intentFilter.addAction(mode.getIntent());
        AtakBroadcast.getInstance().registerReceiver(coordReceiver,
                intentFilter);

        initUnits();

        // allow for long press enlargement of the detail
        boolean b = _prefs.getBoolean("detail.enlarged", false);
        MapTextFormat _format = MapView.getTextFormat(Typeface.DEFAULT,
                b ? 2 : 7);
        _positionText.setTextFormat(_format);

        OnLongPressListener _textLongPressListener = new OnLongPressListener() {
            @Override
            public void onMapWidgetLongPress(MapWidget widget) {
                // pop out the radial menu

                if (mw == null)
                    return;
                if (radialFocus == null) {
                    radialFocus = new Marker("CoordWidgetFocus");
                    radialFocus.setMetaBoolean("addToObjList", false);
                    radialFocus.setMetaBoolean("nevercot", true);
                    radialFocus.setVisible(false);
                }

                float inset = 30 * MapView.DENSITY;
                PointF p = widget.getAbsolutePosition();
                p.x += inset;
                p.y += widget.getHeight() - inset;

                radialFocus.setMetaString("menu",
                        "menus/coord_widget_menu.xml");

                if (mw.getMapItem() != radialFocus) {
                    mw.openMenuOnItem(radialFocus);
                    mapView.getMapController()
                            .removeOnFocusPointChangedListener(mw);
                    mw.onFocusPointChanged(p.x, p.y);
                } else
                    mw.clearMenu();

            }
        };
        _positionText.addOnLongPressListener(_textLongPressListener);

    }

    /**
     * Set the self coordinate overlay updater instance used to make updates
     * to the self marker coordinate text (see ATAK-15612)
     * @param updater Self coordinate updater
     */
    void setSelfCoordOverlayUpdater(SelfCoordOverlayUpdater updater) {
        selfCoordText = updater.getWidget();
        if (selfCoordText != null) {
            selfCoordText.addOnTextChangedListener(
                    selfCoordChangedListener = new TextWidget.OnTextChangedListener() {
                        @Override
                        public void onTextWidgetTextChanged(TextWidget widget) {
                            if (_activeMarker == self)
                                updatePointText(self);
                        }
                    });
        }
    }

    protected void initUnits() {

        // see the values in Span.java
        rangeSystem = Integer.parseInt(_prefs.getString(
                "rab_rng_units_pref", String.valueOf(1)));

        bearingUnits = Angle.findFromValue(
                Integer.parseInt(_prefs.getString("rab_brg_units_pref",
                        String.valueOf(0))));

        northRef = NorthReference.findFromValue(
                Integer.parseInt(_prefs.getString("rab_north_ref_pref",
                        String.valueOf(NorthReference.MAGNETIC.getValue()))));

        areaRef = Area.findFromValue(_prefs.getInt("area_display_pref",
                Area.METRIC));

        switch (Integer
                .parseInt(_prefs.getString("alt_unit_pref", "0"))) {
            case 0:
                altUnits = Span.FOOT;
                break;
            case 1:
                altUnits = Span.METER;
                break;
            default: // default to feet
                altUnits = Span.FOOT;
                break;
        }

    }

    void dispose() {
        selfCoordText.removeOnTextChangedListener(selfCoordChangedListener);
        _mapView.getRootGroup().removeOnItemListChangedListener(this);
        _prefs.unregisterOnSharedPreferenceChangeListener(this);
        if (self != null)
            self.removeOnPointChangedListener(this);
    }

    @Override
    public void onPointChanged(final PointMapItem item) {
        // if either the self marker or the activeMarker move, the onPointChanged will be tripped
        // in either case - the handlePointChanged will only be for the active Marker.
        if (_activeMarker != null) {

            if (item == _activeMarker)
                _activePoint = _activeMarker.getGeoPointMetaData();
            else if (item instanceof MetaMapPoint
                    && item.getUID().equals("TARGET_POINT")) {
                // Fine-adjust target point data
                String targetUID = item.getMetaString("targetUID", null);
                if (FileSystemUtils.isEquals(targetUID, _activeMarker.getUID()))
                    _activePoint = item.getGeoPointMetaData();
            }

            handlePointChange(_activeMarker);
        }
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
        if (item.getUID().equals(_activeMarkerUID)) {
            if (item instanceof Marker) {
                setActiveMarker((Marker) item);
                _activeMarker.addOnPointChangedListener(this);
                _activeMarker.addOnTrackChangedListener(this);
            }
        }
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
    }

    @Override
    public void onMapWidgetClick(MapWidget widget, MotionEvent event) {
        if (_pointItem != null) {
            final GeoPoint point = _pointItem.getPoint();
            CameraController.Programmatic.panTo(
                    _mapView.getRenderer3(), point, false);
        }
    }

    @Override
    public void onSharedPreferenceChanged(
            SharedPreferences sharedPreferences, String key) {

        if (key == null)
            return;

        if (key.equals("rab_rng_units_pref")
                || key.equals("rab_brg_units_pref") ||
                key.equals("alt_unit_pref")
                || key.equals("alt_display_pref")
                || key.equals("rab_north_ref_pref")
                || key.equals("area_display_pref")) {
            initUnits();
            if (_activeMarker != null)
                handlePointChange(_activeMarker);
        }
        if (key.equals("coord_display_pref") && _activeMarker != null) {
            handlePointChange(_activeMarker);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals("com.atakmap.android.action.SHOW_POINT_DETAILS")) {
            _activeMarkerUID = intent.getStringExtra("uid");
            _adjustOverlay(intent.getBooleanExtra("displayRnB", true));
        } else if (action
                .equals("com.atakmap.android.action.HIDE_POINT_DETAILS")
                && _activeMarker != null &&
                _activeMarker.getUID().equals(intent.getStringExtra("uid"))) {
            //Log.d(TAG, "HIDE_POINT_DETAILS: " + intent.getStringExtra("uid"));
            if (_activeMarker != null) {
                _activeMarker
                        .removeOnPointChangedListener(this);
                _activeMarker
                        .removeOnTrackChangedListener(this);
            }
            _activeMarkerUID = null;
            setActiveMarker(null);
            _savedText = null;
            _positionText.setVisible(false);
        } else if (action.equals("com.atakmap.android.maps.SHOW_DETAILS")) {
            PointMapItem pmi = null;
            GeoPointMetaData point = null;
            String title = null;
            double speed = -1, heading = -1;
            boolean estimate = false;
            boolean teamMate = false;
            double area = Double.NaN;
            double height = Double.NaN;

            ElevationDisplayInfo el = new ElevationDisplayInfo();
            if (intent.hasExtra("uid")) {
                String pointUID = intent.getStringExtra("uid");
                if (pointUID != null) {
                    MapItem item = _mapView.getMapItem(pointUID);
                    if (item != null)
                        height = item.getHeight();
                    if (_activeMarker != null) {
                        _activeMarker.removeOnPointChangedListener(this);
                        _activeMarker.removeOnTrackChangedListener(this);
                    }
                    if (item instanceof Marker) {
                        String label = item.getMetaString("label",
                                ATAKUtilities.getDisplayName(item));
                        if (label != null && !label.equals("Red X")
                                && !label.startsWith("Local SPI")) {
                            setActiveMarker((Marker) item);
                            _activeMarker.addOnPointChangedListener(this);
                            _activeMarker.addOnTrackChangedListener(this);
                        }
                    }

                    if (ToolManagerBroadcastReceiver.getInstance()
                            .getActiveTool() instanceof SpecialPointButtonTool
                            && item != null && item.getType()
                                    .equals("b-m-p-s-p-i")) {
                        _marker = true;
                        return; // Do nothing
                    } else
                        _marker = false;

                    if (item instanceof PointMapItem) {
                        pmi = (PointMapItem) item;
                        if (pmi.getMetaBoolean("toggleDetails", false)
                                && pmi == _pointItem
                                && !intent.getBooleanExtra("force", false)) {
                            _positionText.setVisible(false);
                            _setPointMapItem(null);
                        } else {
                            _setPointMapItem(pmi);
                            point = pmi.getGeoPointMetaData();
                        }

                        MapItem s = ATAKUtilities.findAssocShape(pmi);
                        if (s instanceof Shape) {
                            area = ((Shape) s).getArea();
                        }
                    } else if (item instanceof Shape) {
                        point = GeoPointMetaData
                                .wrap(((Shape) item).getClickPoint());

                        area = ((Shape) item).getArea();
                    } else if (item != null) {
                        try {
                            String p = item.getMetaString("menu_point",
                                    item.getMetaString("last_touch", null));
                            point = GeoPointMetaData
                                    .wrap(GeoPoint.parseGeoPoint(p));
                        } catch (Exception e) {
                            Log.e(TAG, "error: ", e);
                        }
                    }

                    title = ATAKUtilities.getDisplayName(item);

                    if (item instanceof Marker) {
                        if (item.getType().equals("self"))
                            speed = item.getMetaDouble("Speed", Double.NaN);
                        else
                            speed = ((Marker) item).getTrackSpeed();

                        heading = ((Marker) item).getTrackHeading();

                        if (Double.isNaN(speed)) {
                            speed = item.getMetaDouble("est.speed", Double.NaN);
                            estimate = true;
                        }

                        if (Double.isNaN(heading)) {
                            heading = item.getMetaDouble("est.course",
                                    Double.NaN);
                            estimate = true;
                        }

                        teamMate = item.hasMetaValue("team");
                        el.doNotDisplayAgl = item.getMetaBoolean(
                                "doNotDisplayAgl", false);
                    }
                    if (item != null) {
                        if (item.getMetaBoolean("extendedElevationDisplay",
                                false) && point != null) {
                            // pull terrain
                            double t = ElevationManager.getElevation(
                                    point.get(), DTM_FILTER, el.terrain);

                            // pull surface
                            double s = ElevationManager.getElevation(
                                    point.get(), DSM_FILTER, el.surface);

                        }
                    }
                }
            }
            if (point == null && intent.hasExtra("point")) {
                try {
                    point = GeoPointMetaData.wrap(GeoPoint.parseGeoPoint(intent
                            .getStringExtra("point")));
                } catch (Exception ex) {
                    Log.e(TAG, "error: ", ex);
                }
            }

            if (point != null && point.get().isValid()) {
                if (pmi != null) {
                    boolean displayRB = pmi.getMetaBoolean("pairinglineself_on",
                            false);
                    findDistanceAndBearing(pmi);
                    _updatePoint(point, title, null,
                            pmi.getMetaDouble("distance", 0.0d),
                            pmi.getMetaDouble("bearing", 0.0d), displayRB,
                            speed, heading, estimate, height, area, teamMate,
                            el);
                } else {
                    _updatePoint(point, title, null, -1, 0, false,
                            speed, heading, estimate, height, area, teamMate,
                            el);

                }
            }
        } else if (action.equals("com.atakmap.android.maps.HIDE_DETAILS")) {

            if (_activeMarker != null) {
                _activeMarker.removeOnPointChangedListener(this);
                _activeMarker.removeOnTrackChangedListener(this);
                setActiveMarker(null);
            }
            _marker = true;
            if (!FileSystemUtils.isEmpty(_savedText)
                    && _positionText != null
                    && _positionText.getTextFormat() != null) {
                // restore the redXMarker's coord overlay text, and reposition the overlay for it's
                // new size.

                _positionText.setText(_savedText);
                boolean disable = _pointItem != null
                        ? _pointItem.getMetaBoolean("disableCoordinateOverlay",
                                false)
                        : false;
                _positionText.setVisible(!disable);

                _savedText = null;
            } else if (_activeMarker != null && _activeMarker.getType()
                    .equals("b-m-p-s-p-i")) {
                _adjustOverlay(true);
                if (_pointItem == _activeMarker)
                    return;
            } else if (_positionText != null) {
                _positionText.setVisible(false);
            }
            _setPointMapItem(null);

            if (_activeMarker == null && _activeMarkerUID != null)
                _adjustOverlay(true);
        }
    }

    protected void _adjustOverlay(final boolean displayRB) {
        if (_activeMarker != null) {
            _activeMarker.removeOnPointChangedListener(this);
            _activeMarker.removeOnTrackChangedListener(this);
        }
        MapItem pmi = _mapView.getMapItem(_activeMarkerUID);
        setActiveMarker(pmi instanceof Marker ? (Marker) pmi : null);

        if (_activeMarker != null) {
            _activeMarker.addOnPointChangedListener(this);
            _activeMarker.addOnTrackChangedListener(this);
            String label = _activeMarker.getMetaString("label",
                    ATAKUtilities.getDisplayName(_activeMarker));
            findDistanceAndBearing(_activeMarker);

            ElevationDisplayInfo el = new ElevationDisplayInfo();
            el.doNotDisplayAgl = _activeMarker.getMetaBoolean(
                    "doNotDisplayAgl",
                    false);

            if (_activeMarker.getMetaBoolean("extendedElevationDisplay",
                    false)) {
                final GeoPointMetaData point = _activeMarker
                        .getGeoPointMetaData();

                // pull terrain
                double t = ElevationManager.getElevation(
                        point.get().getLatitude(),
                        point.get().getLongitude(), DTM_FILTER, el.terrain);

                // pull surface
                double s = ElevationManager.getElevation(
                        point.get().getLatitude(),
                        point.get().getLongitude(), DSM_FILTER, el.surface);
            }

            boolean estimated = false;
            double speed;
            if (_activeMarker.getType().equals("self"))
                speed = _activeMarker.getMetaDouble("Speed", Double.NaN);
            else
                speed = _activeMarker.getTrackSpeed();

            if (Double.isNaN(speed)) {
                speed = _activeMarker.getMetaDouble("est.speed", Double.NaN);
                estimated = true;
            }

            double heading = _activeMarker.getTrackHeading();
            if (Double.isNaN(heading)) {
                heading = _activeMarker.getMetaDouble("est.course", Double.NaN);
                estimated = true;
            }

            _updatePoint(_activeMarker.getGeoPointMetaData(), label, null,
                    _activeMarker.getMetaDouble("distance", 0.0d),
                    _activeMarker.getMetaDouble("bearing", 0.0d),
                    displayRB,
                    speed, heading, false, _activeMarker.getHeight(),
                    Double.NaN,
                    _activeMarker.hasMetaValue("team"),
                    el);
        }
    }

    protected String constructCoordText(final String label,
            final String sublabel,
            final GeoPointMetaData pointWithMetadata, final double distance,
            final double bearing,
            final boolean displayRB, final double speed, final double heading,
            final boolean estimate, final double height, final double area,
            final boolean teamMate,
            final ElevationDisplayInfo el,
            int[] lineColors) {

        GeoPoint point = pointWithMetadata.get();

        coordString = getCoords(point);

        int lineNum = 0;

        StringBuilder retval = new StringBuilder();
        if (label != null) {
            lineColors[lineNum++] = -1;
            retval.append(label);
            retval.append("\n");
        }

        if (sublabel != null) {
            lineColors[lineNum++] = -1;
            retval.append(sublabel);
            retval.append("\n");
        }

        lineColors[lineNum++] = -1;
        retval.append(coordString);

        String source = "";
        if (point.isAltitudeValid())
            source = pointWithMetadata.getAltitudeSource();

        String altString = "--- ft MSL";
        if (point.isAltitudeValid()) {
            if (el.doNotDisplayAgl) {
                //force/skip AGL display
                String altRef = _prefs
                        .getString("alt_display_pref", "MSL");
                //just use fixed MSL or HAE based on prefs
                if (altRef.equals("HAE"))
                    altString = EGM96.formatHAE(point, altUnits);
                else
                    altString = EGM96.formatMSL(point, altUnits);

            } else {
                altString = AltitudeUtilities.format(point, _prefs);
            }
        }

        lineColors[lineNum++] = -1;
        retval.append("\n");
        retval.append(altString);
        if (!source.equals(GeoPointMetaData.UNKNOWN)) {
            retval.append(" ");
            retval.append(source);
        }
        if (el.terrain.get().isAltitudeValid()
                && el.terrain.get().getAltitude() != EGM96
                        .getHAE(point)) {
            lineColors[lineNum++] = -1;
            retval.append("\nTerrain ");
            retval.append(AltitudeUtilities.format(el.terrain.get(),
                    _prefs));
            if (!el.terrain.getAltitudeSource()
                    .equals(GeoPointMetaData.UNKNOWN)) {
                retval.append(" ");
                retval.append(el.terrain.getAltitudeSource());
            }
        }

        //System.out.println("shb (surface): " + el.surface.get());
        //System.out.println("shb (surface altval): " + el.surface.get().isAltitudeValid());
        if (el.surface.get().isAltitudeValid()) {
            lineColors[lineNum++] = -1;
            retval.append("\nSurface ");
            retval.append(AltitudeUtilities.format(el.surface.get(),
                    _prefs));
            if (!el.surface.getAltitudeSource()
                    .equals(GeoPointMetaData.UNKNOWN)) {
                retval.append(" ");
                retval.append(el.surface.getAltitudeSource());
            }
        }

        if (el.surface.get().isAltitudeValid()
                && el.terrain.get().isAltitudeValid()) {

            final double surfaceHeight = EGM96.getHAE(el.surface.get())
                    - EGM96.getHAE(
                            el.terrain.get());
            lineColors[lineNum++] = -1;
            retval.append("\nSurface Height ");
            retval.append(SpanUtilities.format(surfaceHeight, Span.METER,
                    altUnits));
            retval.append(" AGL");
        }

        boolean leNeedsNewLine = true;
        if (!Double.isNaN(point.getCE())) {
            lineColors[lineNum++] = -1;
            retval.append("\nCE ");
            retval.append(TLECategory.getCEString(point.getCE()));
            leNeedsNewLine = false;
        }
        if (!Double.isNaN(point.getLE())) {
            if (leNeedsNewLine) {
                retval.append("\n");
                lineColors[lineNum++] = -1;
            } else {
                retval.append(" ");
            }
            retval.append("LE ");
            retval.append(TLECategory.getCEString(point.getLE()));
        }

        if (GeoPointMetaData.isPrecisionImageryDerived(pointWithMetadata)) {
            final TLECategory cat = TLECategory.getCategory(point.getCE());
            lineColors[lineNum++] = cat.getColor();

            retval.append("\n ");
            retval.append(pointWithMetadata.getGeopointSource());
            retval.append(" ");
            retval.append(ErrorCategory.getCategory(point.getCE()));
        }

        // based on WinTAK's decision to show area and height 0.5 km2, 50.00 m Hgt
        String heightStr = "";
        String areaStr = "";
        if (!Double.isNaN(area))
            areaStr = AreaUtilities.formatArea(areaRef.getValue(), area,
                    Area.METER2);

        if (!Double.isNaN(height) && Double.compare(height, 0.0) != 0.0)
            heightStr = SpanUtilities.format(height, Span.METER,
                    altUnits);

        if (areaStr.length() > 0 || heightStr.length() > 0) {
            String haline = "";
            if (areaStr.length() > 0)
                haline = areaStr;

            if (heightStr.length() > 0) {
                if (haline.length() > 0)
                    haline += "  ";
                haline += heightStr + " Hgt";
            }

            if (haline.length() > 0) {
                retval.append("\n");
                lineColors[lineNum++] = -1;
                retval.append(haline);

            }
        }

        // XXX: if the distance is 0, you are on top of the item.
        // no real reason to provide a a range and bearing.
        if (displayRB && distance > 0) {

            double bearingDisplay = bearing;

            String rangeString = SpanUtilities.formatType(rangeSystem,
                    distance, Span.METER);

            String northStr = "T";

            if (northRef == NorthReference.MAGNETIC) {
                bearingDisplay = ATAKUtilities.convertFromTrueToMagnetic(
                        point, bearingDisplay);
                northStr = "M";
            } else if (northRef == NorthReference.GRID) {
                bearingDisplay -= ATAKUtilities.computeGridConvergence(
                        point, bearingDisplay, distance);
                northStr = "G";
            }
            bearingDisplay = AngleUtilities.wrapDeg(bearingDisplay);
            String bearingString = AngleUtilities.format(bearingDisplay,
                    bearingUnits) + northStr;

            lineColors[lineNum++] = -1;
            retval.append("\n");
            retval.append(bearingString);
            retval.append("        ");
            retval.append(rangeString);
        }

        if (speed > 0 || teamMate) {

            int speedIndex = 0;

            try {
                speedIndex = Integer.parseInt(_prefs.getString(
                        "speed_unit_pref", String.valueOf(0)));
            } catch (Exception ignore) {

            }

            lineColors[lineNum++] = -1;
            retval.append("\n");

            String speedString = (speed > 0)
                    ? speedFormatter.getSpeedFormatted(speed)
                    : "--- " + speedUnits[speedIndex];

            retval.append(speedString);

            final int len = speedString.length();
            if (len == 5)
                retval.append("      ");
            else if (len == 6)
                retval.append("     ");
            else
                retval.append("    ");

            String orientationString = "---" + Angle.DEGREE_SYMBOL;
            double orientation = heading;
            if (!Double.isNaN(orientation)) {
                String unit = "T";
                if (northRef == NorthReference.MAGNETIC) {
                    orientation = ATAKUtilities.convertFromTrueToMagnetic(
                            point, orientation);
                    unit = "M";
                } else if (northRef == NorthReference.GRID) {
                    orientation -= ATAKUtilities.computeGridConvergence(point,
                            orientation, 1);
                    unit = "G";
                }
                orientation = AngleUtilities.wrapDeg(orientation);
                orientationString = AngleUtilities.format(orientation,
                        bearingUnits) + unit;
                if (orientation < 10)
                    orientationString = "  " + orientationString;
                else if (orientation < 100)
                    orientationString = " " + orientationString;
            }

            retval.append(orientationString);
            if (estimate)
                retval.append(" EST");
        }

        return retval.toString();
    }

    @Override
    public void onTrackChanged(Marker marker) {
        handlePointChange(marker);
    }

    private void handlePointChange(final PointMapItem item) {
        // Self marker changes are handled in the self coord overlay updater
        if (item == null || item == _mapView.getSelfMarker()
                && selfCoordChangedListener != null)
            return;

        updatePointText(item);
    }

    private void updatePointText(@NonNull
    final PointMapItem item) {
        GeoPointMetaData point = item.getGeoPointMetaData();
        if (_activePoint != null)
            point = _activePoint;

        double speed = -1, heading = -1;
        boolean estimate = false;
        boolean teamMate = false;
        double height = item.getHeight();
        double area = Double.NaN;

        MapItem shp = ATAKUtilities.findAssocShape(item);
        if (shp instanceof Shape) {
            area = ((Shape) shp).getArea();
        }

        ElevationDisplayInfo el = new ElevationDisplayInfo();
        if (item instanceof Marker) {
            if (item.getType().equals("self"))
                speed = item.getMetaDouble("Speed", Double.NaN);
            else
                speed = ((Marker) item).getTrackSpeed();

            heading = ((Marker) item).getTrackHeading();

            if (Double.isNaN(speed)) {
                speed = item.getMetaDouble("est.speed", Double.NaN);
                estimate = true;
            }

            if (Double.isNaN(heading)) {
                heading = item.getMetaDouble("est.course", Double.NaN);
                estimate = true;
            }

            teamMate = item.hasMetaValue("team");
            el.doNotDisplayAgl = item.getMetaBoolean(
                    "doNotDisplayAgl",
                    false);

            if (item.getMetaBoolean("extendedElevationDisplay", false)) {
                // pull terrain
                double t = ElevationManager.getElevation(
                        point.get().getLatitude(),
                        point.get().getLongitude(), DTM_FILTER, el.terrain);

                // pull surface
                double s = ElevationManager.getElevation(
                        point.get().getLatitude(),
                        point.get().getLongitude(), DSM_FILTER, el.surface);

            }
        }

        boolean display = item.getMetaBoolean("pairinglineself_on", false);

        String label = item.getMetaString("label",
                ATAKUtilities.getDisplayName(item));
        findDistanceAndBearing(item, point.get());
        _updatePoint(point, label, null,
                item.getMetaDouble("distance", 0.0d),
                item.getMetaDouble("bearing", 0.0d),
                display, speed, heading, estimate, height, area, teamMate, el);
    }

    private void findDistanceAndBearing(PointMapItem spi, GeoPoint point) {
        if (self == null || !self.getPoint().isValid()) {
            return;
        }

        GeoPoint targetLocation = self.getPoint();

        spi.setMetaDouble("distance", targetLocation.distanceTo(point));

        double bearing = targetLocation.bearingTo(point);
        spi.setMetaDouble("bearing", (bearing + 360.0f) % 360.0f); // adjust range to [0,360)
    }

    private void findDistanceAndBearing(PointMapItem spi) {
        findDistanceAndBearing(spi, spi.getPoint());
    }

    /**
     * This is the uber formatting function from ATAK 2.0+
     * @param point the point to use
     * @param label the label
     * @param sublabel a sublabel if it exists
     * @param distance the distance to the red X
     * @param bearing the bearing to the red X
     * @param displayRB true if the distance and bearing are to be shown
     * @param speed the speed
     * @param heading heading
     * @param estimate if the speed and heading are estimated
     * @param height the height of the item
     * @param area the area
     * @param teamMate if the item is a team mate
     * @param el the elevation model for the case where a surface model is shown.
     */
    protected void _updatePoint(final GeoPointMetaData point,
            final String label,
            final String sublabel, final double distance, final double bearing,
            boolean displayRB, final double speed, final double heading,
            final boolean estimate, final double height, final double area,
            final boolean teamMate,
            final ElevationDisplayInfo el) {

        // either the user has a range and bearing arrow displayed or the label is a Red X or a Local SPI
        // in these three cases, display the range and bearing.
        displayRB = displayRB || label.equals("Red X")
                || label.startsWith("Local SPI");

        int[] lineColors = new int[MAX_LINE_COUNT];
        Arrays.fill(lineColors, -1);
        String coordText = constructCoordText(label, sublabel, point, distance,
                bearing, displayRB,
                speed, heading, estimate,
                height, area,
                teamMate, el, lineColors);

        if (_marker && !label.equals("Red X") && !label.startsWith("Local SPI"))
            _savedText = _positionText.getText();

        _positionText.setText(coordText);
        _positionText.setColors(lineColors);
        boolean disable = _pointItem != null
                && _pointItem.getMetaBoolean("disableCoordinateOverlay", false);
        _positionText.setVisible(!disable);
    }

    private void _setPointMapItem(PointMapItem point) {
        if (_pointItem != null) {
            _pointItem.removeOnPointChangedListener(this);
            if (_pointItem instanceof Marker) {
                ((Marker) _pointItem)
                        .removeOnTrackChangedListener(this);
            }
        }
        _pointItem = point;
        if (point != null) {
            point.addOnPointChangedListener(this);
            if (_pointItem instanceof Marker) {
                ((Marker) _pointItem)
                        .addOnTrackChangedListener(this);
            }
        }
    }

    private void setActiveMarker(Marker marker) {
        if (marker != _activeMarker) {
            _activeMarker = marker;
            _activePoint = marker != null ? marker.getGeoPointMetaData() : null;
        }
    }

    protected static class ElevationDisplayInfo {
        public boolean doNotDisplayAgl;
        final public GeoPointMetaData terrain = new GeoPointMetaData();
        final public GeoPointMetaData surface = new GeoPointMetaData();

        ElevationDisplayInfo() {
            doNotDisplayAgl = false;
        }
    }

    private String getCoords(GeoPoint point) {
        // Check to see if the point item has an override for the desired
        // coordinate display format; if it does use it, if not use the current
        // display preference setting
        String coordFmt = _prefs.getString(
                "coord_display_pref", _mapView.getContext().getString(
                        R.string.coord_display_pref_default));
        if (_pointItem != null) {
            coordFmt = _pointItem.getMetaString("coordFormat", coordFmt);
        }
        String curCoordString = CoordinateFormatUtilities.formatToString(
                point, CoordinateFormat.find(coordFmt));

        return curCoordString;
    }

    protected final BroadcastReceiver coordReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case COORD_COPY:
                    ATAKUtilities.copyClipboard("coordinates", coordString,
                            true);
                    _mapView.getRootGroup().removeItem(radialFocus);
                    mw.clearMenu();
                    break;
                case COORD_ENLARGE:
                    boolean b = _prefs.getBoolean("detail.enlarged", false);
                    // toggle the variable
                    _prefs.edit().putBoolean("detail.enlarged", !b).apply();
                    b = !b;
                    MapTextFormat _format = MapView.getTextFormat(
                            Typeface.DEFAULT,
                            b ? 2 : 7);
                    _positionText.setTextFormat(_format);
                    mw.clearMenu();
                    break;

                default:
                    break;
            }
        }
    };

}
