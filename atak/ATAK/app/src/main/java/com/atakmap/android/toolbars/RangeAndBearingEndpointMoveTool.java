
package com.atakmap.android.toolbars;

import android.content.Intent;
import android.graphics.PointF;
import android.os.Bundle;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

public class RangeAndBearingEndpointMoveTool extends Tool implements
        MapEventDispatcher.MapEventDispatchListener {

    public static final String TAG = "RangeAndBearingEndpointMoveTool";

    private static final String IDENTIFIER = "RangeAndBearingEndpointMoveTool";
    private RangeAndBearingEndpoint _ep;

    public RangeAndBearingEndpointMoveTool(MapView mapView) {
        super(mapView, IDENTIFIER);
        ToolManagerBroadcastReceiver.getInstance().registerTool(IDENTIFIER,
                this);
        _ep = null;
    }

    @Override
    public void dispose() {
    }

    /**
     * Helper method to send the intent required to ask the ToolbarLibrary to make this tool active.
     * You can call this if you need to start your tool.
     */
    public void requestBeginTool(RangeAndBearingEndpoint ep) {
        _ep = ep;
        Intent myIntent = new Intent();
        myIntent.setAction(ToolManagerBroadcastReceiver.BEGIN_TOOL);
        myIntent.putExtra("tool", IDENTIFIER);
        AtakBroadcast.getInstance().sendBroadcast(myIntent);
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (event.getType().equals(MapEvent.MAP_CLICK)) {
            PointF p = event.getPointF();
            _ep.setPoint(_mapView.inverseWithElevation(p.x, p.y));
            requestEndTool();
        } else if (event.getType().equals(MapEvent.ITEM_CLICK)) {
            MapItem item = event.getItem();
            boolean handled = false;
            if (item instanceof PointMapItem) {
                if (_ep.getParent().containsEndpoint((PointMapItem) item))
                    return; // Prevent users from attaching the tail and head to the same object
                switch (_ep.getPart()) {
                    case RangeAndBearingEndpoint.PART_TAIL:
                        if (!_ep.getParent().isReversed()) {
                            _ep.getParent().setPoint1((PointMapItem) item);
                        } else {
                            _ep.getParent().setPoint2((PointMapItem) item);
                        }
                        break;
                    case RangeAndBearingEndpoint.PART_HEAD:
                        if (!_ep.getParent().isReversed()) {
                            _ep.getParent().setPoint2((PointMapItem) item);
                        } else {
                            _ep.getParent().setPoint1((PointMapItem) item);
                        }
                        break;
                }
                try {
                    _ep.removeFromGroup();
                } catch (Exception e) {
                    Log.e(TAG, "error: ", e);
                }
                handled = true;
            } else if (item instanceof Shape) {
                // Shape selected from deconfliction menu
                _ep.setPoint(((Shape) item).getClickPoint());
                handled = true;
            }
            // Tell the event dispatcher whether we handled the item click, so that it can be
            // handled as a map click in case item click doesn't work
            event.getExtras().putBoolean("eventNotHandled", !handled);

            if (handled)
                requestEndTool();
        }
    }

    @Override
    protected void onToolEnd() {
        _mapView.getMapEventDispatcher().popListeners();
        _mapView.getMapTouchController().setToolActive(false);
        TextContainer.getInstance().closePrompt();
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        _mapView.getMapEventDispatcher().pushListeners();
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_CLICK);
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.ITEM_CLICK);
        _mapView.getMapEventDispatcher().clearListeners(
                MapEvent.ITEM_LONG_PRESS);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK, this);
        _mapView.getMapTouchController().setToolActive(true);
        TextContainer.getInstance().displayPrompt(
                _mapView.getContext().getString(
                        R.string.rb_prompt2));
        return true;
    }

}
