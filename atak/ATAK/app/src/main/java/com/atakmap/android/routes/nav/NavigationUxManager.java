
package com.atakmap.android.routes.nav;

import com.atakmap.android.routes.RouteNavigator;

/**
 * Manages the User Experience for navigation.  It is responsible for ensuring the UI is shown
 * and cleaned up at the appropriate times, that the cues are spoken as they should be, and any
 * other User Experience related tasks are handled.
 */

public final class NavigationUxManager {

    /*******************************************************************************
     * Fields and Properties
     *******************************************************************************/
    private final RouteNavigator routeNavigator;

    private static NavigationUxManager instance;

    public static NavigationUxManager getInstance() {
        return instance;
    }

    private RouteNavigator.RouteNavigatorListener objectivePairingService = null;

    public RouteNavigator.RouteNavigatorListener getObjectivePairingService() {
        return objectivePairingService;
    }

    public void setObjectivePairingService(
            RouteNavigator.RouteNavigatorListener objectivePairingService) {
        if (this.objectivePairingService != null) {
            routeNavigator.unregisterRouteNavigatorListener(
                    this.objectivePairingService);
        }

        this.objectivePairingService = objectivePairingService;

        if (objectivePairingService != null)
            routeNavigator
                    .registerRouteNavigatorListener(objectivePairingService);
    }

    private RouteNavigator.RouteNavigatorListener navigationFeedbackService = null;

    public RouteNavigator.RouteNavigatorListener getNavigationFeedbackService() {
        return navigationFeedbackService;
    }

    public void setNavigationFeedbackService(
            RouteNavigator.RouteNavigatorListener navigationFeedbackService) {
        if (this.navigationFeedbackService != null) {
            routeNavigator.unregisterRouteNavigatorListener(
                    this.navigationFeedbackService);
        }

        this.navigationFeedbackService = navigationFeedbackService;

        if (navigationFeedbackService != null)
            routeNavigator
                    .registerRouteNavigatorListener(navigationFeedbackService);
    }

    private RouteNavigator.RouteNavigatorListener navigationZoomService = null;

    public RouteNavigator.RouteNavigatorListener getNavigationZoomService() {
        return navigationZoomService;
    }

    public void setNavigationZoomService(
            RouteNavigator.RouteNavigatorListener navigationZoomService) {
        if (this.navigationZoomService != null) {
            routeNavigator.unregisterRouteNavigatorListener(
                    this.navigationZoomService);
        }

        this.navigationZoomService = navigationZoomService;

        if (navigationZoomService != null)
            routeNavigator
                    .registerRouteNavigatorListener(navigationZoomService);
    }

    private RouteNavigator.RouteNavigatorListener sceneSettingService = null;

    public RouteNavigator.RouteNavigatorListener getSceneSettingService() {
        return sceneSettingService;
    }

    public void setSceneSettingService(
            RouteNavigator.RouteNavigatorListener sceneSettingService) {
        if (this.sceneSettingService != null) {
            routeNavigator
                    .unregisterRouteNavigatorListener(this.sceneSettingService);
        }

        this.sceneSettingService = sceneSettingService;

        if (sceneSettingService != null)
            routeNavigator.registerRouteNavigatorListener(sceneSettingService);
    }

    /*******************************************************************************
     * Ctor
     *******************************************************************************/

    public NavigationUxManager(RouteNavigator navigator) {
        this.routeNavigator = navigator;
        instance = this;

        setNavigationFeedbackService(getDefaultNavigationFeedbackService());
        setNavigationZoomService(getDefaultNavigationZoomService());
        setObjectivePairingService(getDefaultObjectivePairingService());
        setSceneSettingService(getDefaultSceneSettingService());
    }

    /*******************************************************************************
     * Methods
     *******************************************************************************/

    //Default Providers

    public RouteNavigator.RouteNavigatorListener getDefaultObjectivePairingService() {
        return new ObjectivePairingService();
    }

    public RouteNavigator.RouteNavigatorListener getDefaultNavigationFeedbackService() {
        return new NavigationFeedbackService();
    }

    public RouteNavigator.RouteNavigatorListener getDefaultNavigationZoomService() {
        return new NavigationZoomService();
    }

    public RouteNavigator.RouteNavigatorListener getDefaultSceneSettingService() {
        return new SceneSettingService();
    }

}
