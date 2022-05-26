
package com.atakmap.android.routes;

import java.util.UUID;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

public class GoToMapTool extends BroadcastReceiver implements
        MapEventDispatcher.MapEventDispatchListener {

    public static final String TAG = "GoToMapTool";

    private static final String GOTO_MAPTOOL = "com.atakmap.android.routes.GOTO_NAV_BEGIN";
    public static final String GOTO_MAPTOOL_END = "com.atakmap.android.routes.GOTO_NAV_END";
    public static final String END_TOOL = "com.atakmap.android.maps.toolbar.END_TOOL";
    private final MapView _mapView;
    private final TextContainer _container;
    private String _targetUID;
    private boolean waitingForNav;

    static GoToMapTool _instance;

    private GoToMapTool(MapView mapView) {
        _mapView = mapView;
        _container = TextContainer.getInstance();

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(GOTO_MAPTOOL);
        filter.addAction(GOTO_MAPTOOL_END);
        filter.addAction("com.android.arrowmaker.BACK_PRESS");

        AtakBroadcast.getInstance().registerReceiver(this, filter);
        _instance = this;
    }

    synchronized public static GoToMapTool getInstance(MapView mapView) {
        if (_instance == null) {
            _instance = new GoToMapTool(mapView);
        }
        return _instance;
    }

    public void dispose() {
        AtakBroadcast.getInstance().unregisterReceiver(this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        Log.d(TAG, "received request to: " + intent.getAction());
        if ("com.android.arrowmaker.BACK_PRESS".equals(intent.getAction())) {
            if (waitingForNav)
                end();
            return;
        }

        if (GOTO_MAPTOOL_END.equals(intent.getAction())
                || END_TOOL.equals(intent.getAction())) {
            Log.d(TAG, "request to end the quick nav in progress");
            end();
            return;
        }

        PointMapItem _self = ATAKUtilities.findSelf(_mapView);
        if (_self == null) {
            Toast.makeText(_mapView.getContext(),
                    R.string.self_marker_required,
                    Toast.LENGTH_LONG).show();
            return;
        }

        configureNav(intent.getExtras());

    }

    private boolean configureNav(final Bundle extras) {

        if (extras != null && extras.containsKey("point")) {
            GeoPoint pt = GeoPoint.parseGeoPoint(extras.getString("point"));
            if (pt == null) {
                return false;
            }
            createPoint(GeoPointMetaData.wrap(pt));
            // _dummyPoint = true;
            _startAction();
            return true;
        }
        if (extras != null && extras.containsKey("target")) {
            _targetUID = extras.getString("target");
            if (_targetUID == null) {
                return false;
            }
            // _dummyPoint = false;
            _startAction();
            return true;
        }
        // _dummyPoint = false;

        _mapView.getMapEventDispatcher().removeMapEventListener(
                MapEvent.ITEM_CLICK, this);
        _mapView.getMapEventDispatcher().removeMapEventListener(
                MapEvent.MAP_CLICK, this);
        _mapView.getMapEventDispatcher().removeMapEventListener(
                MapEvent.ITEM_REMOVED, this);

        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_REMOVED, this);
        _mapView.getMapTouchController().setToolActive(true);
        _targetUID = null;
        _container.displayPrompt(_mapView.getResources().getString(
                R.string.quick_nav_prompt));
        waitingForNav = true;
        return true;
    }

    public void end() {
        waitingForNav = false;

        _mapView.getMapTouchController().setToolActive(false);
        _mapView.getMapEventDispatcher().removeMapEventListener(
                MapEvent.ITEM_CLICK, this);
        _mapView.getMapEventDispatcher().removeMapEventListener(
                MapEvent.MAP_CLICK, this);
        _mapView.getMapEventDispatcher().removeMapEventListener(
                MapEvent.ITEM_REMOVED, this);
        _container.closePrompt();

    }

    @Override
    public void onMapEvent(MapEvent event) {
        switch (event.getType()) {
            case MapEvent.ITEM_CLICK:
                MapItem pt = event.getItem();
                if (_targetUID == null
                        &&
                        !pt.getUID().equals(
                                _mapView.getSelfMarker().getUID())) {
                    _targetUID = pt.getUID();
                    _container.closePrompt();
                    _mapView.getMapTouchController().setToolActive(false);
                    _mapView.getMapEventDispatcher().removeMapEventListener(
                            MapEvent.ITEM_CLICK, this);
                    _mapView.getMapEventDispatcher().removeMapEventListener(
                            MapEvent.MAP_CLICK, this);

                    // hide the menu if you clicked on a thing
                    Intent localMenu = new Intent();
                    localMenu.setAction("com.atakmap.android.maps.HIDE_MENU");
                    localMenu.putExtra("uid",
                            _mapView.getSelfMarker().getUID());
                    AtakBroadcast.getInstance().sendBroadcast(localMenu);

                    // stop focus the menu if you clicked on a thing
                    Intent localMenu2 = new Intent();
                    localMenu2.setAction("com.atakmap.android.maps.UNFOCUS");
                    localMenu2.putExtra("uid",
                            _mapView.getSelfMarker().getUID());
                    AtakBroadcast.getInstance().sendBroadcast(localMenu2);

                    _startAction();
                }
                break;
            case MapEvent.MAP_CLICK:
                if (event.getItem() == null) {
                    final GeoPointMetaData geoPoint = _mapView
                            .inverseWithElevation(
                                    event.getPointF().x,
                                    event.getPointF().y);
                    // addPoint(_point);
                    _container.closePrompt();
                    _mapView.getMapEventDispatcher().addMapEventListener(
                            MapEvent.ITEM_ADDED,
                            new MapEventDispatcher.MapEventDispatchListener() {
                                @Override
                                public void onMapEvent(MapEvent event) {
                                    if (event.getItem().getUID()
                                            .equals(_targetUID)) {
                                        _mapView.getMapEventDispatcher()
                                                .removeMapEventListener(
                                                        MapEvent.ITEM_ADDED,
                                                        this);
                                        _startAction();
                                    }
                                }
                            });

                    createPoint(geoPoint);

                    _mapView.getMapTouchController().setToolActive(false);
                    _mapView.getMapEventDispatcher().removeMapEventListener(
                            MapEvent.ITEM_CLICK, this);
                    _mapView.getMapEventDispatcher().removeMapEventListener(
                            MapEvent.MAP_CLICK, this);
                    waitingForNav = false;
                }
                break;
            case MapEvent.ITEM_REMOVED:
                if (event.getItem() != null
                        && event.getItem().getUID().equals(_targetUID)) {
                    end();
                }
                break;
        }

    }

    private void createPoint(GeoPointMetaData geoPoint) {
        _targetUID = UUID.randomUUID().toString();

        new PlacePointTool.MarkerCreator(geoPoint)
                .setType("b-m-p-w-GOTO")
                .setUid(_targetUID)
                .showCotDetails(false)
                .placePoint();
    }

    private void _startAction() {

        Intent intent = new Intent();
        intent.setAction("com.atakmap.android.toolbars.BLOOD_HOUND");
        intent.putExtra("uid", _targetUID);
        AtakBroadcast.getInstance().sendBroadcast(intent);
    }

}
