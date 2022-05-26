
package com.atakmap.android.routes.elevation;

import android.content.SharedPreferences;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.routes.elevation.model.UnitConverter;
import com.atakmap.android.routes.elevation.service.RouteElevationService;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.conversion.EGM96;

import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.elevation.ElevationManager;

import org.achartengine.model.XYSeries;

public class SelfPresenter {
    private final static int _MINIMUN_METER_RANGE = 100;
    private final static int DELTA_RECOMPUTE_METERS = 10;
    private final static int DELTA_RECOMPUTE_MS = 2000;

    private final SharedPreferences _prefs;
    private RouteElevationView _routeElevationView;
    private GeoPointMetaData[] _geoPoint = new GeoPointMetaData[0];
    private double[] _distance = new double[] {};
    boolean _running;
    public static final String TAG = "SelfPresenter";
    private String title;
    private MapView _mapView;

    Thread processThread;

    public SelfPresenter(SharedPreferences prefs) {
        _prefs = prefs;
    }

    public void bind(final RouteElevationView routeElevationView,
            final MapView mapView) {
        this._routeElevationView = routeElevationView;
        this._mapView = mapView;
    }

    synchronized public void start(final String routeTitle,
            GeoPointMetaData[] geoPoints,
            double[] distances) {

        // There can be only one processing.
        stop();

        title = routeTitle;

        Log.d(TAG, "starting processing the route information for: " + title);
        synchronized (this) {
            this._distance = distances;
            this._geoPoint = geoPoints;
            _running = true;
            processThread = new Thread("selfpresenter-thread-" + title) {
                @Override
                public void run() {
                    process();
                }
            };
            processThread.start();
        }
    }

    public void stop() {
        synchronized (this) {
            if (processThread == null)
                return;

            _running = false;
            Log.d(TAG, "stopping processing the route information for: "
                    + title);
            processThread.interrupt();
            processThread = null;
            _geoPoint = new GeoPointMetaData[] {};
        }

    }

    /**
     * Will recompute when the last selfMarker distance is greater than the DELTA_RECOMPUTE_METERS, poling once every second.
     */
    public void process() {

        GeoPoint lastSelfPos = null;
        PointMapItem self = null;

        while (_running) {

            if (self == null) {
                self = ATAKUtilities.findSelf(_mapView);
            }

            final boolean recompute = self != null
                    && _geoPoint.length > 0
                    &&
                    (lastSelfPos == null || (lastSelfPos.distanceTo(self
                            .getPoint()) > DELTA_RECOMPUTE_METERS));

            if (recompute) {

                // limit the recomputes based on your current self point.
                lastSelfPos = self.getPoint();

                double minDist = Double.MAX_VALUE;
                GeoPointMetaData selfPoint = null;
                double pointDistance = 0;
                synchronized (SelfPresenter.this) {
                    for (int i = 0; i < _geoPoint.length - 1; i++) {
                        GeoPoint source = _geoPoint[i].get();
                        GeoPoint target = _geoPoint[i + 1].get();
                        GeoPoint reference = self.getPoint();
                        GeoPointMetaData p = RouteElevationService
                                .closestSegmentPoint(
                                        source, target,
                                        reference);
                        if (p == null)
                            continue;
                        final double dist = GeoCalculations.distanceTo(p.get(),
                                self.getPoint());
                        if (dist < minDist && dist < _MINIMUN_METER_RANGE) {
                            minDist = dist;
                            pointDistance = UnitConverter.Meter
                                    .toFeet(_distance[i] + GeoCalculations
                                            .distanceTo(_geoPoint[i].get(),
                                                    p.get()));
                            selfPoint = p;
                        }
                    }
                }

                if (_routeElevationView.getChart() != null
                        && _routeElevationView.getChart()
                                .getImageDataset() != null
                        && _routeElevationView.getChart().getDataset() != null
                        && _routeElevationView.getChart().getDataset()
                                .getSeriesCount() > 0) {
                    final XYSeries selfPointSeries = _routeElevationView
                            .getChart()
                            .getImageDataset().getSeriesByTitle("self point");

                    if (selfPoint != null && minDist != Double.MAX_VALUE) {
                        Span rangeUnits = _routeElevationView.getRangeUnits();
                        double fpDistance = SpanUtilities.convert(
                                pointDistance, Span.FOOT, rangeUnits);

                        Span altUnits = _routeElevationView.getAltUnits();
                        String altRef = _prefs.getString(
                                "alt_display_pref", "MSL");

                        if (!selfPoint.get().isAltitudeValid())
                            selfPoint = ElevationManager.getElevationMetadata(
                                    selfPoint.get());

                        double alt = selfPoint.get().getAltitude();
                        if (altRef.equals("MSL"))
                            alt = EGM96.getMSL(selfPoint.get());

                        double fpElevation = selfPoint.get().isAltitudeValid()
                                ? SpanUtilities
                                        .convert(alt, Span.METER, altUnits)
                                : 0;
                        selfPointSeries.clear();
                        selfPointSeries.add(fpDistance, fpElevation);
                        _routeElevationView.getChart().repaint();
                    } else {
                        selfPointSeries.clear();
                    }
                    _routeElevationView.getChart().repaint();
                }
            }

            try {
                Thread.sleep(DELTA_RECOMPUTE_MS);
            } catch (InterruptedException ignored) {
            }
        }
        // XXX ended au - natural
        Log.d(TAG, "finished processing the route information for: " + title);
    }
}
