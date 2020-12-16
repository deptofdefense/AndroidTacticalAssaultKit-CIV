
package com.atakmap.android.helloworld;

import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.RouteNavigationManager;
import com.atakmap.android.routes.RouteNavigator;
import com.atakmap.coremap.maps.coords.GeoPoint;

/**
 * Copyright (c) PAR Government Systems Corporation. All rights reserved.
 * Created by Joe Austin on 6/12/18.
 */
public class RouteEventListener
        implements RouteNavigator.RouteNavigatorListener,
        RouteNavigationManager.RouteNavigationManagerEventListener {

    /*******************************************************************************
     * RouteNavigatorListener Implementation
     *******************************************************************************/

    @Override
    public void onNavigationStarting(RouteNavigator navigator) {

    }

    @Override
    public void onNavigationStarted(RouteNavigator navigator, Route route) {
        showToast("Navigation started");
        navigator.getNavManager().registerListener(this);
    }

    @Override
    public void onNavigationStopping(RouteNavigator navigator, Route route) {
        navigator.getNavManager().unregisterListener(this);
    }

    @Override
    public void onNavigationStopped(RouteNavigator navigator) {
        showToast("Navigation stopped");
    }

    /*******************************************************************************
     * RouteNavigationManagerEventListener Implementation
     *******************************************************************************/

    @Override
    public void onGpsStatusChanged(RouteNavigationManager rnm, boolean state) {
    }

    @Override
    public void onLocationChanged(RouteNavigationManager routeNavigationManager,
            GeoPoint oldLocation, GeoPoint newLocation) {
        if (!newLocation.equals(oldLocation)) {
            showToast("Location changed");
        }
    }

    @Override
    public void onNavigationObjectiveChanged(
            RouteNavigationManager routeNavigationManager,
            PointMapItem newObjective, boolean isFromRouteProgression) {
        showToast("Navigation Objective Changed");
    }

    @Override
    public void onOffRoute(RouteNavigationManager routeNavigationManager) {
        showToast("Off Route");
    }

    @Override
    public void onReturnedToRoute(
            RouteNavigationManager routeNavigationManager) {
        showToast("Back on Route.");
    }

    @Override
    public void onTriggerEntered(RouteNavigationManager routeNavigationManager,
            PointMapItem item, int triggerIndex) {
        showToast("Entered trigger");
    }

    @Override
    public void onArrivedAtPoint(RouteNavigationManager routeNavigationManager,
            PointMapItem item) {
        showToast("Arrived at point");
    }

    @Override
    public void onDepartedPoint(RouteNavigationManager routeNavigationManager,
            PointMapItem item) {
        showToast("Departed point");
    }

    /*******************************************************************************
     * Misc. Helper Methods
     *******************************************************************************/

    private void showToast(final String msg) {
        MapView.getMapView().post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MapView.getMapView().getContext(), msg,
                        Toast.LENGTH_SHORT).show();
            }
        });

    }
}
