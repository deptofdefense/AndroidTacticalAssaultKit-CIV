
package com.atakmap.android.toolbar.tools;

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
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.user.CamLockerReceiver;
import com.atakmap.app.R;

public class SpecifyLockItemTool extends Tool {

    public static final String TOOL_IDENTIFIER = "com.atakmap.android.toolbar.tools.SpecifyLockItemTool";
    String _linkBack = null;

    public SpecifyLockItemTool(MapView mapView) {
        super(mapView, TOOL_IDENTIFIER);
    }

    @Override
    public void dispose() {
    }

    @Override
    protected boolean onToolBegin(Bundle extras) {

        _linkBack = extras.getString("linkBack", null);

        // push all the dispatch listeners
        _mapView.getMapEventDispatcher().pushListeners();

        // clear all the listeners listening for a click
        clearExtraListeners();

        _mapView.getMapTouchController().setToolActive(true);

        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK,
                lockItemPressListener);
        AtakBroadcast.getInstance().registerReceiver(locReceiver,
                new DocumentedIntentFilter(CamLockerReceiver.TOGGLE_LOCK,
                        "Track if the map is still lockable")
                                .addAction(
                                        CamLockerReceiver.TOGGLE_LOCK_LONG_CLICK,
                                        "Track if the map is still lockable")
                                .addAction(CamLockerReceiver.LOCK_CAM,
                                        "Track if the map is still lockable"));

        String text = _mapView.getContext().getString(
                R.string.tap_lock_item);
        TextContainer.getInstance().displayPrompt(text);

        return true;
    }

    @Override
    protected void onToolEnd() {
        _mapView.getMapTouchController().setToolActive(false);
        _mapView.getMapEventDispatcher().popListeners();
        TextContainer.getInstance().closePrompt();
        AtakBroadcast.getInstance().unregisterReceiver(locReceiver);

        _linkBack = null;
    }

    private final MapEventDispatcher.MapEventDispatchListener lockItemPressListener = new MapEventDispatcher.MapEventDispatchListener() {

        @Override
        public void onMapEvent(MapEvent event) {

            if (MapEvent.ITEM_CLICK.equals(event.getType())) {
                final MapItem mi = event.getItem();
                if (mi instanceof PointMapItem) {
                    Intent intent = new Intent(CamLockerReceiver.LOCK_CAM)
                            .putExtra("uid", mi.getUID());
                    AtakBroadcast.getInstance().sendBroadcast(intent);

                    requestEndTool();

                    if (_linkBack != null) {
                        intent = new Intent(_linkBack);
                        AtakBroadcast.getInstance().sendBroadcast(intent);
                    }
                    _linkBack = null;

                    return;
                }
            }

            Toast.makeText(_mapView.getContext(),
                    R.string.select_lock_pointitem,
                    Toast.LENGTH_LONG).show();
        }
    };

    private final BroadcastReceiver locReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            requestEndTool();
        }
    };
}
