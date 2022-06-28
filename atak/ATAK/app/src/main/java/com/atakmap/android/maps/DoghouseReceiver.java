
package com.atakmap.android.maps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.overlay.DefaultMapGroupOverlay;
import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.RouteMapReceiver;

import java.util.List;

public class DoghouseReceiver extends BroadcastReceiver implements
        MapItem.OnVisibleChangedListener,
        SharedPreferences.OnSharedPreferenceChangeListener,
        MapEventDispatcher.MapEventDispatchListener,
        Shape.OnPointsChangedListener,
        Route.OnRouteMethodChangedListener, Route.OnStrokeColorChangedListener {

    private static final String TAG = "DoghouseReceiver";

    /** Name of the Doghouse MapGroup */
    public static final String DOGHOUSE_GROUP = "Doghouses";

    /** Key for route metadata boolean indicating whether to show/hide doghouses */
    public static final String META_SHOW_DOGHOUSES = "showDoghouses";

    /** Intent to change the DTD ID of the each doghouse for a Route */
    public static final String CHANGE_DTD_ID = "com.atakmap.android.maps.Doghouse.CHANGE_DTD_ID";

    public static final String META_DTD_ID = "dtdId";
    public static final String META_CASCADE_DTD_ID = "cascade";

    // location preferences
    /** Set the distance from the associated route */
    public static final String DISTANCE_FROM_LEG = "dhPrefs_dxFromLeg";
    /** Set the distance along the associated route as a percentage */
    public static final String PERCENT_ALONG_LEG = "dhPrefs_percentDownLeg";
    /** Set the location preference relative to the associated route */
    public static final String RELATIVE_LOCATION = "dhPrefs_displayLocation";

    public static final String NORTH_REFERENCE_KEY = "rab_north_ref_pref";

    // constants
    /** Maximum distance from the associated route */
    public static final int DISTANCE_FROM_LEG_MAX = 200; // meters?
    /** Maximum percentage along the associated route (100, duh) */
    public static final float PERCENT_ALONG_LEG_MAX = 100f; // percent

    // drawing preferences preferences
    public static final String STROKE_WIDTH = "dhPrefs_strokeWidth";
    public static final String STROKE_COLOR = "dhPrefs_strokeColor";
    public static final String TEXT_COLOR = "dhPrefs_textColor";
    public static final String SHADE_COLOR = "dhPrefs_shadeColor";

    // scale preference
    public static final String MAX_SCALE_VISIBLE = "dhPrefs_scale_invisible";

    /** The length of one segment of doghouse as drawn by the graphics lib */
    public static final String SIZE_SEGMENT = "dhPrefs_sizeSegment";

    public static final String SHOW_NORTH_REF = "doghouseShowNorthReference";

    private final MapView _mapView;
    private final DefaultMapGroupOverlay _overlay;
    private final MapGroup _doghouseGroup;
    private final DoghouseViewModel _viewModel;

    private static DoghouseReceiver instance;

    private DoghouseReceiver(MapView mapView) {
        _mapView = mapView;

        _doghouseGroup = new DefaultMapGroup(DOGHOUSE_GROUP);
        _doghouseGroup.setMetaBoolean("ignoreOffscreen", true);
        _doghouseGroup.setMetaBoolean("addToObjList", false);

        _overlay = new DefaultMapGroupOverlay(_mapView, _doghouseGroup);
        _mapView.getMapOverlayManager().addOverlay(_overlay);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_ADDED, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_REMOVED, this);
        _mapView.getMapEventDispatcher()
                .removeMapEventListener(MapEvent.ITEM_REFRESH, this);
        _mapView.getMapEventDispatcher()
                .addMapEventListener(MapEvent.ITEM_PERSIST, this);

        // ensure default data are visible
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(_mapView.getContext());

        // when the application is loaded the first time, Doghouse preferences
        // won'd exist, so detect that and set defaults in that case
        if (checkApplyDefaults(prefs)) {
            applyDefaults(prefs);
        }

        prefs.registerOnSharedPreferenceChangeListener(this);

        _viewModel = new DoghouseViewModel(_doghouseGroup);
    }

    public void dispose() {
        _doghouseGroup.clearItems();
        _mapView.getMapOverlayManager().removeOverlay(_overlay);
        _mapView.getMapEventDispatcher().removeMapEventListener(
                MapEvent.ITEM_ADDED, this);
        _mapView.getMapEventDispatcher().removeMapEventListener(
                MapEvent.ITEM_REMOVED, this);
        _mapView.getMapEventDispatcher()
                .removeMapEventListener(MapEvent.ITEM_REFRESH, this);
        _mapView.getMapEventDispatcher()
                .removeMapEventListener(MapEvent.ITEM_PERSIST, this);
        PreferenceManager.getDefaultSharedPreferences(_mapView.getContext())
                .unregisterOnSharedPreferenceChangeListener(this);
        AtakBroadcast.getInstance().unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {

        if (key == null)
            return;

        if (RELATIVE_LOCATION.equals(key)) {
            int c = prefs.getInt(key, -1);
            if (c != -1) {
                Doghouse.DoghouseLocation loc = Doghouse.DoghouseLocation
                        .fromConstant(c);
                _viewModel.setRelativeLocation(loc);
            }
        }
    }

    @NonNull
    public synchronized static DoghouseReceiver newInstance(
            @NonNull MapView mapView) {
        if (instance == null) {
            instance = new DoghouseReceiver(mapView);
        }

        return instance;
    }

    @NonNull
    public synchronized static DoghouseReceiver getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "instance is null. did you forget to call `create()` somewhere?");
        }

        return instance;
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (!(event.getItem() instanceof Route)) {
            return;
        }
        Route route = (Route) event.getItem();
        if (MapEvent.ITEM_ADDED.equals(event.getType())) {
            if (route.getRouteMethod() == Route.RouteMethod.Flying
                    && route.getMetaBoolean(META_SHOW_DOGHOUSES, true)) {
                _viewModel.addDoghouses(route);
            }
            route.addOnVisibleChangedListener(this);
            route.addOnStrokeColorChangedListener(this);
            route.addOnRouteMethodChangedListener(this);
            route.addOnPointsChangedListener(this);
        } else if (MapEvent.ITEM_REMOVED.equals(event.getType())) {
            if (route.getRouteMethod() == Route.RouteMethod.Flying
                    && route.getMetaBoolean(META_SHOW_DOGHOUSES, true)) {
                _viewModel.removeDoghouses(route);
            }
            route.removeOnVisibleChangedListener(this);
            route.removeOnStrokeColorChangedListener(this);
            route.removeOnRouteMethodChangedListener(this);
            route.removeOnPointsChangedListener(this);
        } else if (MapEvent.ITEM_REFRESH.equals(event.getType())) {
            if (route.getRouteMethod() == Route.RouteMethod.Flying
                    && route.getMetaBoolean(META_SHOW_DOGHOUSES, true)) {
                _viewModel.updateDoghouses(route);
            }
        } else if (MapEvent.ITEM_PERSIST.equals(event.getType())) {
            if (route.getRouteMethod() == Route.RouteMethod.Flying
                    && route.getMetaBoolean(META_SHOW_DOGHOUSES, true)) {
                _viewModel.updateDoghouses(route);
            }
        }
    }

    /**
     * When a route's visibility changes, change the doghouse visibility to match.
     * @param item The map item whose visibility changed
     */
    @Override
    public void onVisibleChanged(MapItem item) {
        if (item instanceof Route) {
            List<Doghouse> doghouses = _viewModel
                    .getDoghousesForRoute((Route) item);
            if (doghouses != null) {
                for (Doghouse dh : doghouses) {
                    dh.setVisible(item.getVisible(false));
                }
            }
        }
    }

    /**
     * Change visibility of the doghouse when routes get faded out
     * e.g. their alpha value dips. 50 is an arbitrary threshold
     * @param s the shape
     */
    @Override
    public void onStrokeColorChanged(Shape s) {
        if (s instanceof Route) {
            Route r = (Route) s;
            int alpha = Color.alpha(r.getStrokeColor());
            if (alpha <= 50) {
                if (r.getVisible())
                    _viewModel.hideDoghousesForRoute(r);
            } else {
                if (r.getVisible())
                    _viewModel.showDoghousesForRoute(r);
            }
        }
    }

    @Override
    public void onPointsChanged(Shape s) {
        if (!(s instanceof Route)) {
            return;
        }
        final Route route = (Route) s;
        if (route.getRouteMethod() == Route.RouteMethod.Flying
                && route.getMetaBoolean(META_SHOW_DOGHOUSES, true)) {
            _viewModel.updateDoghouses(route);
        }
    }

    /**
     * If a route's method changes, respond by either adding or removing
     * doghouses for that route depending on it's new type.
     * @param route The route whose method was changed
     */
    @Override
    public void onRouteMethodChanged(Route route) {
        if (route.getRouteMethod() == Route.RouteMethod.Flying
                && route.getMetaBoolean(META_SHOW_DOGHOUSES, true)) {
            _viewModel.addDoghouses(route);
        } else {
            _viewModel.removeDoghouses(route);
        }
    }

    public DoghouseViewModel getDoghouseViewModel() {
        return _viewModel;
    }

    /**
     * Add doghouses for a route given its UID
     * @param uid The UID of the route
     * @see #addDoghousesForRoute(Route)
     */
    public void addDoghousesForRoute(@NonNull String uid) {
        final Route route = getRouteWithUid(uid);
        if (route != null) {
            addDoghousesForRoute(route);
        }
    }

    /**
     * Add doghouses for a Route.
     * @param route The route
     */
    public void addDoghousesForRoute(@NonNull
    final Route route) {
        if (route.getMetaBoolean(META_SHOW_DOGHOUSES, true)) {
            _viewModel.addDoghouses(route);
        }
    }

    /**
     * Get the doghouse for a route at the specified point within the route
     * @param index The index of the route point
     * @param route The route
     * @return The doghouse at <emph>index</emph> or <code>null</code>
     */
    @Nullable
    public Doghouse getDoghouseForRoute(int index, @NonNull
    final Route route) {
        return _viewModel.getDoghouse(index, route);
    }

    /**
     * Get doghouses for this route if there are any.
     * @param route The route
     * @return A list of doghouses for this route or <code>null</code>
     */
    @Nullable
    public List<Doghouse> getDoghousesForRoute(@NonNull
    final Route route) {
        return _viewModel.getDoghousesForRoute(route);
    }

    /**
     * Remove doghouses for a route given its UID.
     * @param uid The UID of the route.
     * @see #removeDoghousesForRoute(Route)
     */
    public void removeDoghousesForRoute(@NonNull
    final String uid) {
        final Route route = getRouteWithUid(uid);
        if (route != null) {
            removeDoghousesForRoute(route);
        }
    }

    /**
     * Remove doghouses for a particular Route.
     * @param route The Route
     */
    public void removeDoghousesForRoute(@NonNull
    final Route route) {
        _viewModel.removeDoghouses(route);
    }

    /**
     * Get a route from the map with the given UID.
     * @param uid The UID
     * @return The route with that UID or <code>null</code>
     */
    @Nullable
    static Route getRouteWithUid(String uid) {
        final MapItem mi = RouteMapReceiver.getInstance()
                .getRouteGroup()
                .deepFindUID(uid);

        if (!(mi instanceof Route)) {
            return null;
        }

        return (Route) mi;
    }

    /**
     * The first time the application is loaded, doghouse preferences
     * won't be a part of the preferences file. Detect this so that
     * defaults can be set.
     * @param prefs The core SharedPreferences
     * @return true if default values should be set (the
     *              doghouse keys are not in the map); false otherwise.
     */
    private boolean checkApplyDefaults(SharedPreferences prefs) {
        String test = prefs.getString("dh_data_row_0", null);
        return test == null;
    }

    /**
     * On the first start-up, apply default preference values for
     * doghouses.
     * @param prefs The core SharedPreferences
     */
    private void applyDefaults(SharedPreferences prefs) {
        prefs.edit()
                .putString("dh_data_row_0",
                        Doghouse.DoghouseFields.TURNPOINT_ID.toString())
                .putString("dh_data_row_1",
                        Doghouse.DoghouseFields.BEARING_TO_NEXT.toString())
                .putString("dh_data_row_2",
                        Doghouse.DoghouseFields.DISTANCE_TO_NEXT.toString())
                .putInt(RELATIVE_LOCATION,
                        Doghouse.DoghouseLocation.OUTSIDE_OF_TURN.getConstant())
                .putInt(DISTANCE_FROM_LEG, DISTANCE_FROM_LEG_MAX / 2)
                .putFloat(PERCENT_ALONG_LEG,
                        (PERCENT_ALONG_LEG_MAX / 2) / PERCENT_ALONG_LEG_MAX)
                .apply();
    }

}
