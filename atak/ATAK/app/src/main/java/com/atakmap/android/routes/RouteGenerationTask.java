
package com.atakmap.android.routes;

import android.content.SharedPreferences;
import android.os.AsyncTask;

import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.List;

/**
 * Abstract class for generating routes. This class provides the basic infrastructure for
 * registering event listeners in the form of {@link RouteGenerationEventListener} instances with
 * its subclasses.
 */
public abstract class RouteGenerationTask extends
        AsyncTask<RouteGenerationPackage, Double, RoutePointPackage> {

    public static final String TAG = "RouteGenerationTask";

    private Exception backgroundException = null;

    private boolean alertOnCreation = true;

    private final RouteGenerationEventListener listener;

    public RouteGenerationEventListener getListener() {
        return listener;
    }

    public RouteGenerationTask(RouteGenerationEventListener listener) {
        this.listener = listener;
    }

    /** Sets whether or not a dialog box will be displayed when the route is calculated.
     *  Default value is "true" */
    public void setAlertOnCreation(boolean value) {
        alertOnCreation = value;
    }

    @Override
    protected void onPreExecute() {
        if (listener != null) {
            listener.onBeforeRouteGenerated(this, alertOnCreation);
        }
    }

    @Override
    protected void onPostExecute(RoutePointPackage routePointPackage) {
        if (listener != null) {

            if (backgroundException != null) {
                listener.onException(backgroundException);
            }

            listener.onAfterRouteGenerated(routePointPackage);
        }
    }

    @Override
    protected void onProgressUpdate(Double... values) {
        if (listener != null) {
            listener.onProgressUpdated(values[0]);
        }
    }

    @Override
    protected void onCancelled() {
        if (listener != null) {
            listener.onCancelled();
        }
    }

    @Override
    protected RoutePointPackage doInBackground(
            RouteGenerationPackage... routeGenerationPackages) {
        if (routeGenerationPackages == null
                || routeGenerationPackages.length != 1) {
            throw new IllegalArgumentException(
                    "Must provide one and only one route package");
        }

        RouteGenerationPackage rtGenPackage = routeGenerationPackages[0];

        try {
            RoutePointPackage routePackage = generateRoute(
                    rtGenPackage.getPreferences(),
                    rtGenPackage.getStartPoint(), rtGenPackage.getEndPoint(),
                    rtGenPackage.getByWayOf());

            if (listener != null) {
                listener.onRouteGenerated(routePackage);
            }

            return routePackage;

        } catch (Exception ex) {
            backgroundException = ex;

            return null;
        }
    }

    public abstract RoutePointPackage generateRoute(SharedPreferences prefs,
            GeoPoint origin, GeoPoint dest, List<GeoPoint> byWayOf);

    /**
     * Interface for signaling events and progress updates.
     */
    public interface RouteGenerationEventListener {

        /**
         * Fires when an exception has been thrown.
         * (NOTE:  Will run on the UI thread).
         * @param ex The exception that was thrown.
         */
        void onException(Exception ex);

        /**
         * Fires when there is progress to report (NOTE:  Will run on the UI thread).
         * @param progress A value from 0 to 1 representing the progress.
         */
        void onProgressUpdated(double progress);

        /**
         * Fires before the task is executed (NOTE:  Will run on the UI thread)
         * @param task The associated task.
         * @param displayDialog Specify whether or not to alert the user that
         *                      a new route is being generated.
         */
        void onBeforeRouteGenerated(RouteGenerationTask task,
                boolean displayDialog);

        /**
         * Fires immediately after the route is generated (NOTE:  Will run on the BACKGROUND thread).
         * @param routePointPackage The routePointPackage that was generated.
         */
        void onRouteGenerated(RoutePointPackage routePointPackage);

        /**
         * Fires after the route has been returned (after onRouteGenerated) and has not been cancelled.
         * (NOTE:  Will run on the UI thread)
         * @param routePointPackage
         */
        void onAfterRouteGenerated(RoutePointPackage routePointPackage);

        /**
         * Fires after the task has been cancelled.  Note: in most cases, the onRouteGenerated event will still fire.
         * (NOTE:  Will run on the UI thread)
         */
        void onCancelled();
    }

}
