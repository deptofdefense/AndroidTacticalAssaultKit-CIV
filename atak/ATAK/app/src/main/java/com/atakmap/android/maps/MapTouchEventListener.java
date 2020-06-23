
package com.atakmap.android.maps;

import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.android.user.PlacePointTool;
import java.util.UUID;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.preference.PreferenceManager;

import com.atakmap.android.gui.HintDialogHelper;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.AtakMapController;

/**
 * Default map event listener
 * 
 * 
 */
class MapTouchEventListener implements
        MapEventDispatcher.MapEventDispatchListener {

    private static final String TAG = "MapTouchEventListener";

    private final MapView _mapView;
    private final SharedPreferences _prefs;

    private boolean atakDoubleTapToZoom;
    private boolean atakTapToggleActionBar;
    private boolean atakLongPressDropAPoint;

    MapTouchEventListener(MapView mapView) {
        _mapView = mapView;
        _prefs = PreferenceManager.getDefaultSharedPreferences(mapView
                .getContext());
        _prefs.registerOnSharedPreferenceChangeListener(_prefListener);
        setPrefs();
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener _prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(
                SharedPreferences sharedPreferences, String key) {
            if (key.equals("atakDoubleTapToZoom") ||
                    key.equals("atakLongPressMap")) {
                setPrefs();
            }
        }
    };

    private void setPrefs() {
        atakDoubleTapToZoom = _prefs.getBoolean("atakDoubleTapToZoom", false);
        final String state = _prefs.getString("atakLongPressMap", "actionbar");
        atakTapToggleActionBar = state.equals("actionbar");
        atakLongPressDropAPoint = state.equals("dropicon");
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (event == null || FileSystemUtils.isEmpty(event.getType()))
            return;

        if (MapEvent.MAP_SCALE.equals(event.getType())) {
            AtakMapController ctrl = _mapView.getMapController();
            Point p = event.getPoint();
            ctrl.zoomBy(event.getScaleFactor(), p.x, p.y, false);
        } else if (MapEvent.MAP_SCROLL.equals(event.getType())) {
            AtakMapController ctrl = _mapView.getMapController();
            Point p = event.getPoint();
            ctrl.panBy(p.x, p.y, false, false);
        } else if (MapEvent.MAP_LONG_PRESS.equals(event.getType())) {
            if (atakTapToggleActionBar) {
                HintDialogHelper
                        .showHint(
                                _mapView.getContext(),
                                _mapView.getContext().getString(
                                        R.string.tool_text28),
                                _mapView.getContext().getString(
                                        R.string.tool_text29),
                                "actionbar.display");

                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        ActionBarReceiver.TOGGLE_ACTIONBAR));
            } else if (atakLongPressDropAPoint) {
                dropHostile(event);
            }
        } else if (MapEvent.MAP_DOUBLE_TAP.equals(event.getType())) {
            if (atakDoubleTapToZoom) {
                AtakMapController ctrl = _mapView.getMapController();
                Point p = event.getPoint();
                ctrl.zoomBy(2, p.x, p.y, false);
            }
        }
    }

    private void dropHostile(final MapEvent event) {
        Point p = event.getPoint();

        GeoPointMetaData gp = _mapView.inverseWithElevation(p.x, p.y);
        if (gp == null || !gp.get().isValid()) {
            Log.w(TAG, "Cannot Create Point with no map coordinates");
            return;
        }

        final String uid = UUID.randomUUID().toString();
        final String type = _prefs.getString("lastCoTTypeSet", "a-u-G");

        final boolean showNineLine = _prefs.getBoolean("autostart_nineline",
                false);

        PlacePointTool.MarkerCreator mc = new PlacePointTool.MarkerCreator(gp)
                .setUid(uid)
                .setType(type)
                .showCotDetails(false)
                .setShowNineLine(showNineLine && type.startsWith("a-h"));
        if (_prefs.contains("lastIconsetPath"))
            mc = mc.setIconPath(_prefs.getString("lastIconsetPath", ""));
        Marker placedPoint = mc.placePoint();

        if (placedPoint != null)
            Log.d(TAG, "Dropping new point " + uid + ", at " + gp);
        else
            Log.d(TAG, "Could not place the point");
    }

}
