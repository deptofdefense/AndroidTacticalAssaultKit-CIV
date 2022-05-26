
package com.atakmap.android.routes.elevation;

import java.util.List;

import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.LinearLayout;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.routes.FloatingPointRoute;
import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.elevation.chart.XYImageSeriesDataset;
import com.atakmap.android.routes.elevation.model.RouteData;
import com.atakmap.android.routes.elevation.model.SeekerMarker;
import com.atakmap.android.routes.elevation.service.AnalyticsElevationService;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.maps.conversion.EGM96;

import com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;

class RouteElevationPresenter implements ChartSelectionListener {

    private RouteElevationView _view;
    private RouteData _routeData;
    private Route _route;
    private SeekerMarker _marker;
    private ChartSeekBar _seekerBar;
    private SharedPreferences _prefs;
    private MapView _mapView;
    private SeekerBarPanelPresenter seekerBarPanelPresenter;

    public void bind(final RouteElevationView v, final MapView mapView,
            final SeekerBarPanelPresenter seekerBarPanelPresenter) {
        _view = v;
        _seekerBar = _view.getSeekerBar();
        _marker = new SeekerMarker(mapView);
        _mapView = mapView;
        _prefs = PreferenceManager
                .getDefaultSharedPreferences(mapView.getContext());
        this.seekerBarPanelPresenter = seekerBarPanelPresenter;

    }

    // Auto centering suppression is needed to fix bug 5976.
    // Auto centering was triggering while dragging vertices around in the map view, making it
    // difficult for users to position the object being dragged
    boolean setSuppressAutoCentering(boolean value) {
        return _marker.setSuppressAutoCentering(value);
    }

    @Override
    public void update(final int index, double xVal, double yVal,
            boolean moveSeeker,
            boolean seekerStopped) {

        if (_routeData == null || seekerBarPanelPresenter == null)
            return;

        GeoPointMetaData[] points = _routeData.getGeoPoints();
        if (index < 0 || index >= points.length)
            return;
        GeoPointMetaData point = points[index];

        seekerBarPanelPresenter.updateMgrsText(point.get());
        seekerBarPanelPresenter.updateMslText(point.get());
        seekerBarPanelPresenter.updateGainText(0);
        seekerBarPanelPresenter.updateSlopeText(0);

        // Seek to marker when elevation profile is open
        if (RouteElevationBroadcastReceiver.getInstance().isDropDownOpen()) {
            _marker.setQuickDraw(!seekerStopped);

            if (_route instanceof FloatingPointRoute) {

                // Special case for FloatingPointRoute

                FloatingPointRoute floatingRoute = (FloatingPointRoute) _route;
                List<GeoPoint> floatingGeoPoints = floatingRoute
                        .getFloatingGeoPoints();

                Integer[] indicies = _routeData.getIndices();
                if (indicies.length < 2 || index < indicies[0]
                        || index > indicies[indicies.length - 1])
                    _marker.draw(point.get());

                //find the index of the floating point that is before the current floating point
                int minIndex = 0;
                for (int i = 1; i < indicies.length; i++) {
                    if (indicies[i] < index)
                        minIndex = i;
                    else
                        break;
                }

                //set the altitude of the marker to be the interpolation of the altitude of the floating points
                //before and after the current point along the path
                if (minIndex >= floatingGeoPoints.size() - 1
                        || floatingGeoPoints.get(minIndex) == null
                        || floatingGeoPoints.get(minIndex + 1) == null)
                    _marker.draw(point.get());
                double alt = floatingGeoPoints.get(minIndex).getAltitude();
                if (minIndex < indicies.length - 1
                        && index != indicies[minIndex]) {
                    int diff = indicies[minIndex + 1] - indicies[minIndex];
                    int current = index - indicies[minIndex];
                    double percentage = (double) current / (double) diff;

                    double altDiff = floatingGeoPoints.get(minIndex + 1)
                            .getAltitude()

                            -
                            floatingGeoPoints.get(minIndex).getAltitude();

                    alt = alt + (altDiff * percentage);
                }

                GeoPoint gp = new GeoPoint(point.get().getLatitude(),
                        point.get().getLongitude(), alt,
                        point.get().getAltitudeReference());
                _marker.draw(gp);
            } else {
                // Generic route
                AltitudeMode altMode = _route.getAltitudeMode();
                if (altMode == AltitudeMode.ClampToGround) {
                    // Clamp marker to terrain
                    _marker.draw(new GeoPoint(point.get().getLatitude(),
                            point.get().getLongitude(), 0,
                            AltitudeReference.AGL));
                } else {
                    // Show marker on the exact route line
                    _marker.draw(point.get());
                }
            }
        }

        // fixes the seeker offset bug.
        _view.getSeekerBar().refreshMargins();

        if (moveSeeker && _view.getChart() != null) {
            double[] screen = _view.getChart().toScreenPoint(new double[] {
                    xVal, yVal
            });
            int progress = (int) Math.floor(screen[0])
                    - (int) (((float) _view.getChart().getLeftMargin()));
            _view.getSeekerBar().setProgress(progress);
            _view.getChart().onProgressChanged(_seekerBar, progress, true);
        }

        // update in realtime gain
        AnalyticsElevationService.findRouteSeekElevationGain(
                _routeData, index, seekerBarPanelPresenter);
        double s = AnalyticsElevationService.findInstantaneousSlope(
                _routeData.getDistances(),
                _routeData.getGeoPoints(), index);
        if (!Double.isNaN(s))
            seekerBarPanelPresenter.updateSlopeText(s);

        AnalyticsElevationService.findClosestControlPoint(
                _routeData.getDistances(),
                _routeData.getControlPointData().getDistances(),
                index,
                _routeData.getControlPointData().getNames(),
                _routeData.getTotalDistance(), seekerBarPanelPresenter);
    }

    public void setXAxisMin(double min) {
        _view.getRenderer().setXAxisMin(min);
    }

    public void setXAxisMax(double max) {
        _view.getRenderer().setXAxisMax(max);
    }

    public void setYAxisMin(double min) {
        _view.getRenderer().setYAxisMin(min);
    }

    public void setYAxisMax(double max) {
        _view.getRenderer().setYAxisMax(max);
    }

    public void updateChart(Route route, RouteData data) {
        this._routeData = data;
        this._route = route;

        //update the top text of the chart
        seekerBarPanelPresenter
                .updateMslText(_routeData.getGeoPoints()[0].get());

        int altFmt = Integer.parseInt(_prefs.getString("alt_unit_pref",
                String.valueOf(Span.ENGLISH)));

        String altDisplayPref = _prefs.getString("alt_display_pref",
                "MSL");

        String _X_AXIS_TITLE = MapView.getMapView()
                .getContext().getString(com.atakmap.app.R.string.routes_text25);
        String _Y_AXIS_TITLE = MapView.getMapView()
                .getContext().getString(com.atakmap.app.R.string.routes_text26);

        XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
        XYImageSeriesDataset imageDataset = new XYImageSeriesDataset();

        XYSeries hValues = new XYSeries("");
        XYSeries altitudeSeries = new XYSeries(_Y_AXIS_TITLE);

        XYSeries nullAltitudeSeries = new XYSeries(
                _Y_AXIS_TITLE);
        XYSeries cpAltitudeSeries = new XYSeries(
                _X_AXIS_TITLE);

        double[] xValues = data.getDistances();
        GeoPointMetaData[] yValues = data.getGeoPoints();

        double maxElevation = Double.MIN_VALUE;
        double minElevation = Double.MAX_VALUE;
        double lastReal = Double.MIN_VALUE;

        for (int i = 0; i < xValues.length; i++) {
            if (yValues[i].get().isAltitudeValid()) {

                // If the elevation is valid, go ahead and add to the elevation list
                double altType;
                if (altDisplayPref.equals("MSL")) {
                    altType = EGM96.getMSL(yValues[i].get());
                } else {
                    altType = EGM96.getHAE(yValues[i].get());
                }

                double altValue;
                if (altFmt == Span.ENGLISH) {
                    // convert meters to ft
                    altValue = altType
                            * ConversionFactors.METERS_TO_FEET;
                } else {
                    altValue = altType;
                }

                // add to list of elevations
                hValues.add(xValues[i], altValue);

                // default starting elevation in case route begins in null territory
                if (Double.compare(lastReal, Double.MIN_VALUE) == 0)
                    lastReal = altValue;

                // determine min/max
                if (altValue > maxElevation)
                    maxElevation = altValue;
                if (altValue < minElevation)
                    minElevation = altValue;

            } else {
                // mark as invalid
                hValues.add(xValues[i], Double.MIN_VALUE);
            }
        }

        // determine elevation to use for hiding the fills
        double hideElev = 0;
        if (minElevation < 0 && maxElevation < 0)
            hideElev = maxElevation;
        else if (minElevation > 0 && maxElevation > 0)
            hideElev = minElevation;

        // organize grid fills appropriately
        // this is done after initial loop because we needed
        // min/max elevation for fill hiding
        int hCount = hValues.getItemCount();
        double hx, hy, hyNext;
        for (int i = 0; i < hCount; i++) {
            hx = hValues.getX(i);
            hy = hValues.getY(i);
            hyNext = (i < hCount - 1 ? hValues.getY(i + 1) : hy);
            if (hy == Double.MIN_VALUE) {
                altitudeSeries.add(hx, hideElev);
                nullAltitudeSeries.add(hx, lastReal);
                if (hyNext != Double.MIN_VALUE) {
                    altitudeSeries.add(hx, hyNext);
                    nullAltitudeSeries.add(hx, hideElev);
                }
            } else {
                lastReal = hy;
                altitudeSeries.add(hx, hy);
                nullAltitudeSeries.add(hx, hideElev);
                if (hyNext == Double.MIN_VALUE) {
                    altitudeSeries.add(hx, hideElev);
                    nullAltitudeSeries.add(hx, hy);
                }
            }
        }

        if (route instanceof FloatingPointRoute) {
            //place the floating related points as check points above the graph
            List<GeoPoint> floatingGeoPoints = ((FloatingPointRoute) route)
                    .getFloatingGeoPoints();
            double dist = 0;
            for (int i = 0; i < floatingGeoPoints.size(); i++) {
                GeoPoint gp = floatingGeoPoints.get(i);
                if (i > 0) {
                    dist += gp.distanceTo(floatingGeoPoints.get(i - 1))
                            * ConversionFactors.METERS_TO_FEET;
                }
                double altitude = gp.getAltitude();
                double alt;
                if (altFmt == Span.ENGLISH) {
                    alt = altitude
                            * ConversionFactors.METERS_TO_FEET;
                } else {
                    alt = altitude;
                }
                cpAltitudeSeries.add(dist, alt);

                if (alt > maxElevation)
                    maxElevation = alt;
            }
        } else {
            double[] distances = data.getControlPointData().getDistances();
            for (int i = 0; i < distances.length; i++) {

                if (altFmt == Span.ENGLISH) {
                    cpAltitudeSeries
                            .add(distances[i],
                                    EGM96
                                            .getMSL(data.getControlPointData()
                                                    .getGeoPoints()[i])

                                            * ConversionFactors.METERS_TO_FEET);
                } else {
                    cpAltitudeSeries
                            .add(distances[i],
                                    EGM96.getMSL(data.getControlPointData()
                                            .getGeoPoints()[i]));
                }
            }
        }

        setXAxisMin(0);
        setXAxisMax(data.getTotalDistance());

        setYAxisMin(minElevation);
        setYAxisMax(maxElevation);

        dataset.addSeries(altitudeSeries);
        dataset.addSeries(nullAltitudeSeries);
        imageDataset.addSeries(cpAltitudeSeries);
        imageDataset.addSeries(new XYSeries("self point"));

        refreshChart(dataset, imageDataset, hValues);
    }

    private void refreshChart(final XYMultipleSeriesDataset dataset,
            final XYImageSeriesDataset imageDateset,
            final XYSeries heightSeries) {

        //refresh the view to update displayed labels based on user prefs
        _view.refresh();

        final ChartSelectionListener listener = this;
        _view.post(new Runnable() {
            @Override
            public void run() {
                double realPos = 0;
                if (_view.getChart() != null)
                    realPos = _view.getChart().getRealPosition();
                RouteElevationChart chart = new RouteElevationChart(_mapView
                        .getContext(), _view.getRenderer(),
                        _view.getImageRender(), dataset, imageDateset,
                        heightSeries);

                chart.addCountsListener(listener);
                chart.setMargins(RouteElevationView._CHART_LEFT_MARGIN,
                        RouteElevationView._CHART_RIGHT_MARGIN);

                _view.getLayout().removeAllViews();

                _view.getLayout().setOrientation(LinearLayout.VERTICAL);
                _view.getLayout().addView(_seekerBar,
                        new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT));
                _view.getLayout().addView(chart,
                        new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.MATCH_PARENT));
                chart.setPositions(realPos, _seekerBar.getProgress());
                chart.repaint();
                _seekerBar.setOnSeekBarChangeListener(chart);

                _view.setChart(chart);
            }
        });

    }

    void shutDown() {
        if (_marker != null)
            _marker.clear();
        if (_routeData != null)
            _routeData = null;
    }

    void invalidate() {
        _view.refresh();
        _view.invalidate();
    }
}
