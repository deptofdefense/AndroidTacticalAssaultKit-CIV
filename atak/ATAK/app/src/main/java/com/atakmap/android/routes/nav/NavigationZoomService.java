
package com.atakmap.android.routes.nav;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.RouteNavigationManager;
import com.atakmap.android.routes.RouteNavigator;
import com.atakmap.coremap.maps.coords.GeoPoint;

/**
 * Manages zooming in and out throughout the course of navigation.
 */

public class NavigationZoomService implements
        RouteNavigator.RouteNavigatorListener,
        RouteNavigationManager.RouteNavigationManagerEventListener {

    /*******************************************************************************
     * Fields and Properties
     *******************************************************************************/

    private final double desiredZoom = 1.120997868311173E-4;
    private double maneuverDesiredZoom = 3.600922593220702E-4;
    private double cachedZoomLevel = 0;
    private RouteNavigator routeNavigator;
    private SharedPreferences preferences;

    /*******************************************************************************
     * RouteNavigatorListener Interface Implementation
     *******************************************************************************/

    private void zoomToIfDesired(double zoom) {
        if (preferences.getBoolean(
                SceneSettingService.PREF_TRADITIONAL_NAV_MODE,
                SceneSettingService.TRADITIONAL_NAV_MODE_DEFAULT)) {
            routeNavigator.getMapView().getMapController().zoomTo(zoom, true);
        }
    }

    @Override
    public void onNavigationStarting(RouteNavigator navigator) {
        if (preferences == null) {
            preferences = PreferenceManager
                    .getDefaultSharedPreferences(navigator.getMapView()
                            .getContext());
        }

        routeNavigator = navigator;
    }

    @Override
    public void onNavigationStarted(RouteNavigator navigator, Route route) {
        cachedZoomLevel = navigator.getMapView().getMapScale();
        navigator.getNavManager().registerListener(this);

        zoomToIfDesired(desiredZoom);
    }

    @Override
    public void onNavigationStopping(RouteNavigator navigator, Route route) {
        navigator.getNavManager().unregisterListener(this);

        zoomToIfDesired(cachedZoomLevel);
    }

    @Override
    public void onNavigationStopped(RouteNavigator navigator) {

    }

    /*******************************************************************************
     * RouteNavigatorListener Interface Implementation
     *******************************************************************************/

    @Override
    public void onGpsStatusChanged(
            RouteNavigationManager routeNavigationManager, boolean found) {
    }

    @Override
    public void onLocationChanged(
            RouteNavigationManager routeNavigationManager,
            GeoPoint oldLocation, GeoPoint newLocation) {

    }

    @Override
    public void onNavigationObjectiveChanged(
            RouteNavigationManager routeNavigationManager,
            PointMapItem newObjective, boolean isFromRouteProgression) {

    }

    @Override
    public void onOffRoute(RouteNavigationManager routeNavigationManager) {
        //zoomToIfDesired(desiredZoom);
    }

    @Override
    public void onReturnedToRoute(
            RouteNavigationManager routeNavigationManager) {

    }

    @Override
    public void onTriggerEntered(RouteNavigationManager routeNavigationManager,
            PointMapItem item, int triggerIndex) {

    }

    @Override
    public void onArrivedAtPoint(RouteNavigationManager routeNavigationManager,
            PointMapItem item) {

    }

    @Override
    public void onDepartedPoint(RouteNavigationManager routeNavigationManager,
            PointMapItem item) {
        //zoomToIfDesired(desiredZoom);
    }
}
