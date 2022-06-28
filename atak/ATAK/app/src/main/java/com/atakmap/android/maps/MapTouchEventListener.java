
package com.atakmap.android.maps;

import com.atakmap.android.navigation.views.NavView;
import com.atakmap.coremap.log.Log;
import com.atakmap.android.user.PlacePointTool;
import java.util.UUID;
import android.content.SharedPreferences;
import android.graphics.PointF;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.CameraController;
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.MapRenderer3;
import com.atakmap.math.PointD;

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

            if (key == null)
                return;

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
            _mapView.getMapTouchController().onScaleEvent(event);
        } else if (MapEvent.MAP_SCROLL.equals(event.getType())) {
            PointF p = event.getPointF();
            do {
                Bundle panExtras = event.getExtras();
                if (panExtras == null)
                    break;
                boolean doPanTo = panExtras.getBoolean("cameraPanTo", false);
                if (!doPanTo)
                    break;
                final float x = panExtras.getFloat("x", Float.NaN);
                final float y = panExtras.getFloat("y", Float.NaN);
                if (Float.isNaN(x) || Float.isNaN(y))
                    break;
                final GeoPoint originalFocus = panExtras
                        .getParcelable("originalFocus");
                final MapRenderer3 glglobe = _mapView.getRenderer3();

                // don't attempt pan-to when off world
                final MapRenderer2.InverseResult result = glglobe.inverse(
                        new PointD(x, y, 0d),
                        GeoPoint.createMutable(),
                        MapRenderer2.InverseMode.RayCast,
                        MapRenderer2.HINT_RAYCAST_IGNORE_SURFACE_MESH
                                | MapRenderer2.HINT_RAYCAST_IGNORE_TERRAIN_MESH,
                        MapRenderer2.DisplayOrigin.UpperLeft);

                if (result == MapRenderer2.InverseResult.None)
                    break;

                CameraController.Interactive.panTo(glglobe, originalFocus, x, y,
                        MapRenderer3.CameraCollision.AdjustFocus, false);
                return;
            } while (false);
            CameraController.Interactive.panBy(_mapView.getRenderer3(), p.x,
                    p.y, MapRenderer3.CameraCollision.AdjustFocus, false);
        } else if (MapEvent.MAP_LONG_PRESS.equals(event.getType())) {
            if (atakTapToggleActionBar)
                NavView.getInstance().toggleButtons();
            else if (atakLongPressDropAPoint)
                dropHostile(event);
        } else if (MapEvent.MAP_DOUBLE_TAP.equals(event.getType())) {
            if (atakDoubleTapToZoom) {
                PointF p = event.getPointF();
                GeoPoint focus = null;
                Bundle panExtras = event.getExtras();
                if (panExtras != null)
                    focus = panExtras.getParcelable("originalFocus");
                if (focus == null) {
                    focus = GeoPoint.createMutable();
                    _mapView.getRenderer3().inverse(new PointD(p.x, p.y), focus,
                            MapRenderer2.InverseMode.RayCast,
                            MapRenderer2.HINT_RAYCAST_IGNORE_SURFACE_MESH
                                    | MapRenderer2.HINT_RAYCAST_IGNORE_TERRAIN_MESH,
                            MapRenderer2.DisplayOrigin.UpperLeft);
                }
                // XXX - don't allow further zoom in once collision occurs
                final MapRenderer3.CameraCollision collide = MapRenderer3.CameraCollision.Abort;
                if (focus != null && focus.isValid())
                    CameraController.Interactive.zoomBy(_mapView.getRenderer3(),
                            2d, focus, p.x, p.y, collide, false);
                else
                    CameraController.Interactive.zoomBy(_mapView.getRenderer3(),
                            2d, collide, false);
            }
        }
    }

    private void dropHostile(final MapEvent event) {
        PointF p = event.getPointF();

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
