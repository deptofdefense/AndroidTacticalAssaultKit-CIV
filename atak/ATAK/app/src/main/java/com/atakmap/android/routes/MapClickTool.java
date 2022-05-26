
package com.atakmap.android.routes;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.user.CamLockerReceiver;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

/**
 *
 *    ** DEPRECATED ** DO NOT MODIFY ** SEE com.atakmap.android.user.MapClickTool
 * Simple tool for retrieving a map/item click
 * Callback intent contains the "point" extra
 *
 * Please note, using this class and listening for when the END_TOOL is broadcast 
 * needs to GUARANTEE that it is first checked to see if the tool is active for that
 * specific workflow or guarantee that calls to END_TOOL from other workflows with this 
 * tool will not cause issues.
 * @deprecated @see com.atakmap.android.user.MapClickTool
 */
@DeprecatedApi(since = "4.5", forRemoval = true, removeAt = "4.8")
public class MapClickTool extends Tool implements
        MapEventDispatcher.MapEventDispatchListener {

    /**
     * Please migrate to using com.atakmap.android.user.MapClickTool
     * @deprecated
     */
    public static final String TOOL_NAME = "MapClickTool-Deprectated";

    private final Context _context;
    private final TextContainer _container;
    private Intent _callbackIntent;
    private GeoPointMetaData _clicked;

    MapClickTool(MapView mapView) {
        super(mapView, TOOL_NAME);
        _context = mapView.getContext();
        _container = TextContainer.getInstance();
        ToolManagerBroadcastReceiver.getInstance()
                .registerTool(TOOL_NAME, this);
    }

    @Override
    public void dispose() {
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        _clicked = null;
        _callbackIntent = extras.getParcelable("callback");
        if (_callbackIntent == null) {
            requestEndTool();
            return false;
        }

        Intent intent = new Intent();
        intent.setAction(CamLockerReceiver.UNLOCK_CAM);
        AtakBroadcast.getInstance().sendBroadcast(intent);

        _mapView.getMapEventDispatcher().pushListeners();
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.ITEM_CLICK);
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_CLICK);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, this);

        String prompt = extras.getString("prompt");
        _container.displayPrompt(!FileSystemUtils.isEmpty(prompt) ? prompt
                : _context.getString(R.string.map_click_select_prompt));
        _mapView.getMapTouchController().skipDeconfliction(true);
        return true;
    }

    @Override
    public void onToolEnd() {
        _container.closePrompt();

        if (_callbackIntent != null) {
            if (_clicked != null)
                _callbackIntent.putExtra("point",
                        _clicked.get().toStringRepresentation());
            AtakBroadcast.getInstance().sendBroadcast(_callbackIntent);
        }

        _mapView.getMapEventDispatcher().popListeners();
        _mapView.getMapTouchController().skipDeconfliction(false);
        _clicked = null;
    }

    @Override
    public void onMapEvent(MapEvent event) {
        _clicked = findPoint(event);
        if (_clicked == null)
            return;
        requestEndTool();
    }
}
