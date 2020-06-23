
package com.atakmap.android.routes.elevation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.content.res.Configuration;
import android.view.LayoutInflater;

import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.RouteMapReceiver;
import com.atakmap.android.routes.elevation.model.RouteCache;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

public class RouteElevationBroadcastReceiver extends DropDownReceiver
        implements DropDown.OnStateListener, MapItem.OnGroupChangedListener,
        Route.OnRoutePointsChangedListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    static final public String TAG = "RouteElevationBroadcastReceiver";

    private static RouteElevationBroadcastReceiver _instance;

    private final MapView _mapView;
    private final Context _context;
    private final LayoutInflater _inflater;

    private Route _route;
    private String _title;
    private ProcessRouteThread processThread;
    private static int orient = Configuration.ORIENTATION_LANDSCAPE;
    private RouteElevationView routeElevationView;
    private SeekerBarPanelView seekerBarPanelView;
    private AnalysisPanelView analysisPanelView;
    private AnalysisPanelPresenter analysisPanelPresenter;

    private final RouteElevationPresenter routeElevationPresenter;
    private final SeekerBarPanelPresenter seekerBarPanelPresenter;
    private final SelfPresenter selfPresenter;
    private final SharedPreferences prefs;
    private Boolean bInterpolateElevations;
    private boolean _dropDownOpen = false;
    private Intent _onCloseIntent;

    private final BroadcastReceiver routePointsChangedReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context arg0, Intent arg1) {
            // if the points are not adjusting (being dragged), update the
            // elevation profile
            if (arg1.getBooleanExtra("isPointAdjusting", false))
                return;
            startProcessing();
        }

    };

    private RouteElevationBroadcastReceiver(MapView mapView,
            MapGroup mapGroup) {
        super(mapView);

        _mapView = mapView;
        _context = mapView.getContext();
        _inflater = LayoutInflater.from(_context);

        prefs = PreferenceManager.getDefaultSharedPreferences(mapView
                .getContext());

        // Setup Presenters
        analysisPanelPresenter = new AnalysisPanelPresenter();
        seekerBarPanelPresenter = new SeekerBarPanelPresenter(mapView);
        routeElevationPresenter = new RouteElevationPresenter();
        selfPresenter = new SelfPresenter(prefs);

        initViews();

        // Null means use the preference, otherwise use the set value
        this.bInterpolateElevations = null;
    }

    synchronized public static void initialize(MapView mapView,
            MapGroup mapGroup) {
        if (_instance == null)
            _instance = new RouteElevationBroadcastReceiver(mapView, mapGroup);
    }

    synchronized public static RouteElevationBroadcastReceiver getInstance() {
        return _instance;
    }

    private void initViews() {
        // Save the initial orientation so we can now when to reinitialize the views & presenters
        orient = _context.getResources().getConfiguration().orientation;

        // Setup Views
        routeElevationView = (RouteElevationView) _inflater.inflate(
                R.layout.route_elevation_graph, _mapView, false);
        routeElevationView.initialize();

        analysisPanelView = routeElevationView
                .findViewById(R.id.analysisPanelView);
        analysisPanelView.initialize(routeElevationView);

        seekerBarPanelView = routeElevationView
                .findViewById(R.id.seekerBarPanelView);
        seekerBarPanelView.initialize(routeElevationView);

        analysisPanelPresenter.bind(analysisPanelView, _mapView);
        seekerBarPanelPresenter.bind(seekerBarPanelView);
        routeElevationPresenter.bind(routeElevationView, _mapView,
                seekerBarPanelPresenter);
        selfPresenter.bind(routeElevationView, _mapView);
    }

    @Override
    public void disposeImpl() {
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
    }

    @Override
    public void onDropDownVisible(boolean v) {
        _dropDownOpen = v;
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        routeElevationPresenter.shutDown();
        try {
            AtakBroadcast.getInstance().unregisterReceiver(
                    routePointsChangedReceiver);
        } catch (Exception ignored) {
        }
        if (_route != null) {
            _route.removeOnRoutePointsChangedListener(this);
            _route.removeOnGroupChangedListener(this);
        }
        _route = null;
        stopProcessing();
        if (_onCloseIntent != null)
            AtakBroadcast.getInstance().sendBroadcast(_onCloseIntent);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String k) {
        if (k.equals("elevProfileInterpolateAlt") && _route != null) {
            startProcessing();
        }
    }

    public void setRoute(Route route, Boolean bInterpolateElevations) {
        _route = route;
        this.bInterpolateElevations = bInterpolateElevations;
    }

    public void setRoute(Route route) {
        setRoute(route, null);
    }

    public void setTitle(String title) {
        _title = title;
    }

    /**
     * This method bootstraps the elevation profile process. If the profile has been previously
     * calculated this method will display the cached version. A new profile will be generated if a
     * cache does not exist.
     * <p/>
     * Once computed, a display panel will appear showing the results of the elevation profile
     * computation.
     */
    public void startProcessing() {
        if (_route != null && _route.getNumPoints() >= 2) {
            if (processThread == null || processThread.isDisposed()) {
                Log.d(TAG, "bootstrap the elevation profile process: "
                        + _route.getTitle());
                processThread = new ProcessRouteThread(getMapView());
            }
            processThread.start(_route, routeElevationView,
                    analysisPanelPresenter, routeElevationPresenter,
                    selfPresenter, bInterpolateElevations);
        } else if (!isClosed())
            closeDropDown();
    }

    public void stopProcessing() {
        if (processThread != null)
            processThread.dispose();
        selfPresenter.stop();
    }

    public void openDropDown(Intent onCloseIntent) {
        if (_route == null || _route.getNumPoints() < 2)
            return;
        _checkOrientation();
        showDropDown(routeElevationView, FULL_WIDTH, HALF_HEIGHT, FULL_WIDTH,
                THIRD_HEIGHT, this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        AtakBroadcast.getInstance().registerReceiver(
                routePointsChangedReceiver,
                new DocumentedIntentFilter(
                        RouteMapReceiver.POINTS_CHANGED_ACTION));
        _route.addOnRoutePointsChangedListener(this);
        _route.addOnGroupChangedListener(this);

        startProcessing();
        _onCloseIntent = onCloseIntent;
    }

    public void openDropDown() {
        openDropDown(null);
    }

    public boolean isDropDownOpen() {
        return _dropDownOpen;
    }

    @Override
    public void onRoutePointsChanged(final Route route) {
        final String title = route.getTitle();
        // points have changed, the cache entry is invalid
        setRoute(route, bInterpolateElevations);
        RouteCache.getInstance().invalidate(title);
        routeElevationPresenter.setSuppressAutoCentering(true);
        startProcessing();
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        if (_route == item && !isClosed())
            closeDropDown();
    }

    /**
     * Check the orientation to see if we should update which layout the RouteElevationView uses.
     */
    private void _checkOrientation() {
        int o = getMapView().getContext().getResources()
                .getConfiguration().orientation;

        // If the orientation changes then update the views
        if (o != orient)
            initViews();
    }
}
