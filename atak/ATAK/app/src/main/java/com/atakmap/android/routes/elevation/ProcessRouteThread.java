
package com.atakmap.android.routes.elevation;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Pair;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.elevation.model.ControlPointData;
import com.atakmap.android.routes.elevation.model.RouteCache;
import com.atakmap.android.routes.elevation.model.RouteData;
import com.atakmap.android.routes.elevation.model.UnitConverter;
import com.atakmap.android.routes.elevation.service.AnalyticsElevationService;
import com.atakmap.android.routes.elevation.service.RouteElevationService;
import com.atakmap.android.util.LimitingThread;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.math.MathUtils;

import org.apache.commons.lang.ArrayUtils;

import java.util.Vector;

public class ProcessRouteThread implements Runnable,
        SharedPreferences.OnSharedPreferenceChangeListener {

    public final static String TAG = "ProcessRouteThread";
    private static final int REFRESH_INTERVAL = 2000;

    private final MapView mapView;
    private RouteData result;
    private Route route;
    private RouteElevationView routeView;
    private AnalysisPanelPresenter analysisPanelPresenter;
    private RouteElevationPresenter routeElevationPresenter;
    private SelfPresenter selfPresenter;
    private Boolean bInterpolateElevations;

    private final SharedPreferences _prefs;

    private GeoPoint[] _controlPoints;
    private String[] _controlNames;

    private boolean disposed = false;

    private final LimitingThread lThread = new LimitingThread(
            "RouteElevationProcess", this);

    public ProcessRouteThread(MapView mapView) {
        this.mapView = mapView;
        _prefs = PreferenceManager.getDefaultSharedPreferences(
                mapView.getContext());
        _prefs.registerOnSharedPreferenceChangeListener(this);
    }

    public synchronized void start(Route route,
            RouteElevationView routeElevationView,
            AnalysisPanelPresenter analysisPresenter,
            RouteElevationPresenter routePresenter,
            SelfPresenter selfPresenter, Boolean bInterpolateElevations) {
        if (this.disposed)
            return;
        this.route = route;
        this.routeView = routeElevationView;
        this.analysisPanelPresenter = analysisPresenter;
        this.routeElevationPresenter = routePresenter;
        this.selfPresenter = selfPresenter;
        this.bInterpolateElevations = bInterpolateElevations;
        lThread.exec();
    }

    @Override
    public void run() {
        Route route;
        boolean interp;
        synchronized (this) {
            if (this.disposed)
                return;
            route = this.route;
            interp = _prefs.getBoolean("elevProfileInterpolateAlt", true);
            if (this.bInterpolateElevations != null)
                interp = this.bInterpolateElevations;
        }

        _updateControlPoints();
        // Initialize and show ProgressDialog
        GeoPointMetaData[] tempRoute = route.getMetaDataPoints();
        // Check to see if it's already been cached
        RouteData cached = RouteCache.getInstance().retrieve(route.getTitle());
        if (cached != null
                // Check if interpolation changed
                && cached.getInterpolatedAltitudes() == interp
                // Check if geoPoint points moved
                && (cached.getUnexpandedGeoPoints() != null && ArrayUtils
                        .isEquals(
                                cached.getUnexpandedGeoPoints(), tempRoute))
                // Check if control points moved
                && ArrayUtils.isEquals(cached.getControlPointData()
                        .getGeoPoints(), _controlPoints)
                // Check if point names changed
                && ArrayUtils.isEquals(cached.getControlPointData().getNames(),
                        _controlNames)) {
            this.result = cached;
        } else {
            // Need to create RouteData instance then cache
            RouteCache.getInstance().invalidate(route.getTitle());
            // TRK: do we need to synchronize on a lock here?

            // Start expanding route
            RouteData result = RouteElevationService.expandRoute(tempRoute,
                    RouteElevationService.computeRelativeFrequency(route
                            .getTotalDistance()),
                    interp);

            ControlPointData controlPointData = new ControlPointData();
            controlPointData.setGeoPoints(_controlPoints);
            controlPointData.setDistances(RouteElevationService
                    .findControlPoints(result,
                            _controlPoints));
            controlPointData.setIndices(RouteElevationService
                    .getControlPointIndices(result,
                            _controlPoints));
            controlPointData.setNames(_controlNames);

            result.setControlPointData(controlPointData);

            // compress dataset
            result = RouteElevationService.compressDataset(result);

            Pair<GeoPointMetaData, GeoPointMetaData> minmax = AnalyticsElevationService
                    .findMinMax(result
                            .getGeoPoints());

            result.setMinAlt(minmax.first);
            result.setMaxAlt(minmax.second);

            result.setMaxSlope(AnalyticsElevationService.findRouteMaximumSlope(
                    result.getDistances(), result.getGeoPoints()));
            result.setTotalGain(AnalyticsElevationService
                    .findRouteTotalElevation(result, true, -1));
            result.setTotalLoss(AnalyticsElevationService
                    .findRouteTotalElevation(result, false, -1));

            RouteCache.getInstance().cache(route.getTitle(), result);
            this.result = result;
        }

        display(route, result);

        try {
            Thread.sleep(REFRESH_INTERVAL);
        } catch (InterruptedException ignore) {
        }
    }

    public synchronized void dispose() {
        if (!disposed) {
            disposed = true;
            _prefs.unregisterOnSharedPreferenceChangeListener(this);
            selfPresenter.stop();
            lThread.dispose(false);
        }
    }

    public synchronized boolean isDisposed() {
        return this.disposed;
    }

    /*
     * This method displays the RouteData object by invoking the necessary UI constructs on the UI
     * Thread
     */
    private synchronized void display(final Route r, final RouteData result) {
        if (disposed || r != this.route)
            return;
        selfPresenter.start(r.getTitle(), result.getGeoPoints(),
                result.getDistances());
        mapView.post(new Runnable() {
            @Override
            public void run() {
                synchronized (this) {
                    if (disposed || r != route)
                        return;
                    try {
                        _updateAnalysisPanelPresenter();
                        double[] dists = result.getDistances();
                        result.setTotalDistance(dists.length > 0
                                ? dists[dists.length - 1]
                                : 0);
                        int index = -1;
                        RouteElevationChart chart = routeView.getChart();
                        if (chart != null)
                            index = chart.getClosestIndexForX(
                                    chart.getRealPosition());
                        index = MathUtils.clamp(index, 0, dists.length - 1);
                        routeElevationPresenter.updateChart(r, result);
                        routeElevationPresenter.update(index,
                                dists.length > 0 ? dists[index] : 0,
                                result.getGeoPoints()[index].get()
                                        .getAltitude(),
                                true, false);
                        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                                "com.atakmap.android.maps.ROUTE_PROCESSING_DONE"));
                    } catch (Exception e) {
                        Log.d(TAG,
                                "error occurred, route might have been modified externally while viewing",
                                e);
                        dispose();
                    }
                }
            }
        });
    }

    private void _updateAnalysisPanelPresenter() {
        analysisPanelPresenter.updateMaxAlt(result.getMaxAlt());
        analysisPanelPresenter.updateMinAlt(result.getMinAlt());
        analysisPanelPresenter.updateTotalGain(result
                .getTotalGain());
        analysisPanelPresenter.updateTotalLoss(result
                .getTotalLoss());
        analysisPanelPresenter.updateMaxSlope(result.getMaxSlope());

        analysisPanelPresenter.updateTotalDist(UnitConverter.Meter
                .toFeet(route.getTotalDistance()));
    }

    private void _updateControlPoints() {
        Vector<GeoPoint> temp = new Vector<>();
        Vector<String> tvname = new Vector<>();
        PointMapItem marker;
        for (int i = 0; i < route.getNumPoints(); i++) {
            marker = route.getMarker(i);
            if (marker != null) {
                temp.add(route.getPoint(i).get());
                tvname.add(marker.getMetaString("callsign", null));
            }
        }
        _controlPoints = temp.toArray(new GeoPoint[0]);
        _controlNames = tvname.toArray(new String[0]);
    }

    @Override
    public synchronized void onSharedPreferenceChanged(SharedPreferences p,
            String key) {
        if (key == null)
            return;

        if (key.equals("rab_rng_units_pref")
                || key.equals("alt_unit_pref")
                || key.equals("alt_display_pref")
                || key.equals("alt_display_agl")) {
            _updateAnalysisPanelPresenter();
            routeElevationPresenter.invalidate();
            routeElevationPresenter.updateChart(route, result);
        }
    }
}
