
package com.atakmap.android.geofence.component;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.data.ClearContentRegistry;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.geofence.alert.GeoFenceAlerting;
import com.atakmap.android.geofence.data.GeoFence;
import com.atakmap.android.geofence.data.GeoFenceDatabase;
import com.atakmap.android.geofence.monitor.GeoFenceManager;
import com.atakmap.android.geofence.ui.GeoFenceMapOverlay;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapOverlayManager;
import com.atakmap.android.maps.MapView;

import com.atakmap.android.geofence.data.ShapeUtils;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;

/**
 * Manages shapes used as Geo Fences
 */
public class GeoFenceComponent extends AbstractMapComponent {
    private static final String TAG = "GeoFenceComponent";

    public static final String BREACH_MARKER_TYPE = "geofence-breach-marker";
    private static MapGroup _breachMarkerMapGroup;

    /**
     * Listen for updates to geo fences
     */
    public interface GeoFenceListener {
        void onFenceAdded(GeoFence fence, MapItem item);

        void onFenceChanged(GeoFence fence, MapItem item);

        void onFenceRemoved(String mapItemUid);
    }

    private GeoFenceDatabase _database;
    private GeoFenceReceiver _receiver;
    private GeoFenceMapOverlay _overlay;
    private GeoFenceManager _manager;
    private MapView _mapView;

    private static GeoFenceComponent _instance;

    private final ConcurrentLinkedQueue<GeoFenceListener> _onGeoFenceChanged = new ConcurrentLinkedQueue<>();

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        _mapView = view;
        //init DB
        _database = GeoFenceDatabase.instance();

        //get handle to MapGroup, created by another component
        _manager = new GeoFenceManager(this, view);

        //setup GeoFence overlay
        _overlay = new GeoFenceMapOverlay(view, _database, _manager);
        MapOverlayManager overlayManager = view.getMapOverlayManager();
        overlayManager.addAlertsOverlay(_overlay);

        //now create messaging/receiver
        _receiver = new GeoFenceReceiver(this, _database);
        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(GeoFenceReceiver.EDIT,
                "Intent to view and edit Geofence parameters");
        filter.addAction(GeoFenceReceiver.DISPLAY_ALERTING,
                "Intent to view list of Geofence alerts within Overlay Manager");
        filter.addAction(GeoFenceReceiver.ITEMS_SELECTED,
                "Intent to specific a list of map items to monitor in a Geofence");
        filter.addAction(GeoFenceReceiver.ADD,
                "Intent to a CoT based Geofence to the local system");

        AtakBroadcast.getInstance().registerReceiver(_receiver, filter);

        ClearContentRegistry.getInstance().registerListener(_receiver.ccl);

        synchronized (GeoFenceComponent.class) {
            _breachMarkerMapGroup = _mapView.getRootGroup().findMapGroup(
                    "Geo Fence Breaches");
            if (_breachMarkerMapGroup == null) {
                _breachMarkerMapGroup = new DefaultMapGroup(
                        "Geo Fence Breaches");
                _breachMarkerMapGroup.setMetaString("overlay",
                        "geofencebreaches");
                _breachMarkerMapGroup.setMetaBoolean("permaGroup", true);
                _breachMarkerMapGroup.setMetaBoolean("addToObjList", false);
                _mapView.getRootGroup().addGroup(_breachMarkerMapGroup);
            }
        }

        _instance = this;
    }

    public static synchronized GeoFenceComponent getInstance() {
        return _instance;
    }

    public static MapGroup getMapGroup() {
        synchronized (GeoFenceComponent.class) {
            return _breachMarkerMapGroup;
        }
    }

    public GeoFenceDatabase getDatabase() {
        return _database;
    }

    GeoFenceAlerting getAlerting() {
        return _manager.getAlerting();
    }

    public MapView getMapView() {
        return _mapView;
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {

        ClearContentRegistry.getInstance().unregisterListener(_receiver.ccl);

        if (_receiver != null) {
            AtakBroadcast.getInstance().unregisterReceiver(_receiver);
            _receiver.dispose();
            _receiver = null;
        }

        if (_manager != null) {
            _manager.dispose();
            _manager = null;
        }

        _database = null;
    }

    public GeoFenceManager getManager() {
        return _manager;
    }

    /**
     * Add a groupList listener
     *
     * @param listener the listener
     */
    public final void addGeoFenceChangedListener(GeoFenceListener listener) {
        _onGeoFenceChanged.add(listener);
    }

    /**
     * Remove a groupList listener
     *
     * @param listener the listener
     */
    public final void removeGeoFenceChangedListener(
            GeoFenceListener listener) {
        _onGeoFenceChanged.remove(listener);
    }

    public void dispatch(GeoFence fence, MapItem item) {
        if (item == null)
            return;

        GeoFenceDatabase.InsertOrUpdateResult result = _database
                .insertOrUpdate(fence);

        switch (result) {
            case Insert:
                dispatchGeoFenceAdded(fence, item);
                break;
            case Updated:
                dispatchGeoFenceChanged(fence, item);
                break;
            case Failure:
            case AlreadyUpToDate:
            default:
        }
    }

    public void notify(MapItem item) {
        if (_database.hasFence(item.getUID())) {
            String message = String
                    .format(_mapView.getContext().getString(
                            R.string.geofence_received_shape),
                            ATAKUtilities.getDisplayName(
                                    ATAKUtilities.findAssocShape(item)));
            NotificationUtil.getInstance().postNotification(
                    R.drawable.ic_menu_geofence, NotificationUtil.WHITE,
                    _mapView.getContext()
                            .getString(R.string.geo_fence_received),
                    message, message,
                    ShapeUtils.getZoomShapeIntent(item));
        }
    }

    /**
     * Invoked when a GeoFence is added
     *
     * @param fence the fence that is added
     * @param item the map item
     */
    public final void dispatchGeoFenceAdded(GeoFence fence, MapItem item) {
        for (GeoFenceListener l : _onGeoFenceChanged) {
            l.onFenceAdded(fence, item);
        }
    }

    /**
     * Invoked when a GeoFence is removed
     *
     * @param mapItemUid the uid for the map item that is associated with the geofence
     */
    public final void dispatchGeoFenceRemoved(String mapItemUid) {
        for (GeoFenceListener l : _onGeoFenceChanged) {
            l.onFenceRemoved(mapItemUid);
        }
    }

    /**
     * Invoked when a GeoFence is updated
     *
     * @param fence the fence that has been changed.
     */
    public final void dispatchGeoFenceChanged(GeoFence fence, MapItem item) {
        for (GeoFenceListener l : _onGeoFenceChanged) {
            l.onFenceChanged(fence, item);
        }
    }
}
