
package com.atakmap.android.selfcoordoverlay;

import java.text.DecimalFormat;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.location.LocationMapComponent;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.PointMapItem.OnPointChangedListener;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.navigation.SelfCoordBottomBar;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.tools.SpecifySelfLocationTool;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.AltitudeUtilities;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.android.util.SpeedFormatter;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MapWidget.OnLongPressListener;
import com.atakmap.android.widgets.MarkerIconWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.android.widgets.TextWidget;
import com.atakmap.app.R;
import com.atakmap.app.SettingsActivity;
import com.atakmap.app.preferences.NetworkConnectionPreferenceFragment;
import com.atakmap.comms.CotStreamListener;
import com.atakmap.comms.ReportingRate;
import com.atakmap.comms.app.CotPortListActivity;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.Ellipsoid;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MGRSPoint;
import com.atakmap.coremap.maps.coords.NorthReference;
import gov.tak.api.util.Disposable;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.View;

public class SelfCoordOverlayUpdater extends CotStreamListener implements
        MapEventDispatcher.MapEventDispatchListener,
        OnSharedPreferenceChangeListener, OnPointChangedListener,
        MapWidget.OnClickListener, View.OnClickListener {

    private static final String TAG = "SelfCoordOverlayUpdater";

    // Display preference
    public static final String PREF_DISPLAY = "self_coord_info_display";
    public static final String DISPLAY_BOTTOM_RIGHT = "bottom_right";
    public static final String DISPLAY_BOTTOM_BAR = "bottom_bar";
    public static final String DISPLAY_NONE = "none";

    // Old preference for toggling display
    private static final String PREF_LEGACY_DISPLAY = "show_self_coordinate_overlay";

    private final MapView _mapView;
    protected static final DecimalFormat _noDecimalFormat = LocaleUtil
            .getDecimalFormat("0");
    protected TextWidget _text, _noGpsMessage;
    private final SelfCoordBottomBar _bottomBar;
    private Marker _self;
    private String _toggle;
    private boolean _enlarge;
    private boolean _visible = true;
    private String _displayType;
    private boolean _noGps = true;

    protected Updater calc = null;

    private final static int ICON_WIDTH = 32;
    private final static int ICON_HEIGHT = 32;

    protected static final int NOTIFICATION_ID = 31576;

    /**
     * Display status of TAK server connection
     */
    protected final MarkerIconWidget _connectedButtonWidget;
    protected int _connIcon = 0;

    protected CoordinateFormat _coordMode;
    private BroadcastReceiver prirec;

    protected final SharedPreferences _prefs;
    protected int _northReference;

    // long press
    private boolean _isEnlarged = false;

    private MapTextFormat _format;

    /**
     * Track if we're currently connected
     */
    protected boolean _bConnected;

    /**
     * We display if at least one TAK Server connection exists on local device
     */
    protected boolean _bAtLeastOneConnection;

    protected boolean _streamChanged = false;

    private BroadcastReceiver selflocrec;

    protected SpeedFormatter speedFormatter;

    @Override
    public void onMapEvent(MapEvent event) {
        if (event.getType().equals(MapEvent.ITEM_ADDED)) {
            final String deviceUID = _mapView.getSelfMarker()
                    .getUID();
            final MapItem item = event.getItem();
            final String itemUID = item.getUID();

            if (itemUID != null && itemUID.equals(deviceUID)
                    && item instanceof PointMapItem)
                _self = (Marker) item;

            /*
             * this is done under a map event so that it can find itself. if it fails to
             * find the self marker, it will keep trying every time there is an update
             * until the self marker is present. AS.
             */
            if (_self != null) {
                calc.change();
                _self.addOnPointChangedListener(this);
                _mapView.getMapEventDispatcher()
                        .removeMapEventListener(
                                MapEvent.ITEM_ADDED, this); // stop listening when we've found
                // ourself
                // I've gone out to look for myself
                // If I return before I get back
                // Please tell me to wait
            }

        }
    }

    public static SelfCoordOverlayUpdater getInstance() {
        return _instance;
    }

    public void change() {
        calc.change();
    }

    public static SelfCoordOverlayUpdater _instance;

    public SelfCoordOverlayUpdater(MapView mapView) {
        super(mapView.getContext(), TAG, null);

        _mapView = mapView;
        calc = new Updater();

        speedFormatter = SpeedFormatter.getInstance();

        RootLayoutWidget root = (RootLayoutWidget) _mapView.getComponentExtra(
                "rootLayoutWidget");
        LinearLayoutWidget brLayout = root
                .getLayout(RootLayoutWidget.BOTTOM_RIGHT);

        _format = MapView.getTextFormat(Typeface.DEFAULT, 2);

        // Message to display when self marker is not set and there's no GPS
        _noGpsMessage = new TextWidget(_mapView.getContext().getString(
                R.string.nogps_stmt), 4);
        _noGpsMessage.setName("No GPS Text");
        _noGpsMessage.setColor(Color.RED);
        _noGpsMessage.addOnClickListener(this);
        brLayout.addWidget(_noGpsMessage);

        // Self coordinate text
        _text = new TextWidget("", _format);
        _text.setName("Self Coordinate Text");
        _text.setVisible(false);
        _text.addOnClickListener(this);
        _text.addOnLongPressListener(_textLongPressListener);
        brLayout.addWidget(_text);

        LinearLayoutWidget hTray = brLayout.getOrCreateLayout(
                "SelfLocTray_H");
        _connectedButtonWidget = new MarkerIconWidget();
        _connectedButtonWidget.setName("Connection Icon");
        _connectedButtonWidget.addOnClickListener(this);
        hTray.addChildWidgetAt(0, _connectedButtonWidget);

        _self = ATAKUtilities.findSelf(mapView);

        DocumentedIntentFilter priFilter = new DocumentedIntentFilter();
        priFilter
                .addAction("com.atakmap.android.action.PRI_TRANSFORMED_COORDS");
        AtakBroadcast.getInstance().registerReceiver(
                prirec = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context c, Intent i) {
                        // do stuff.
                    }
                }, priFilter);

        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_ADDED, this);

        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_RESIZED, this);

        _prefs = PreferenceManager.getDefaultSharedPreferences(_mapView
                .getContext());
        _prefs.registerOnSharedPreferenceChangeListener(this);
        _coordMode = CoordinateFormat.find(_prefs.getString(
                "coord_display_pref",
                _mapView.getContext().getString(
                        R.string.coord_display_pref_default)));
        _northReference = Integer
                .parseInt(_prefs.getString("rab_north_ref_pref",
                        String.valueOf(NorthReference.MAGNETIC.getValue())));
        _toggle = _prefs.getString("self_coord_action", "panto");
        _enlarge = _prefs.getBoolean("selfcoord_legacy_enlarge", false);

        // Where to display the coordinate info
        if (_prefs.contains(PREF_DISPLAY)) {
            // Whether to display the bottom-right widget
            _displayType = getDisplayType();
        } else {
            // Legacy conversion
            onSharedPreferenceChanged(_prefs, PREF_LEGACY_DISPLAY);
        }

        DocumentedIntentFilter selfLocationSpecifiedFilter = new DocumentedIntentFilter();
        selfLocationSpecifiedFilter
                .addAction("com.atakmap.android.map.SELF_LOCATION_SPECIFIED");
        AtakBroadcast.getInstance().registerReceiver(
                selflocrec = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        _noGps = false;
                        updateTextVisibility();
                    }
                }, selfLocationSpecifiedFilter);

        // View that displays coordinate data on the bottom of the screen
        _bottomBar = ((Activity) _context).findViewById(
                R.id.self_coordinate_bar);
        _bottomBar.setOnClickListener(this);

        _instance = this;
    }

    @Override
    public void dispose() {
        super.dispose();

        Log.d("Night Vision", " disposed");

        if (selflocrec != null)
            AtakBroadcast.getInstance().unregisterReceiver(selflocrec);
        selflocrec = null;

        if (prirec != null)
            AtakBroadcast.getInstance().unregisterReceiver(prirec);
        prirec = null;

        calc.dispose();
    }

    @Override
    public void onSharedPreferenceChanged(
            final SharedPreferences sp,
            final String key) {

        if (key == null)
            return;

        switch (key) {
            // Where to display the self coordinate
            case PREF_DISPLAY:
                _displayType = getDisplayType();
                updateTextVisibility();
                break;

            // Legacy display preference redirect
            case PREF_LEGACY_DISPLAY:
                boolean show = sp.getBoolean(key, true);
                sp.edit().putString(PREF_DISPLAY,
                        show ? DISPLAY_BOTTOM_RIGHT : DISPLAY_NONE).apply();
                break;

            case "alt_unit_pref":
            case "alt_display_pref":
                calc.invalidateCache();
                calc.change();
                break;
            case "rab_north_ref_pref":
                _northReference = Integer.parseInt(sp.getString(key,
                        "0"));
                calc.invalidateCache();
                calc.change();
                break;
            case "self_coord_action":
                _toggle = sp.getString(key, "nothing");
                calc.invalidateCache();
                calc.change();
                break;
            case "coord_display_pref":
                _coordMode = CoordinateFormat.find(sp.getString(
                        key,
                        _mapView.getContext().getString(
                                R.string.coord_display_pref_default)));
                calc.invalidateCache();
                calc.change();
                break;
            case "displayServerConnectionWidget":
                calc.change();
                break;
            case "selfcoord_legacy_enlarge":
                _enlarge = sp.getBoolean(key, false);
                break;
            case "replaceCallsign":
                calc.invalidateCache();
                calc.change();
                break;
        }
    }

    @Override
    public void onPointChanged(PointMapItem item) {
        _noGps = false;
        updateTextVisibility();
        calc.change();
    }

    /**
     * Get the GPS text widget
     * @return Text widget
     */
    public TextWidget getWidget() {
        if (_text != null)
            return _text;
        else
            return _noGpsMessage;
    }

    /**
     * Get the self coordinate info display position/type
     * @return Either {@link #DISPLAY_BOTTOM_RIGHT}, {@link #DISPLAY_BOTTOM_BAR}
     * or {@link #DISPLAY_NONE}
     */
    public String getDisplayType() {
        return _prefs.getString(PREF_DISPLAY, DISPLAY_BOTTOM_RIGHT);
    }

    protected class Updater implements Runnable, Disposable {
        boolean disposed;
        int state;
        final Thread thread;

        Updater() {
            this.disposed = false;
            this.state = 0;

            this.thread = new Thread(this);
            this.thread.setPriority(Thread.NORM_PRIORITY);
            this.thread.setName("Updater-SelfCoordinate");
            this.thread.start();
        }

        public void invalidateCache() {
            cachedPoint = null;
            cachedLocationStr = null;
            cachedAltString = null;
            cachedType = null;
            cachedAltitude = Double.NEGATIVE_INFINITY;
        }

        public synchronized void change() {
            this.state++;
            this.notify();
        }

        @Override
        public void dispose() {
            synchronized (this) {
                this.disposed = true;
                this.notify();
            }
        }

        @Override
        public void run() {

            int compute = 0;
            while (true) {
                synchronized (this) {
                    if (this.disposed)
                        break;
                    if (compute == this.state) {
                        try {
                            this.wait();
                        } catch (InterruptedException ignored) {
                        }
                        continue;
                    }
                    compute = this.state;
                }
                updateText(_self);
                try {
                    Thread.sleep(350);
                } catch (InterruptedException ignore) {
                }
            }

        }

        private String cachedLocationStr = "";
        private String cachedAltString;
        private CoordinateFormat cachedType;
        private GeoPoint cachedPoint;
        private double cachedAltitude;

        protected void updateText(Marker item) {
            // Get the effective instead of desired prefix, so that this overlay also falls back to
            // other location providers
            String prefix = _mapView.getMapData().getString(
                    "locationSourceEffectivePrefix");

            String locationStr = "";

            String callsign = _mapView.getDeviceCallsign();

            // protection based on the arbitrary max callsign length 
            //    ATAK-8010 ATAK My Callsign - Seems to have no limit 
            //    on the size of the callsign

            if (callsign.length() > 40)
                callsign = callsign.substring(0, 40);

            String altString = "--- ft MSL";
            String speedString = speedFormatter.getSpeedFormatted(item);
            String accuracyString = "+/- ---m";
            String orientationString = "---" + Angle.DEGREE_SYMBOL;

            if (item != null) {
                GeoPoint point = item.getPoint();

                if (cachedPoint != null && cachedType != null
                        && cachedLocationStr != null &&
                        point.getLatitude() == cachedPoint.getLatitude() &&
                        point.getLongitude() == cachedPoint.getLongitude() &&
                        _coordMode == cachedType) {
                    locationStr = cachedLocationStr;
                } else if (_coordMode == CoordinateFormat.MGRS) {
                    MGRSPoint mgrs = MGRSPoint.fromLatLng(Ellipsoid.WGS_84,
                            point.getLatitude(),
                            point.getLongitude(), null);
                    locationStr = mgrs.getFormattedString();
                } else if (_coordMode == CoordinateFormat.DD) {
                    locationStr = CoordinateFormatUtilities.formatToString(
                            point,
                            CoordinateFormat.DD);
                } else if (_coordMode == CoordinateFormat.DM) {
                    locationStr = CoordinateFormatUtilities.formatToString(
                            point,
                            CoordinateFormat.DM);
                } else if (_coordMode == CoordinateFormat.DMS) {
                    locationStr = CoordinateFormatUtilities.formatToString(
                            point,
                            CoordinateFormat.DMS);
                } else if (_coordMode == CoordinateFormat.UTM) {
                    locationStr = CoordinateFormatUtilities.formatToString(
                            point,
                            CoordinateFormat.UTM);
                }
                cachedLocationStr = locationStr;
                cachedPoint = point;
                cachedType = _coordMode;

                double alt = item.getPoint().getAltitude();
                if (cachedAltString != null
                        && Double.compare(alt, cachedAltitude) == 0) {
                    altString = cachedAltString;
                } else {
                    if (item.getPoint().isAltitudeValid())
                        altString = AltitudeUtilities.format(
                                item.getPoint(), _prefs);
                    cachedAltString = altString;
                }
                cachedAltitude = alt;

                speedString = speedFormatter.getSpeedFormatted(item);

                final double accuracy = item.getPoint().getCE();
                if (!Double.isNaN(accuracy)) {
                    accuracyString = "+/- " + _noDecimalFormat.format(accuracy)
                            + "m";
                }

                double orientation = item.getTrackHeading();
                if (!Double.isNaN(orientation)) {
                    String unit = "T";
                    if (_northReference != 0) { // NORTH_REFERENCE_TRUE=0, NORTH_REFERENCE_MAGNETIC=1,
                        // NORTH_REFERENCE_GRID=2
                        orientation = ATAKUtilities.convertFromTrueToMagnetic(
                                item.getPoint(), orientation);
                        unit = "M";
                    }
                    orientationString = AngleUtilities.format(orientation)
                            + unit;
                }
            }
            int firstLine = Color.CYAN;

            // Comparisons against gpsUpdateTick are against SystemClock.elapsedRealtime as per the definition in
            // LocationMapComponent.

            String source = "";

            if ((prefix != null && prefix.equals("fake"))
                    || item == null
                    || SystemClock.elapsedRealtime()
                            - item.getMetaLong("gpsUpdateTick",
                                    0) > LocationMapComponent.GPS_TIMEOUT_MILLIS) {
                source = "NO GPS";
                firstLine = Color.RED;

                accuracyString = "+/- ---m";
                _mapView.getMapData()
                        .putBoolean("mockLocationCallsignValid", false); // invalidate the
                // mock callsign
            }
            if (prefix != null
                    && _mapView.getMapData().containsKey(
                            prefix + "LocationSource")) {
                source = _mapView.getMapData().getString(
                        prefix + "LocationSource");
                if (_mapView.getMapData().containsKey(
                        prefix + "LocationSourceColor")) {
                    firstLine = Color.parseColor(_mapView.getMapData()
                            .getString(
                                    prefix + "LocationSourceColor"));
                } else {
                    firstLine = Color.GREEN;
                }
            }

            StringBuilder coordText = new StringBuilder(512);
            if (!FileSystemUtils.isEmpty(source))
                coordText.append(source).append("\n");
            coordText.append(_mapView.getContext().getString(R.string.callsign))
                    .append(": ");
            coordText.append(callsign);
            coordText.append("\n");

            final float callsignWidth = _format.measureTextWidth(coordText
                    .toString());

            if (locationStr == null)
                locationStr = "";

            final float locationWidth = _format.measureTextWidth(locationStr);
            final float maxWidth = Math.max(callsignWidth, locationWidth);

            final float spaceWidth = _format.measureTextWidth(" ");

            final float altWidth = _format.measureTextWidth(altString);
            final float orientationWidth = _format
                    .measureTextWidth(orientationString);
            final float speedWidth = _format.measureTextWidth(speedString);
            final float accuracyWidth = _format
                    .measureTextWidth(accuracyString);

            StringBuilder line1Space = new StringBuilder();
            for (int spaceCount = 0; altWidth +
                    +orientationWidth
                    + spaceCount * spaceWidth < maxWidth; spaceCount++) {
                line1Space.append(" ");
            }

            StringBuilder line2Space = new StringBuilder();
            for (int spaceCount = 0; speedWidth + accuracyWidth + spaceCount
                    * spaceWidth < maxWidth; spaceCount++) {
                line2Space.append(" ");
            }

            coordText.append(locationStr);
            coordText.append("\n");
            coordText.append(altString);
            coordText.append(line1Space);
            coordText.append(orientationString);
            coordText.append("\n");
            coordText.append(speedString);
            coordText.append(line2Space);
            coordText.append(accuracyString);

            final String coordTextString = coordText.toString();
            int lineCount = countLine(coordTextString);

            updateTextVisibility();

            if (!_noGps) {
                _text.setText(coordTextString);

                int[] colors = new int[lineCount];
                colors[0] = firstLine;
                for (int i = 1; i < colors.length; i++) {
                    colors[i] = Color.CYAN;
                }
                _text.setColors(colors);
            }

            // Bundle coordinate text into a struct
            SelfCoordText txt = new SelfCoordText();
            txt.source = source;
            txt.sourceColor = firstLine;
            txt.callsign = callsign;
            txt.location = locationStr;
            txt.altitude = altString;
            txt.heading = orientationString;
            txt.speed = speedString;
            txt.accuracy = accuracyString;

            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    // Pass coordinate text to the bottom bar
                    // TODO: Interface for registering other text receivers
                    if (_bottomBar != null)
                        _bottomBar.onSelfCoordinateChanged(txt);
                }
            });

            synchronized (this) {
                if (_streamChanged) {
                    _streamChanged = false;
                    if (_bAtLeastOneConnection && isEnabled()) {
                        final String message = _bConnected
                                ? _mapView.getContext().getString(
                                        R.string.notification_text28)
                                        + getFirstActiveDescription()
                                : _mapView.getContext().getString(
                                        R.string.notification_text29);
                        NotificationUtil.getInstance().postNotification(
                                NOTIFICATION_ID,
                                getConnectedDrawable(), getConnectedColor(),
                                _mapView.getContext().getString(
                                        R.string.tak_server_status),
                                message,
                                new Intent("com.atakmap.app.NETWORK_SETTINGS"),
                                false);

                        AtakBroadcast.getInstance().sendBroadcast(
                                new Intent(ReportingRate.REPORT_LOCATION));
                        Log.d(TAG, "connected to a server push new report");

                        Log.d(TAG, "server notification status changed");
                    } else {
                        NotificationUtil.getInstance().clearNotification(
                                NOTIFICATION_ID);
                        Log.d(TAG, "server notification status cleared");
                    }
                }
            }
            if (_bAtLeastOneConnection && _prefs.getBoolean(
                    "displayServerConnectionWidget", false)) {
                int iconRes = getConnectedDrawable();
                if (iconRes != _connIcon) {
                    String imageUri = "android.resource://"
                            + _mapView.getContext().getPackageName() + "/"
                            + getConnectedDrawable();

                    Icon.Builder builder = new Icon.Builder();
                    builder.setAnchor(0, 0);
                    builder.setColor(Icon.STATE_DEFAULT, Color.WHITE);
                    builder.setSize(ICON_WIDTH, ICON_HEIGHT);
                    builder.setImageUri(Icon.STATE_DEFAULT, imageUri);

                    Icon icon = builder.build();
                    _connectedButtonWidget.setIcon(icon);
                    _connIcon = iconRes;
                }
                _connectedButtonWidget.setVisible(true);
            } else
                _connectedButtonWidget.setVisible(false);
        }
    }

    @Override
    public void onMapWidgetClick(MapWidget widget, MotionEvent event) {
        // User tapped NO GPS message - allow user to set self location
        if (widget == _noGpsMessage && !_mapView.getMapData()
                .containsKey("fakeLocation")) {
            ToolManagerBroadcastReceiver.getInstance().startTool(
                    SpecifySelfLocationTool.TOOL_IDENTIFIER, new Bundle());
        }

        // User tapped self coordinate text - cycle coordinate or pan to self
        else if (widget == _text) {
            switch (_toggle) {
                case "cyclecoordinate":
                    toggleCoorDisplay();
                    calc.change();
                    break;
                case "panto":
                    if (_self != null && _self.getGroup() != null) {
                        // Check if the radial is opened on another map item
                        final MapItem mi = MapMenuReceiver.getCurrentItem();
                        if (mi != null && !mi.getUID().equals(_self.getUID())) {
                            // Hide radial, coordinate info, and un-focus
                            AtakBroadcast.getInstance()
                                    .sendBroadcast(new Intent(
                                            MapMenuReceiver.HIDE_MENU));
                            AtakBroadcast.getInstance()
                                    .sendBroadcast(new Intent(
                                            "com.atakmap.android.maps.HIDE_DETAILS"));
                            AtakBroadcast.getInstance().sendBroadcast(
                                    new Intent(
                                            "com.atakmap.android.maps.UNFOCUS"));
                        }
                        // Pan to the self marker without triggering the receiver used
                        // to reset self lock-on
                        _mapView.getMapController().panTo(_self.getPoint(),
                                true,
                                false);
                    }
                    break;
            }
        }

        // User tapped network icon - bring up settings
        else if (widget == _connectedButtonWidget) {
            if (connectedButtonWidgetCallback != null) {
                connectedButtonWidgetCallback.onConnectedButtonWidgetClick();
            } else {
                SettingsActivity
                        .start(NetworkConnectionPreferenceFragment.class);
            }
        }
    }

    @Override
    public void onClick(View v) {
        // Redirect clicks on the bottom bar to the appropriate widget to
        // ensure identical behavior
        if (v == _bottomBar && DISPLAY_BOTTOM_BAR.equals(_displayType)) {
            MotionEvent event = MotionEvent.obtain(0, 0,
                    MotionEvent.ACTION_DOWN, 0, 0, 0);
            if (_noGps)
                onMapWidgetClick(_noGpsMessage, event);
            else
                onMapWidgetClick(_text, event);
            event.recycle();
        }
    }

    public boolean showGPSWidget(boolean show) {
        if (_visible != show) {
            _visible = show;
            updateTextVisibility();
            return true;
        }
        return false;
    }

    protected void updateTextVisibility() {
        boolean show = _displayType == null || _displayType.equals(
                DISPLAY_BOTTOM_RIGHT);
        _noGpsMessage.setVisible(_noGps && _visible && show);
        _text.setVisible(!_noGps && _visible && show);
    }

    protected synchronized int getConnectedDrawable() {
        return _bConnected ? ATAKConstants.getServerConnection(true)
                : ATAKConstants.getServerConnection(false);
    }

    protected synchronized NotificationUtil.NotificationColor getConnectedColor() {
        return _bConnected ? NotificationUtil.GREEN
                : NotificationUtil.RED;
    }

    private void streamsChanged() {
        synchronized (this) {
            _bConnected = isConnected();
            _bAtLeastOneConnection = getStreamCount() > 0;
            _streamChanged = true;
        }
        calc.change();
    }

    @Override
    protected void serviceConnected() {
        streamsChanged();
    }

    @Override
    public void onCotOutputRemoved(Bundle bundle) {
        super.onCotOutputRemoved(bundle);
        streamsChanged();
    }

    @Override
    protected void enabled(CotPortListActivity.CotPort port,
            boolean enabled) {
        streamsChanged();
    }

    @Override
    protected void connected(CotPortListActivity.CotPort port,
            boolean connected) {
        streamsChanged();
    }

    private void toggleTextEnlargement() {
        if (!_enlarge)
            return;

        _isEnlarged = !_isEnlarged;
        if (_isEnlarged)
            _format = MapView.getTextFormat(Typeface.DEFAULT, +7);
        else
            _format = MapView.getTextFormat(Typeface.DEFAULT, +2);
        _text.setTextFormat(_format);
    }

    protected final OnLongPressListener _textLongPressListener = new OnLongPressListener() {

        @Override
        public void onMapWidgetLongPress(MapWidget widget) {
            if (!_enlarge)
                return;
            toggleTextEnlargement();
            _prefs.edit().putInt("coord_legacy_enlarge", _format.getFontSize())
                    .apply();
        }
    };

    private int countLine(final String s) {
        int len = s.length();
        int count = 1;
        for (int i = 0; i < len; ++i) {
            if (s.charAt(i) == '\n')
                count++;
        }
        return count;
    }

    private void toggleCoorDisplay() {
        switch (_coordMode) {
            case DD:
                _coordMode = CoordinateFormat.DM;
                break;
            case DM:
                _coordMode = CoordinateFormat.DMS;
                break;
            case DMS:
                _coordMode = CoordinateFormat.UTM;
                break;
            case UTM:
                _coordMode = CoordinateFormat.MGRS;
                break;
            case MGRS:
                _coordMode = CoordinateFormat.DD;
                break;
            default:
                break;
        }
        _prefs.edit()
                .putString("coord_display_pref", _coordMode.getDisplayName())
                .apply();
    }

    public interface ConnectedButtonWidgetCallback {
        void onConnectedButtonWidgetClick();
    }

    private static ConnectedButtonWidgetCallback connectedButtonWidgetCallback = null;

    /**
     * Sets a callback that will fire when the connectedButtonWidget is clicked. Allows callers
     * to implement custom onMapWidgetClick behavior for the connectedButtonWidget.
     * @param callback the callback to use
     */
    public static void setConnectedButtonWidgetCallback(
            ConnectedButtonWidgetCallback callback) {
        connectedButtonWidgetCallback = callback;
    }
}
