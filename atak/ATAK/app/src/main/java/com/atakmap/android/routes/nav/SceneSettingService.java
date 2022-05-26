
package com.atakmap.android.routes.nav;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapMode;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.RouteMapReceiver;
import com.atakmap.android.routes.RouteNavigator;
import com.atakmap.android.user.CamLockerReceiver;

/**
 * Handles the dimming of routes and the setting of orientation
 */

public class SceneSettingService implements
        RouteNavigator.RouteNavigatorListener {

    /*******************************************************************************
     * Fields and Properties
     *******************************************************************************/

    public final static String PREF_TRADITIONAL_NAV_MODE = "route_track_up_locked_on";
    public final static boolean TRADITIONAL_NAV_MODE_DEFAULT = true;

    private SharedPreferences preferences;
    private double cachedZOrder;
    private MapMode cachedMapMode;
    private boolean cachedNavVisible;

    /*******************************************************************************
     * Methods
     *******************************************************************************/

    /**
     *  Dim or Undim all routes, except for the current one being nagivated.
     * @param state to dim or undim the route.
     */
    private void dimRoutes(Route route, boolean state) {
        // somehow the current route is null, so do not dim any routes
        if (route == null)
            return;

        RouteMapReceiver routeMapReceiver = RouteMapReceiver.getInstance();
        for (Route r : routeMapReceiver.getCompleteRoutes()) {
            if (state) {
                if (!r.getUID().equals(route.getUID())) {
                    r.setAlpha(50);
                    r.setClickable(false);
                    r.hideLabels(true);
                } else {
                    r.setLocked(true);
                    cachedZOrder = r.getZOrder();
                    r.setZOrder(cachedZOrder - 50000);
                    r.setNavigating(true);
                }
            } else {
                r.resetAlpha();
                r.setClickable(true);
                r.hideLabels(false);
                if (r.getUID().equals(route.getUID())) {
                    r.setLocked(false);
                    r.setZOrder(cachedZOrder);
                    r.setNavigating(false);
                }

            }

        }
    }

    private void setLockActive(String markerUID, boolean state) {
        Intent intent = new Intent();
        if (state) {
            intent.setAction(CamLockerReceiver.LOCK_CAM);
        } else {
            intent.setAction(CamLockerReceiver.UNLOCK_CAM);
        }

        intent.putExtra("uid", markerUID);
        AtakBroadcast.getInstance().sendBroadcast(intent);
    }

    private void setOrientationState(MapMode state) {
        Intent orientationIntent = new Intent();
        orientationIntent.setAction(state.getIntent());
        AtakBroadcast.getInstance().sendBroadcast(orientationIntent);
    }

    /*******************************************************************************
     * RouteNavigatorListener Interface Implementation
     *******************************************************************************/

    @Override
    public void onNavigationStarting(RouteNavigator navigator) {
        if (preferences == null) {
            preferences = PreferenceManager
                    .getDefaultSharedPreferences(navigator.getMapView()
                            .getContext());
        }
    }

    @Override
    public void onNavigationStarted(RouteNavigator navigator, Route route) {
        dimRoutes(route, true);

        cachedNavVisible = NavView.getInstance().buttonsVisible();
        NavView.getInstance().toggleButtons(false);

        boolean trackAndLock = preferences.getBoolean(PREF_TRADITIONAL_NAV_MODE,
                TRADITIONAL_NAV_MODE_DEFAULT);

        if (trackAndLock) {
            String selfUID = navigator.getMapView().getSelfMarker().getUID();
            setLockActive(selfUID, true);

            cachedMapMode = NavView.getInstance().getMapMode();
            setOrientationState(MapMode.TRACK_UP);
        }
    }

    @Override
    public void onNavigationStopping(RouteNavigator navigator, Route route) {
        dimRoutes(route, false);

        if (!NavView.getInstance().buttonsVisible())
            NavView.getInstance().toggleButtons(cachedNavVisible);

        boolean trackAndLock = preferences.getBoolean(PREF_TRADITIONAL_NAV_MODE,
                TRADITIONAL_NAV_MODE_DEFAULT);

        if (trackAndLock) {
            String selfUID = navigator.getMapView().getSelfMarker().getUID();
            setLockActive(selfUID, false);
            if (cachedMapMode != null)
                setOrientationState(cachedMapMode);

        }
    }

    @Override
    public void onNavigationStopped(RouteNavigator navigator) {
        //Nothing to do here
    }
}
