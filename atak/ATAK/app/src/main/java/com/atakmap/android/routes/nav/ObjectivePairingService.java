
package com.atakmap.android.routes.nav;

import android.util.Pair;

import com.atakmap.android.maps.Association;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.RouteNavigationManager;
import com.atakmap.android.routes.RouteNavigator;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.UUID;

/**
 * This class is responsible for drawing a line from the user to their current objective.
 */

public class ObjectivePairingService
        implements RouteNavigator.RouteNavigatorListener,
        RouteNavigationManager.RouteNavigationManagerEventListener {

    /*******************************************************************************
     * Fields and Properties
     *******************************************************************************/
    private Association navAssociation = null;
    private MapGroup navGroup = null;
    private RouteNavigator navigator;
    private Marker destinationMarker = null;

    /*******************************************************************************
     * Methods
     *******************************************************************************/

    private void addOrUpdateDestinationMarker(final GeoPoint point) {
        if (destinationMarker == null) {
            destinationMarker = new Marker(point, UUID.randomUUID().toString());
            destinationMarker.setType("b-temp"); // TODO: what's the right type for
            // purely internal things?
            destinationMarker.setMetaBoolean("drag", false);
            destinationMarker.setMetaBoolean("editable", false);
            destinationMarker.setMetaBoolean("addToObjList", false); // always hide these in
            // overlays
            destinationMarker.setMetaString("how", "h-g-i-g-o"); // don't autostale it
            destinationMarker.setVisible(false);
            destinationMarker.setClickable(false);
            navGroup.addItem(destinationMarker);
        } else {
            destinationMarker.setPoint(point);
        }
    }

    /*******************************************************************************
     * RouteNavigatorListener Interface Implementation
     *******************************************************************************/

    @Override
    public void onNavigationStarting(RouteNavigator navigator) {

    }

    @Override
    public void onNavigationStarted(RouteNavigator navigator, Route route) {
        navigator.getNavManager().registerListener(this);
        this.navigator = navigator;
        this.navGroup = navigator.getNavGroup();

        Pair<Integer, PointMapItem> currentObjective = navigator
                .getNavManager()
                .getCurrentObjective();

        if (currentObjective.second != null) {
            onNavigationObjectiveChanged(navigator.getNavManager(),
                    currentObjective.second, true);
        }
    }

    @Override
    public void onNavigationStopping(RouteNavigator navigator, Route route) {
        navigator.getNavManager().unregisterListener(this);

        if (navAssociation != null && navGroup != null) {
            navGroup.removeItem(destinationMarker);
            navGroup.removeItem(navAssociation);
            navAssociation = null;
            destinationMarker = null;
        }
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

        addOrUpdateDestinationMarker(newObjective.getPoint());

        if (navAssociation == null) {
            navAssociation = new Association(UUID.randomUUID().toString());
            navAssociation.setFirstItem(navigator.getMapView().getSelfMarker());
            navAssociation.setZOrder(-80000d);
            navAssociation.setSecondItem(destinationMarker);
            navAssociation.setColor(0x7700FF00);
            navAssociation.setStyle(Association.STYLE_SOLID);
            navAssociation.setLink(Association.LINK_LINE);
            navAssociation.setStrokeWeight(2);
            navAssociation.setClickable(false);
            navGroup.addItem(navAssociation);
        }

        navAssociation.setSecondItem(destinationMarker);
    }

    @Override
    public void onOffRoute(RouteNavigationManager routeNavigationManager) {

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

    }
}
