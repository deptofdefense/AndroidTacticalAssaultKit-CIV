
package com.atakmap.android.routes;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.tools.ActionBarView;
import com.atakmap.android.user.geocode.ReverseGeocodingTask;
import com.atakmap.android.user.geocode.ReverseGeocodingTask.ResultListener;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

public class RouteConfirmationTool extends Tool implements
        MapEventDispatcher.MapEventDispatchListener, View.OnClickListener {

    private static final String TAG = "RouteConfirmationTool";
    public static final String TOOL_NAME = "com.atakmap.android.routes." + TAG;

    private final Context _context;
    private final SharedPreferences _prefs;
    private final TextContainer _container;
    private final RouteMapReceiver _receiver;
    private final Marker _marker;
    private Intent _callbackIntent;

    // Confirm address widget
    private final ActionBarView _toolbar;
    private String _address;
    private boolean _accept;

    RouteConfirmationTool(MapView mapView, RouteMapReceiver receiver) {
        super(mapView, TOOL_NAME);
        _context = mapView.getContext();
        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
        _container = TextContainer.getInstance();
        _receiver = receiver;

        _toolbar = (ActionBarView) LayoutInflater.from(_context).inflate(
                R.layout.route_confirm_address_toolbar, mapView, false);
        _toolbar.setPosition(ActionBarView.TOP_RIGHT);
        _toolbar.setEmbedState(ActionBarView.FLOATING);
        _toolbar.showCloseButton(false);
        _toolbar.findViewById(R.id.route_address_accept)
                .setOnClickListener(this);
        _toolbar.findViewById(R.id.route_address_reject)
                .setOnClickListener(this);

        _marker = new Marker(TAG + "_ConfirmAddressPoint");
        _marker.setTitle("Destination");
        _marker.setIcon(new Icon.Builder().setImageUri(0, ATAKUtilities
                .getResourceUri(R.drawable.ic_checkered_flag_white)).build());
        _marker.setZOrder(Double.NEGATIVE_INFINITY);
        _marker.setMetaBoolean("ignoreOffscreen", true);
        _marker.setMetaBoolean("addToObjList", false);
        _marker.setMetaBoolean("ignoreFocus", false);
        _marker.setMetaBoolean("toggleDetails", true);
        _marker.setMetaBoolean("ignoreMenu", true);
        _marker.setMetaBoolean("nevercot", true);
        _marker.setMetaBoolean("drag", true);

        ToolManagerBroadcastReceiver.getInstance().registerTool(
                TOOL_NAME, this);
    }

    @Override
    public void dispose() {
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        _address = extras.getString("address");
        GeoPoint point = extras.getParcelable("point");
        _callbackIntent = extras.getParcelable("callback");
        if (_callbackIntent == null || point == null
                || FileSystemUtils.isEmpty(_address)) {
            requestEndTool();
            return false;
        }

        _mapView.getMapEventDispatcher().pushListeners();
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.ITEM_CLICK);
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_CLICK);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_DRAG_CONTINUED, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_DRAG_STARTED, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_DRAG_DROPPED, this);

        _container.displayPrompt(_address + "\n" + _context.getString(
                R.string.route_address_confirm_adjust));
        _mapView.getMapTouchController().setToolActive(true);

        _marker.setPoint(point);
        if (_marker.getGroup() == null)
            _receiver.getRouteGroup().addItem(_marker);
        _mapView.getMapController().panZoomTo(point, 0.0005, true);
        ActionBarReceiver.getInstance().setToolView(_toolbar);
        _accept = false;

        return true;
    }

    @Override
    public void onToolEnd() {
        _container.closePrompt();
        if (_callbackIntent != null) {
            _callbackIntent.putExtra("point", _marker.getPoint()
                    .toStringRepresentation());
            _callbackIntent.putExtra("address", _address);
            _callbackIntent.putExtra("accept", _accept);
            AtakBroadcast.getInstance().sendBroadcast(_callbackIntent);
        }
        _mapView.getMapEventDispatcher().popListeners();
        _mapView.getMapTouchController().setToolActive(false);
        if (_marker != null)
            _marker.removeFromGroup();
        ActionBarReceiver.getInstance().setToolView(null);
    }

    @Override
    public void onMapEvent(MapEvent event) {
        String t = event.getType();
        GeoPointMetaData point = findPoint(event);
        if (point == null || !point.get().isValid())
            return;
        _marker.setPoint(point);
        if (t.equals(MapEvent.ITEM_CLICK) || t.equals(MapEvent.MAP_CLICK)
                || t.equals(MapEvent.ITEM_DRAG_DROPPED)) {
            final ReverseGeocodingTask rgt = new ReverseGeocodingTask(
                    point.get(), _context, false);
            rgt.setOnResultListener(new ResultListener() {
                @Override
                public void onResult() {
                    if (_marker.getGroup() == null || !_marker.getPoint()
                            .equals(rgt.getPoint()))
                        return;
                    _address = rgt.getHumanAddress();
                    if (FileSystemUtils.isEmpty(_address))
                        _address = _context
                                .getString(R.string.unknown_address);
                    _container.displayPrompt(_address + "\n" + _context
                            .getString(
                                    R.string.route_address_confirm_adjust));
                }
            });
            rgt.execute();
        }
    }

    @Override
    public void onClick(View v) {
        // Accept geocoder address point
        if (v.getId() == R.id.route_address_accept)
            _accept = true;
        requestEndTool();
    }
}
