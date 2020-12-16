
package com.atakmap.android.mapcompass;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.data.URIQueryParameters;
import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.MotionEvent;

import com.atakmap.android.gui.HintDialogHelper;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapMode;
import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.menu.MapMenuEventListener;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.menu.MapMenuWidget;
import com.atakmap.android.menu.MenuLayoutWidget;
import com.atakmap.android.model.ModelImporter;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.widgets.AbstractParentWidget;
import com.atakmap.android.widgets.DrawableWidget;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.MapFocusTextWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MapWidget.OnClickListener;
import com.atakmap.android.widgets.MapWidget.OnLongPressListener;
import com.atakmap.android.widgets.MapWidget2;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.android.widgets.ScaleWidget;
import com.atakmap.android.widgets.CenterBeadWidget;
import com.atakmap.android.widgets.TextWidget;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.AtakMapView;
import com.atakmap.android.overlay.Overlay;
import com.atakmap.android.overlay.OverlayManager;
import com.atakmap.android.overlay.OverlayManager.OnServiceListener;
import com.atakmap.android.overlay.Overlay.OnVisibleChangedListener;

import com.atakmap.map.projection.ECEFProjection;
import com.atakmap.map.projection.EquirectangularMapProjection;
import com.atakmap.map.projection.Projection;

import java.util.List;

/**
 * Map component that shows the direction of north using an arrow. 
 * Allows toggle of track up / north up modes by clicking.
 */
public class CompassArrowMapComponent extends AbstractMapComponent implements
        Marker.OnTrackChangedListener, OnClickListener,
        OnSharedPreferenceChangeListener, OnLongPressListener,
        OnServiceListener, AtakMapView.OnMapMovedListener,
        MapWidget.OnWidgetPointChangedListener, OnVisibleChangedListener,
        MapView.OnMapProjectionChangedListener,
        AbstractParentWidget.OnWidgetListChangedListener,
        MapMenuEventListener {

    public static final String TAG = "CompassArrowMapComponent";

    public static final String HIDE_ACTION = "com.atakmap.android.mapcompass.HIDE";
    public static final String SHOW_ACTION = "com.atakmap.android.mapcompass.SHOW";
    public static final String TOGGLE_3D = "com.atakmap.android.maps.TOGGLE_3D";
    public static final String LOCK_TILT = "com.atakmap.android.maps.LOCK_TILT";
    public static final String FREE_LOOK = "com.atakmap.android.maps.TOGGLE_FREE_LOOK";

    protected MapView mapView;
    protected MapTouchController mapTouch;
    protected Context context;

    protected MapMode mapMode = MapMode.NORTH_UP;

    // Compass (top-left)
    protected LinearLayoutWidget compassLayout;
    protected CompassArrowWidget compass;
    protected TextWidget compassText;
    protected LinearLayoutWidget freeLookWidget;

    // Scale bar
    protected LinearLayoutWidget tlLayout, brLayout;
    protected ScaleWidget scale;

    // Center cross-hair
    protected CenterBeadWidget cb;
    protected MapFocusTextWidget cbText;
    protected CoordinateFormat coordMode = CoordinateFormat.MGRS;

    private float lastHeading = 0;
    private double lastTilt = 0;

    protected double lockedHeadingValue = Float.NaN;
    protected boolean lockedHeading = false;

    protected Marker selfItem = null;
    protected Marker radialFocus;

    private boolean isVisible = false;
    protected SharedPreferences _prefs;
    private Overlay cbOverlay;

    protected TiltLockWidgetController tlwc;

    private static CompassArrowMapComponent _instance;

    public CompassArrowMapComponent() {
        _instance = this;
    }

    /**
     * Obtain the current instance of the CompassArrowMapComponent.
     */
    public static CompassArrowMapComponent getInstance() {
        return _instance;
    }

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {

        this.mapView = view;
        this.mapTouch = view.getMapTouchController();
        this.context = context;

        Resources res = context.getResources();

        RootLayoutWidget root = (RootLayoutWidget) view.getComponentExtra(
                "rootLayoutWidget");
        LinearLayoutWidget blLayout = root
                .getLayout(RootLayoutWidget.BOTTOM_LEFT);
        this.tlLayout = root.getLayout(RootLayoutWidget.TOP_LEFT);
        this.tlLayout.addOnWidgetPointChangedListener(this);
        this.brLayout = root.getLayout(RootLayoutWidget.BOTTOM_RIGHT);
        this.brLayout.addOnWidgetPointChangedListener(this);

        LinearLayoutWidget layoutH = this.tlLayout.getOrCreateLayout("TL_H");

        LinearLayoutWidget layoutV = new LinearLayoutWidget();
        layoutV.setOrientation(LinearLayoutWidget.VERTICAL);
        layoutH.addWidgetAt(0, layoutV);

        this.compassLayout = new LinearLayoutWidget();
        this.compassLayout.setName("Compass Layout");
        this.compassLayout.setPadding(16f);
        this.compassLayout.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        this.compassLayout.setVisible(false);
        layoutV.addWidgetAt(0, this.compassLayout);

        _prefs = PreferenceManager.getDefaultSharedPreferences(context);
        _prefs.registerOnSharedPreferenceChangeListener(this);

        // Compass
        this.compass = new CompassArrowWidget(this.mapView);
        this.compass.addOnClickListener(this);
        this.compass.addOnLongPressListener(this);
        this.compass.addOnWidgetPointChangedListener(this);
        this.compassLayout.addWidget(this.compass);
        this.compassText = new TextWidget("", MapView.getTextFormat(
                Typeface.DEFAULT_BOLD, -1));
        this.compassText.setName("Compass Text");
        this.compassText.setVisible(false);
        this.compassText.setMargins(0f, 8f, 0f, 0f);
        this.compassLayout.addWidget(this.compassText);

        // Scale bar
        final MapTextFormat mt = MapView.getTextFormat(Typeface.DEFAULT_BOLD,
                -2);
        this.scale = new ScaleWidget(view, mt);
        this.scale.setName("Map Scale");
        this.scale.setPadding(8f, 4f, 8f, 4f);
        this.scale.setMargins(16f, 0f, 16f, 0f);

        boolean scale_vis = _prefs.getBoolean("map_scale_visible", true);
        boolean scale_rounding = _prefs.getBoolean("map_scale_rounding", false);
        this.scale.setVisible(scale_vis);
        this.scale.setRounding(scale_rounding);
        try {
            String s = _prefs.getString(
                    "rab_rng_units_pref",
                    String.valueOf(Span.METRIC));
            if (s != null) {
                int _rangeUnits = Integer.parseInt(s);
                this.scale.setRangeUnits(_rangeUnits);
            }
        } catch (Exception e) {
            Log.d(TAG, "number format exception occurred", e);
        }
        blLayout.addWidgetAt(0, this.scale);

        // Center cross-hair
        boolean cbVisible = _prefs.getBoolean("map_center_designator", false);
        this.cb = new CenterBeadWidget();
        this.cb.setVisible(cbVisible);
        root.addWidget(this.cb);

        tlwc = new TiltLockWidgetController(mapView, compass);
        tlwc.setVisible(false);

        // Free look widget
        float size = res.getDimensionPixelSize(R.dimen.button_small);
        float pd = res.getDimensionPixelSize(R.dimen.auto_margin);
        int bgColor = res.getColor(R.color.actionbar_background_empty);
        this.freeLookWidget = new LinearLayoutWidget();
        this.freeLookWidget.setNinePatchBG(true);
        this.freeLookWidget.setBackingColor(bgColor);
        this.freeLookWidget.setPadding(pd, pd, pd, pd);
        this.freeLookWidget.setVisible(false);
        DrawableWidget dr = new DrawableWidget(res.getDrawable(
                R.drawable.ic_password_show));
        dr.setSize(size, size);
        dr.setColor(Color.rgb(99, 198, 62));
        dr.addOnClickListener(this);
        this.freeLookWidget.addWidget(dr);
        layoutV.addWidget(this.freeLookWidget);

        // Center coordinate text (corresponds with center cross-hair)
        this.coordMode = CoordinateFormat.find(_prefs.getString(
                "coord_display_pref", res.getString(
                        R.string.coord_display_pref_default)));
        this.cbText = new MapFocusTextWidget();
        this.cbText.setMargins(16f, 0f, 0f, 16f);
        this.cbText.setVisible(cbVisible);
        blLayout.addWidgetAt(1, this.cbText);

        // Add listener for changes to track up / north up map mode
        DocumentedIntentFilter intentFilter = new DocumentedIntentFilter();
        intentFilter.addAction(HIDE_ACTION);
        intentFilter.addAction(SHOW_ACTION);
        intentFilter.addAction(TOGGLE_3D);
        intentFilter.addAction(LOCK_TILT);
        intentFilter.addAction(FREE_LOOK);
        for (MapMode mode : MapMode.values())
            intentFilter.addAction(mode.getIntent());
        AtakBroadcast.getInstance().registerReceiver(orientationReceiver,
                intentFilter);

        synchronized (this) {
            selfItem = ATAKUtilities.findSelfUnplaced(mapView);

            // Add listener for changes to track heading
            if (selfItem != null)
                selfItem.addOnTrackChangedListener(this);
        }

        mapView.getMapController().tiltTo(0, true);

        // Add arrow compass to the map
        toggleWidget(true);

        Intent sharedOverlayServiceIntent = new Intent();
        sharedOverlayServiceIntent
                .setAction("com.atakmap.android.overlay.SHARED");

        if (!OverlayManager.aquireService(context, sharedOverlayServiceIntent,
                this)) {

            // try again but embed locally
            OverlayManager
                    .aquireService(context, null, this);
        }
        mapView.addOnMapMovedListener(CompassArrowMapComponent.this);
        onMapMoved(mapView, false);

        MapMode mm = MapMode.NORTH_UP;
        double direction = Double.NaN;

        final String status_mapmode_heading = _prefs
                .getString("status_mapmode_heading_value", null);

        if (status_mapmode_heading != null) {
            try {
                lockedHeadingValue = Double.parseDouble(status_mapmode_heading);
            } catch (Exception e) {
                Log.e(TAG, "error restoring status_mapmode_heading");
            }
        }

        lockedHeading = _prefs.getBoolean("status_mapmode_heading_locked",
                false);

        final String status_mapmode = _prefs.getString("status_mapmode", null);

        if (status_mapmode != null) {
            try {
                mm = MapMode.findFromValue(Integer.parseInt(status_mapmode));
            } catch (Exception e) {
                Log.e(TAG, "error restoring status_mapmode");
            }
        }

        setMapMode(mm);

        // fix up the unlocked map
        if (mm == MapMode.USER_DEFINED_UP) {
            if (!Double.isNaN(lockedHeadingValue)) {
                mapView.getMapController().rotateTo(lockedHeadingValue,
                        true);
            }
            if (!Double.isNaN(lockedHeadingValue)) {
                updateDegDisplay(lockedHeadingValue);
            }
        }

        // set the current state of globe vs flat
        onSharedPreferenceChanged(_prefs, "atakGlobeModeEnabled");
        mapView.addOnMapProjectionChangedListener(this);

        // need to notify the rest of the system components
        // so they are not out of sync.
        Intent sync = new Intent(mm.getIntent());
        AtakBroadcast.getInstance().sendBroadcast(sync);

        // For free-look radial action on regular map items
        MapMenuReceiver.getInstance().addEventListener(this);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {

        // Restore tilt to zero if we were in free look mode w/out 3D mode
        // explicitly toggled on
        if (mapTouch.isFreeForm3DEnabled()
                && !_prefs.getBoolean("status_3d_enabled", false))
            view.getMapController().tiltTo(0, false);

        // record the heading value at the time the app is being shut down.
        _prefs.edit().putString("status_mapmode_heading_value",
                Double.toString(mapView.getMapRotation())).apply();

        mapView.removeOnMapMovedListener(CompassArrowMapComponent.this);
        AtakBroadcast.getInstance().unregisterReceiver(orientationReceiver);
        view.removeOnMapMovedListener(this);
        brLayout.removeOnWidgetPointChangedListener(this);
        compass.removeOnWidgetPointChangedListener(this);
        MapMenuReceiver.getInstance().removeEventListener(this);
    }

    protected final BroadcastReceiver orientationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            String action = intent.getAction();
            if (FileSystemUtils.isEmpty(action))
                return;

            boolean toggle = FileSystemUtils.isEquals(
                    intent.getStringExtra("toggle"), "true");

            // Hide compass widget
            switch (action) {
                case HIDE_ACTION:
                    toggleWidget(false);
                    break;

                // Show compass widget
                case SHOW_ACTION:
                    toggleWidget(true);
                    break;

                // Toggle 3D tilt widget
                case TOGGLE_3D: {
                    double tilt;
                    GeoPoint focus = mapView.inverseWithElevation(
                            mapView.getMapController().getFocusX(),
                            mapView.getMapController().getFocusY()).get();
                    if (tlwc == null || !tlwc.isVisible()) {
                        tilt = lastTilt;
                        enable3DControls(true);
                    } else {
                        lastTilt = mapView.getMapTilt();
                        tilt = -lastTilt;
                        enable3DControls(false);
                    }
                    mapView.getMapController().tiltBy(tilt, focus, true);
                    break;
                }

                // Lock/toggle 3D tilt interactions
                case LOCK_TILT:
                    _prefs.edit()
                            .putBoolean("status_tilt_enabled",
                                    !_prefs.getBoolean("status_tilt_enabled",
                                            true))
                            .apply();
                    if (tlwc != null) {
                        // now that the preference has been set, now determine the lock state
                        tlwc.setLocked(
                                !_prefs.getBoolean("status_tilt_enabled",
                                        true));
                    }
                    break;

                case FREE_LOOK: {
                    boolean freeLook = !mapTouch.isFreeForm3DEnabled();
                    mapTouch.setFreeForm3DEnabled(freeLook);
                    freeLookWidget.setVisible(freeLook);
                    updateCompassWidget();

                    // Set proper focus point on item
                    String uid = intent.getStringExtra("uid");
                    if (freeLook && !FileSystemUtils.isEmpty(uid)) {
                        MapItem mi = mapView.getMapItem(uid);
                        mapTouch.setFreeForm3DItem(mi);
                    }

                    break;
                }

                // Set/toggle map rotation mode
                default:
                    MapMode mode = MapMode.findFromIntent(intent.getAction());
                    if (toggle) {
                        if (mode == MapMode.USER_DEFINED_UP
                                && mapMode == MapMode.USER_DEFINED_UP)
                            mode = MapMode.NORTH_UP;
                    }
                    setMapMode(mode);
                    break;
            }

            // record the desired state of the human toggling the compass

            _prefs.edit().putString("status_mapmode_heading_value",
                    Double.toString(lockedHeadingValue)).apply();

            _prefs.edit().putBoolean("status_mapmode_heading_locked",
                    lockedHeading).apply();

            _prefs.edit().putString("status_mapmode",
                    Integer.toString(mapMode.getValue())).apply();

        }
    };

    protected Runnable gpsLockCallback = null;

    /** 
     * Set the gps lock symbol if the callback r is not null.
     * currently only meched out to allow for the location to receive this dismissal but 
     * in the future attempt to do something else.
     * @param r The behavior to invoke when the GPS lock is dimissed.
     * 
     */
    synchronized public void setGPSLockAction(final Runnable r) {
        gpsLockCallback = r;
        if (r != null && mapMode == MapMode.TRACK_UP) {
            // show the lock button
            compass.setMode(MapMode.USER_DEFINED_UP, true);
            compassText.setText("GPS");
        } else {
            compass.setMode(mapMode, lockedHeading);
        }
        updateCompassWidget();
    }

    /**
     * Get the current map mode.
     */
    synchronized public MapMode getMapMode() {
        return mapMode;
    }

    synchronized public void setMapMode(final MapMode action) {
        mapView.getMapTouchController().setUserOrientation(false);

        if (action == MapMode.NORTH_UP) {
            mapMode = MapMode.NORTH_UP;
            compass.setRotation(0f);
            mapView.getMapController().rotateTo(0, true);
        } else if (action == MapMode.TRACK_UP) {
            mapMode = MapMode.TRACK_UP;

            // Set initial compass heading
            if (selfItem != null)
                onTrackChanged(selfItem);

        } else if (action == MapMode.MAGNETIC_UP) {
            mapMode = MapMode.MAGNETIC_UP;

            // Set initial compass heading
            if (selfItem != null)
                onTrackChanged(selfItem);
        } else if (action == MapMode.USER_DEFINED_UP) {
            mapMode = MapMode.USER_DEFINED_UP;
            if (!lockedHeading) {
                mapView.getMapTouchController()
                        .setUserOrientation(true);
            } else if (!Double.isNaN(lockedHeadingValue)) {
                mapView.getMapController().rotateTo(lockedHeadingValue,
                        true);
            }
            updateDegDisplay(!Double.isNaN(lockedHeadingValue)
                    ? lockedHeadingValue
                    : 0.0);
            onMapMoved(mapView, false);
        }
        compass.setMode(mapMode, lockedHeading);
        if (tlwc != null)
            tlwc.refresh();
        updateCompassWidget();
    }

    @Override
    public void onMapProjectionChanged(AtakMapView view) {

        // possibly not needed but coded just in case a plugin modifies the 
        // projection without using the preference screen.

        final Projection proj = view.getProjection();
        boolean globeModeEnabled = _prefs.getBoolean("atakGlobeModeEnabled",
                true);

        if (proj.getSpatialReferenceID() == ECEFProjection.INSTANCE
                .getSpatialReferenceID()) {
            if (!globeModeEnabled) {
                Log.d(TAG,
                        "ECEFProjection mode enabled outside of the preference screen");
                _prefs.edit().putBoolean("atakGlobeModeEnabled", true).apply();
            }
        } else {
            if (globeModeEnabled) {
                Log.d(TAG,
                        "EquirectangularMapProjection mode enabled outside of the preference screen");
                _prefs.edit().putBoolean("atakGlobeModeEnabled", false).apply();
            }

        }
    }

    @Override
    public void onMapMoved(AtakMapView view, boolean animate) {
        // Update compass degree display
        boolean freeForm3D = mapTouch.isFreeForm3DEnabled();
        double rot = view.getMapRotation();
        if (mapMode == MapMode.USER_DEFINED_UP || freeForm3D)
            updateDegDisplay(rot);
        compass.setRotation((float) rot);

        // Toggle off free look if map moved
        if (freeLookWidget != null
                && freeLookWidget.isVisible() != freeForm3D) {
            freeLookWidget.setVisible(freeForm3D);
            updateCompassWidget();
        }
    }

    protected void updateDegDisplay(double deg) {
        compass.setRotation((float) deg);
        String degString = AngleUtilities.format(deg);
        int charCount = degString.length();
        //forgo using StringFormatter to improve efficiency since this needs to be updated
        //fairly quickly if the user is actively rotating the map
        if (charCount <= 2)
            compassText.setText("00" + degString);
        else if (charCount <= 3)
            compassText.setText("0" + degString);
        else
            compassText.setText(degString);
    }

    @Override
    public void onTrackChanged(Marker marker) {

        float heading = (float) marker.getTrackHeading();

        if (Float.isNaN(heading))
            heading = (float) mapView.getMapData().getDouble("deviceAzimuth",
                    0d);

        if (Float.isNaN(heading))
            heading = 0f;

        // This shouldn't happen, but it occasionally does
        if (heading < 0f || heading > 360f)
            heading = 0f;

        float dHeading = heading - lastHeading;

        // Account for rotating through zero degrees
        if (dHeading > 180f)
            dHeading -= 360f;
        else if (dHeading < -180f)
            dHeading += 360f;

        // Smooth out rotation
        lastHeading += dHeading / 5f;

        if (lastHeading > 360f)
            lastHeading -= 360f;
        if (lastHeading < 0)
            lastHeading += 360f;

        if (mapMode == MapMode.TRACK_UP || mapMode == MapMode.MAGNETIC_UP)
            compass.setRotation(lastHeading);
    }

    @Override
    public void onMapWidgetClick(final MapWidget widget,
            final MotionEvent event) {

        // Toggle off free look
        if (widget.getParent() == freeLookWidget) {
            freeLookWidget.setVisible(false);
            mapTouch.setFreeForm3DEnabled(false);
            updateCompassWidget();
            return;
        }

        synchronized (this) {
            // tapped to unlock the GPS lock
            if (gpsLockCallback != null) {
                gpsLockCallback.run();
                gpsLockCallback = null;
                compass.setMode(mapMode, lockedHeading);
                updateCompassWidget();
                return;
            }
        }

        toggleMapMode();

        HintDialogHelper.showHint(context,
                context.getString(R.string.compass_tip),
                context.getString(R.string.compass_tip2),
                "compass.hint.1");

        synchronized (this) {
            if (selfItem == null) {
                selfItem = ATAKUtilities.findSelfUnplaced(mapView);
                if (selfItem != null)
                    selfItem.addOnTrackChangedListener(this);
            }
        }
    }

    @Override
    public void onMapWidgetLongPress(MapWidget widget) {
        MenuLayoutWidget mw = MapMenuReceiver.getMenuWidget();
        if (mw == null)
            return;
        if (radialFocus == null) {
            radialFocus = new Marker("CompassWidgetFocus");
            radialFocus.setMetaBoolean("addToObjList", false);
            radialFocus.setMetaBoolean("nevercot", true);
            radialFocus.setVisible(false);
            mw.addOnWidgetListChangedListener(this);
        }
        if (radialFocus.getGroup() == null)
            mapView.getRootGroup().addItem(radialFocus);

        // Enable the free-look button when we're zoomed in enough on a 3D model
        boolean freeLook = mapTouch.isFreeForm3DEnabled();
        double resLevel = (Math.log(156543.034 * Math.cos(0d)
                / mapView.getMapResolution()) / Math.log(2));
        if (!freeLook && resLevel >= 15.5) {
            // Check if there are any models in view
            URIQueryParameters params = new URIQueryParameters();
            params.contentType = ModelImporter.CONTENT_TYPE;
            params.fov = new FOVFilter(mapView);
            List<URIContentHandler> handlers = URIContentManager.getInstance()
                    .query(params);
            freeLook = !handlers.isEmpty();
        }
        radialFocus.setMetaString("menu", freeLook
                ? "menus/compass_widget_model_menu.xml"
                : "menus/compass_widget_menu.xml");

        PointF p = compass.getAbsolutePosition();
        p.x += compass.getWidth() / 2;
        p.y += compass.getHeight() / 2;

        radialFocus.toggleMetaData("userUp",
                mapMode == MapMode.USER_DEFINED_UP);
        radialFocus.toggleMetaData("enable3D",
                tlwc != null && tlwc.isVisible());
        radialFocus.toggleMetaData("lockTilt",
                !_prefs.getBoolean("status_tilt_enabled", true));
        radialFocus.toggleMetaData("freeLook", mapTouch.isFreeForm3DEnabled());

        if (mw.getMapItem() != radialFocus) {
            mw.openMenuOnItem(radialFocus);
            mapView.getMapController().removeOnFocusPointChangedListener(mw);
            mw.onFocusPointChanged(p.x, p.y);
            float padding = 32f + (112f * MapView.DENSITY);
            compassLayout.setPadding(16f, 16f, padding, padding);
            HintDialogHelper.showHint(context,
                    context.getString(R.string.compass_tip),
                    context.getString(R.string.compass_tip3),
                    "compass.hint.2");
        } else
            mw.clearMenu();
    }

    @Override
    public void onWidgetAdded(AbstractParentWidget parent, int index,
            MapWidget child) {
    }

    @Override
    public void onWidgetRemoved(AbstractParentWidget parent, int index,
            MapWidget child) {
        if (child instanceof MapMenuWidget)
            compassLayout.setPadding(16f);
    }

    @Override
    public void onSharedPreferenceChanged(
            final SharedPreferences prefs, final String key) {
        switch (key) {
            case "map_scale_visible":
                if (scale != null)
                    scale.setVisible(prefs.getBoolean(key, true) && isVisible);
                break;
            case "map_center_designator":
                boolean visible = prefs.getBoolean(key, true);
                if (cb != null) {
                    cb.setVisible(visible);
                    cbText.setVisible(visible);
                }
                cbOverlay.setVisible(visible);
                onMapMoved(mapView, false);
                break;
            case "map_scale_rounding":
                if (scale != null)
                    scale.setRounding(prefs.getBoolean(key, false));
                break;
            case "rab_rng_units_pref":
                if (scale != null) {
                    try {
                        String s = prefs.getString(key,
                                String.valueOf(Span.METRIC));
                        if (s != null) {
                            scale.setRangeUnits(
                                    Integer.parseInt(s));
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "number format exception occurred", e);
                    }
                }
                break;
            case "coord_display_pref":
                this.coordMode = CoordinateFormat.find(prefs.getString(key,
                        context.getString(
                                R.string.coord_display_pref_default)));
                onMapMoved(mapView, false);
                break;
            case "dexControls":
                if (this.tlwc != null)
                    this.tlwc.refresh();
                break;
            case "atakGlobeModeEnabled":
                if (prefs.getBoolean(key, true)) {
                    mapView.setProjection(ECEFProjection.INSTANCE);
                } else {
                    mapView.setProjection(
                            EquirectangularMapProjection.INSTANCE);
                }
                break;
        }
    }

    @Override
    public void onWidgetPointChanged(MapWidget widget) {
        if (widget == brLayout && this.scale != null) {
            // Prevent scale bar from overlapping bottom-right corner layout
            float[] m = this.scale.getMargins();
            this.scale.setMaxWidth(widget.getPointX() - this.scale
                    .getAbsolutePosition().x - m[MapWidget2.RIGHT]);
        } else if (widget == tlLayout) {
            // Keep compass radial around widget
            MenuLayoutWidget mw = MapMenuReceiver.getMenuWidget();
            if (mw != null && radialFocus != null
                    && mw.getMapItem() == radialFocus) {
                PointF p = compass.getAbsolutePosition();
                mw.onFocusPointChanged(p.x + compass.getWidth() / 2,
                        p.y + compass.getHeight() / 2);
            }
        }
    }

    /**
     * Enable Experimental 3-D controls for ATAK
     */
    synchronized public void enable3DControls(boolean enable) {
        if (enable) {
            tlwc.setVisible(true);
            tlwc.setLocked(!_prefs.getBoolean("status_tilt_enabled", true));
        } else {
            if (tlwc != null) {
                tlwc.setVisible(false);
                tlwc.setLocked(true);
            }
        }
        _prefs.edit().putBoolean("status_3d_enabled", enable).apply();
    }

    /**
     * Toggle the rotate/tilt slider widget
     * This will only work if the matching preference is true
     * @param enable True to enable
     */
    public void enableSlider(boolean enable) {
        if (tlwc != null)
            tlwc.setSliderVisible(enable);
    }

    @Override
    public void onOverlayManagerBind(OverlayManager manager) {
        cbOverlay = manager.registerOverlay("Center Designator");
        cbOverlay.setVisible(cb.isVisible());
        cbOverlay.setIconUri("android.resource://"
                + context.getPackageName() + "/"
                + R.drawable.ic_center_designator);

        cbOverlay.addOnVisibleChangedListener(this);
    }

    @Override
    public void onOverlayManagerUnbind(OverlayManager manager) {
        cbOverlay.removeOnVisibleChangedListener(this);
        cbOverlay = null;
    }

    @Override
    public void onOverlayVisibleChanged(Overlay overlay) {
        final boolean current = _prefs.getBoolean("map_center_designator",
                false);
        if (current != overlay.getVisible()) {
            _prefs.edit()
                    .putBoolean("map_center_designator",
                            overlay.getVisible())
                    .apply();
        }
    }

    protected synchronized void toggleWidget(boolean show) {
        if (show == isVisible)
            return;
        if (this.scale != null)
            this.scale.setVisible(
                    show && _prefs.getBoolean("map_scale_visible", true));
        if (this.compassLayout != null)
            this.compassLayout.setVisible(show);
        enable3DControls(show && _prefs.getBoolean("status_3d_enabled", false));
        isVisible = show;
    }

    // Toggles between the available map modes based on the current map mode
    protected void toggleMapMode() {
        Intent intent = new Intent();

        // XXX - This is where user defined up will go.
        // compass.setMode(MapMode.USER_DEFINED_UP)

        if (mapMode == MapMode.NORTH_UP) {
            intent.setAction(MapMode.TRACK_UP.getIntent());
        } else if (mapMode == MapMode.TRACK_UP) {
            intent.setAction(MapMode.NORTH_UP.getIntent());
        } else if (mapMode == MapMode.USER_DEFINED_UP) {
            Intent intentUser = new Intent();
            intentUser.setAction(MapMode.USER_DEFINED_UP.getIntent());
            synchronized (this) {
                lockedHeading = !lockedHeading;
                lockedHeadingValue = mapView.getMapRotation();
                intentUser.putExtra("lockedHeading", lockedHeading);
            }
            AtakBroadcast.getInstance().sendBroadcast(intentUser);
        } else if (mapMode == MapMode.MAGNETIC_UP) {
            intent.setAction(MapMode.NORTH_UP.getIntent());
        } else {
            intent.setAction(MapMode.TRACK_UP.getIntent());
        }

        AtakBroadcast.getInstance().sendBroadcast(intent);

    }

    protected void showCompassText(boolean show) {
        compassText.setVisible(show);
        updateDegDisplay(mapView.getMapRotation());
    }

    private void updateCompassWidget() {
        showCompassText(mapMode == MapMode.USER_DEFINED_UP
                || mapTouch.isFreeForm3DEnabled());
        if (tlwc != null)
            tlwc.refresh();
    }

    @Override
    public boolean onShowMenu(MapItem item) {
        if (item != null) {
            // Highlight the free-look radial action when active, if there is one
            boolean freeLook = mapTouch.isFreeForm3DEnabled();
            item.toggleMetaData("freeLook", freeLook);
        }
        return false;
    }

    @Override
    public void onHideMenu(MapItem item) {
    }
}
