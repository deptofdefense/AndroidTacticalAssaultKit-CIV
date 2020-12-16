
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
import com.atakmap.map.elevation.ElevationData;
import com.atakmap.map.elevation.ElevationManager;

import java.util.Arrays;

/**
 * TODO: Outline all expected behavior and rewrite this entire class
 */
public class CoordOverlayMapReceiver extends BroadcastReceiver implements
        OnPointChangedListener, MapGroup.OnItemListChangedListener,
        SharedPreferences.OnSharedPreferenceChangeListener,
        Marker.OnTrackChangedListener, MapWidget.OnClickListener {

    MenuLayoutWidget mw = MapMenuReceiver.getMenuWidget();

    private final static int MAX_LINE_COUNT = 11;

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

    private SpeedFormatter speedFormatter;
    private String[] speedUnits;

    private String coordString;
    private Marker radialFocus;

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
        trLayout.addWidgetAt(0, _positionText);

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

                PointF p = widget.getAbsolutePosition();
                int height = mapView.getActionBarHeight();
                if (height == 0)
                    height = (int) (25 * MapView.DENSITY);
                p.x += widget.getPointX() + (25 * MapView.DENSITY);
                p.y += widget.getPointY() + height;

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

    }

    void dispose() {
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
            _mapView.getMapController().panTo(point, false);
        }
    }

    @Override
    public void onSharedPreferenceChanged(
            SharedPreferences sharedPreferences, String key) {
        if (key.equals("rab_rng_units_pref")
                || key.equals("rab_brg_units_pref") ||
                key.equals("alt_unit_pref")
                || key.equals("alt_display_pref")
                || key.equals("rab_north_ref_pref")) {
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
            ElevationDisplayInfo el = new ElevationDisplayInfo();
            if (intent.hasExtra("uid")) {
                String pointUID = intent.getStringExtra("uid");
                if (pointUID != null) {
                    MapItem item = _mapView.getMapItem(pointUID);
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
                    } else if (item instanceof Shape) {
                        point = GeoPointMetaData
                                .wrap(((Shape) item).findTouchPoint());
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
                                    point.get().getLatitude(),
                                    point.get().getLongitude(), DTM_FILTER,
                                    el.terrain);

                            // pull surface
                            double s = ElevationManager.getElevation(
                                    point.get().getLatitude(),
                                    point.get().getLongitude(), DSM_FILTER,
                                    el.surface);

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
                            speed, heading, estimate, teamMate, el);
                } else {
                    _updatePoint(point, title, speed, heading, estimate,
                            teamMate,
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
                    speed, heading,
                    false,
                    _activeMarker.hasMetaValue("team"),
                    el);
        }
    }

    protected String constructCoordText(final String label,
            final String sublabel,
            final GeoPointMetaData pointWithMetadata, final double distance,
            final double bearing,
            final boolean displayRB, final double speed, final double heading,
            final boolean estimate, final boolean teamMate,
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
                Span altUnits;
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
            Span altUnits;
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

        // XXX: if the distance is 0, you are on top of the item.
        // no real reason to provide a a range and bearing.
        if (displayRB && distance > 0) {

            double bearingDisplay = bearing;
            while (bearingDisplay < 0)
                bearingDisplay += 360.0d;

            String rangeString = SpanUtilities.formatType(rangeSystem,
                    distance, Span.METER);

            String bearingString;

            if (northRef == NorthReference.TRUE) {
                bearingString = AngleUtilities.format(bearingDisplay,
                        bearingUnits) + "T";
            } else {
                double bearingM = ATAKUtilities.convertFromTrueToMagnetic(
                        point, bearingDisplay);
                bearingString = AngleUtilities.format(bearingM, bearingUnits)
                        + "M";
            }

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
                if (northRef != NorthReference.TRUE) {
                    orientation = ATAKUtilities.convertFromTrueToMagnetic(
                            point, orientation);
                    unit = "M";
                }
                orientationString = AngleUtilities.format(orientation)
                        + unit;
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

    private void handlePointChange(PointMapItem item) {
        if (item == null)
            return;

        GeoPointMetaData point = item.getGeoPointMetaData();
        if (_activePoint != null)
            point = _activePoint;

        double speed = -1, heading = -1;
        boolean estimate = false;
        boolean teamMate = false;
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
                display, speed, heading, estimate, teamMate, el);

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

    private void _updatePoint(GeoPointMetaData point, final String label,
            final double speed,
            final double heading, boolean estimate, final boolean teamMate,
            ElevationDisplayInfo el) {
        _updatePoint(point, label, null, -1, 0, false, speed, heading, estimate,
                teamMate, el);
    }

    protected void _updatePoint(final GeoPointMetaData point,
            final String label,
            final String sublabel, final double distance, final double bearing,
            boolean displayRB, final double speed, final double heading,
            final boolean estimate, final boolean teamMate,
            final ElevationDisplayInfo el) {

        // either the user has a range and bearing arrow displayed or the label is a Red X or a Local SPI
        // in these three cases, display the range and bearing.
        displayRB = displayRB || label.equals("Red X")
                || label.startsWith("Local SPI");

        int[] lineColors = new int[MAX_LINE_COUNT];
        Arrays.fill(lineColors, -1);
        String coordText = constructCoordText(label, sublabel, point, distance,
                bearing, displayRB,
                speed, heading, estimate, teamMate, el,
                lineColors);

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
