
package com.atakmap.android.user;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapItem.OnMetadataChangedListener;
import com.atakmap.android.maps.MapMode;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.Marker.OnTrackChangedListener;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.PointMapItem.OnPointChangedListener;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.AtakMapController;
import com.atakmap.map.CameraController;
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.elevation.ElevationManager;

public class CamLockerReceiver extends BroadcastReceiver implements
        MapEventDispatcher.MapEventDispatchListener,
        OnTrackChangedListener, OnMetadataChangedListener,
        OnPointChangedListener, AtakMapController.OnPanRequestedListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = "CamLockerReceiver";

    private final static double BOTTOM_LANDSCAPE_PERCENT = .85d;
    private final static double BOTTOM_PORTRAIT_PERCENT = .90d;

    public static final String TOGGLE_LOCK = "com.atakmap.android.maps.TOGGLE_LOCK";
    public static final String TOGGLE_LOCK_LONG_CLICK = "com.atakmap.android.maps.TOGGLE_LOCK_LONG_CLICK";
    public static final String LOCK_CAM = "com.atakmap.android.map.action.LOCK_CAM";
    public static final String UNLOCK_CAM = "com.atakmap.android.map.action.UNLOCK_CAM";

    private boolean forceCenter = false;

    private boolean trackupMode = false; // ATAK always starts in north-up

    private boolean _suppressSnapback;
    private long _snapbackCooldown;
    private final MapView _mapView;
    private PointMapItem _lockedItem;
    private final AtakMapController ctrl;
    private final MapTouchController touch;
    private final Thread lock_monitor;
    private final RelockWidget _relockWidget;
    private String _lastUid;
    private float lastHeading = 0f;
    private boolean disposed = false;
    private final SharedPreferences prefs;
    private boolean disableFloatToBottom;

    public CamLockerReceiver(final MapView mapView) {
        _mapView = mapView;

        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_PRESS, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_SCROLL, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_RELEASE, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_REMOVED, this);
        _snapbackCooldown = 0;
        _suppressSnapback = false;

        ctrl = _mapView.getMapController();
        touch = _mapView.getMapTouchController();

        lock_monitor = new Thread(TAG + "-Monitor") {
            @Override
            public void run() {
                PointMapItem li;
                do {
                    try {
                        Thread.sleep(750);
                    } catch (InterruptedException ignored) {
                    }
                    li = _lockedItem;
                    if (li != null)
                        onPointChanged(li);

                    if (_relockWidget != null) {
                        _relockWidget.onTick();
                    }
                } while (!disposed);
            }
        };
        lock_monitor.start();

        _relockWidget = new RelockWidget(mapView);

        prefs = PreferenceManager
                .getDefaultSharedPreferences(mapView.getContext());
        onSharedPreferenceChanged(prefs, "disableFloatToBottom");
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {

        if (key == null)
            return;

        if (key.equals("disableFloatToBottom")) {
            disableFloatToBottom = sharedPreferences.getBoolean(key, false);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals(LOCK_CAM)) {
            String uid = intent.getStringExtra("uid");
            if (uid != null) {
                MapItem item = _mapView.getMapItem(uid);
                if (item instanceof PointMapItem) {
                    PointMapItem pointItem = (PointMapItem) item;
                    boolean justUnlock = _lockedItem == pointItem;
                    unlockItem(false);
                    if (!justUnlock) {
                        lockItem(pointItem);
                    }

                    hideRelockWidget();
                }
            }
            forceCenter = false;
        } else if (action.equals(UNLOCK_CAM)) {
            boolean bCenter = intent.getBooleanExtra("center", true);
            unlockItem(bCenter);
            displayRelockWidget();
        } else if (action.equals("com.atakmap.android.maps.HIDE_MENU")) {
            forceCenter = false;
        } else if (action.equals("com.atakmap.android.maps.SHOW_MENU")) {
            String uid = intent.getStringExtra("uid");
            if (uid != null) {
                MapItem item = _mapView.getMapItem(uid);
                if (_lockedItem != item) {
                    unlockItem(false);
                    forceCenter = false;
                    displayRelockWidget();
                } else {
                    // if the locked item has had the menu shown, just make the resting point the center
                    // of the screen.
                    forceCenter = true;
                }

            }
        }

        else if (action.equals(MapMode.TRACK_UP.getIntent())) {
            this.trackupMode = true;
        } else if (action.equals(MapMode.NORTH_UP.getIntent())) {
            this.trackupMode = false;
        } else if (action.equals(MapMode.MAGNETIC_UP.getIntent())) {
            this.trackupMode = false;
        } else if (action.equals(MapMode.USER_DEFINED_UP.getIntent())) {
            this.trackupMode = false;
        }

    }

    private void displayRelockWidget() {
        if (_relockWidget != null && !FileSystemUtils.isEmpty(_lastUid)
                && !ATAKUtilities.isSelf(_mapView, _lastUid)) {
            Log.d(TAG, "displayRelockWidget");
            _relockWidget.setUid(_lastUid);
            _relockWidget.setVisible(true);
            _lastUid = null;
        }
    }

    private void hideRelockWidget() {
        if (_relockWidget != null) {
            //Log.d(TAG, "hideRelockWidget");
            _relockWidget.setUid(null);
            _relockWidget.setVisible(false);
            _lastUid = null;
        }
    }

    synchronized public void unlockItem(final boolean center) {
        if (_lockedItem != null) {
            _lockedItem.removeOnPointChangedListener(this);
            _lockedItem
                    .removeOnMetadataChangedListener("driving", this);
            if (_lockedItem instanceof Marker)
                ((Marker) _lockedItem)
                        .removeOnTrackChangedListener(this);

            // remove indicator that the item is locked
            _lockedItem.removeMetaData("camLocked");

            // if I were in driving mode and the lock was ended, zoom back to center.
            // should not hurt if I was not driving.   Only activated when not moving 
            // from locked state to locked state and not opening a menu widget.
            if (center)
                ctrl.panTo(_lockedItem.getPoint(), true, false);

            _lastUid = _lockedItem.getUID();
            _lockedItem = null;

            touch.setFreeForm3DPoint(null);
        }
    }

    synchronized public void lockItem(final PointMapItem pointItem) {
        _lockedItem = pointItem;
        pointItem.addOnPointChangedListener(this);
        pointItem.addOnMetadataChangedListener("driving", this);

        if (pointItem instanceof Marker)
            ((Marker) pointItem)
                    .addOnTrackChangedListener(this);

        touch.setFreeForm3DPoint(pointItem.getPoint());

        _lockedItem.setMetaBoolean("camLocked", true);

        Toast.makeText(
                _mapView.getContext(),
                _mapView.getResources().getString(R.string.locked_on_tip)
                        + ATAKUtilities.getDisplayName(pointItem) + ".",
                Toast.LENGTH_SHORT).show();
        onPointChanged(_lockedItem);
    }

    public boolean isLocked() {
        return _lockedItem != null;
    }

    public PointMapItem getLockedItem() {
        return _lockedItem;
    }

    @Override
    public void onTrackChanged(Marker marker) {
        if (marker != _lockedItem) {
            marker.removeOnTrackChangedListener(this);
            return;
        }

        final float heading = (float) marker.getTrackHeading();

        if (Double.isNaN(heading) || heading < 0f || heading > 360f) {
            return;
        }
        if (marker.getMetaBoolean("driving", false)) {
            if (Math.abs(heading - lastHeading) > 10) {
                lastHeading = heading;
                onPointChanged(marker);
            }
        }

    }

    @Override
    public void onMetadataChanged(MapItem item, final String field) {
        if (item instanceof Marker && field.equals("driving")) {
            onPointChanged((Marker) item);
        }
    }

    @Override
    public void onPointChanged(final PointMapItem item) {
        if (item != _lockedItem) {
            item.removeOnPointChangedListener(this);
            return;
        }

        final MapRenderer3 renderer = _mapView.getRenderer3();

        // ATAK-15238
        if (renderer == null)
            return;

        if (!suppressSnapback()) {
            MapSceneModel sm = renderer.getMapSceneModel(false,
                    MapRenderer2.DisplayOrigin.UpperLeft);
            GeoPoint focuslla = GeoPoint.createMutable();
            float focusx = sm.focusx;
            float focusy = sm.focusy;
            if (forceCenter) {
                sm.mapProjection.inverse(sm.camera.target, focuslla);
            } else {
                focuslla.set(item.getPoint());
                if (!disableFloatToBottom && trackupMode
                        && _mapView.getSelfMarker().getUID()
                                .compareTo(
                                        item.getUID()) == 0
                        && item.getMetaBoolean("driving", false)) {

                    // XXX - If marker altitude is below terrain, this will
                    //       have the effect of shifting the marker "up" the
                    //       screen. Checking for the local elevation and
                    //       adjusting the item altitude does effectively
                    //       position the point at the desired offset HOWEVER
                    //       other code that may be manipulating camera (e.g.
                    //       `LocationMapComponent` performing trackup
                    //       rotation) will not know about the adjusted
                    //       location. This will cause the map to jitter
                    //       between updates operating at the point at true
                    //       altitude and the point at adjusted altitude. For
                    //       that reason, I am leaving this workaround disabled
                    //       for the time being.
                    if (false) {
                        double localEl = ElevationManager.getElevation(
                                focuslla.getLatitude(), focuslla.getLongitude(),
                                null);
                        if (Double.isNaN(localEl))
                            localEl = 0d;
                        if (focuslla.getAltitude() < localEl)
                            focuslla.set(localEl);
                    }

                    if (_mapView.isPortrait())
                        focusy = sm.height * (float) BOTTOM_PORTRAIT_PERCENT;
                    else
                        focusy = sm.height * (float) BOTTOM_LANDSCAPE_PERCENT;
                }
            }

            // no notification
            CameraController.Interactive.panTo(
                    renderer,
                    focuslla,
                    focusx,
                    focusy,
                    MapRenderer3.CameraCollision.AdjustCamera,
                    !forceCenter);

            // during pinch zoom, the self marker should not attempt to center when driving.
            touch.setFreeForm3DPoint(null);
            //touch.setFreeForm3DPoint(focuslla);
        }
    }

    /**
     * Check if the snapback to the locked-on target should be suppressed
     * @return True to suppress
     */
    private boolean suppressSnapback() {
        return _suppressSnapback
                || SystemClock.elapsedRealtime() <= _snapbackCooldown;
    }

    @Override
    public void onMapEvent(MapEvent event) {

        // Ignore map events when we're not locked on anything
        if (!isLocked())
            return;

        String type = event.getType();

        // Allow user to temporarily pan away from target, but keep them locked
        // on if they're just zooming in or changing tilt
        if (type.equals(MapEvent.MAP_SCROLL) || type.equals(MapEvent.MAP_PRESS)
                && suppressSnapback()) {
            touch.setFreeForm3DPoint(null);
            _suppressSnapback = true;
        }

        // Give the user enough time to start scrolling farther
        else if (type.equals(MapEvent.MAP_RELEASE)) {
            if (_suppressSnapback)
                _snapbackCooldown = SystemClock.elapsedRealtime() + 400;
            _suppressSnapback = false;
        }

        // Stop lock on when target is removed
        else if (type.equals(MapEvent.ITEM_REMOVED) &&
                _lockedItem != null &&
                event.getItem().getUID().equals(_lockedItem.getUID())) {
            final Intent toggle = new Intent();
            toggle.setAction(TOGGLE_LOCK);
            AtakBroadcast.getInstance().sendBroadcast(toggle);
            unlockItem(false);
        }
    }

    @Override
    public void onPanRequested() {
        unlockItem(false);
    }

    public void dispose() {
        if (_lockedItem != null)
            _lockedItem.removeOnPointChangedListener(this);

        prefs.unregisterOnSharedPreferenceChangeListener(this);

        disposed = true;
    }
}
