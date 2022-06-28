
package com.atakmap.android.toolbar;

import android.content.Intent;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;

import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

/**
 * A Tool is a modal operation which changes the way that the user interacts with the map. Only one
 * tool can be active at a time; if the user starts a new tool, the old tool ends.
 * 
 * 
 */
public abstract class Tool implements OnKeyListener {

    private boolean _active = false;
    protected final MapView _mapView;
    protected String _identifier;
    protected int _mapListenerCount = 0;

    public Tool(final MapView mapView, final String identifier) {
        _mapView = mapView;
        _identifier = identifier;
    }

    /***
     * Called when this tool has been asked to start (and after the last tool has cleaned up)
     * 
     * @param extras extra params that were passed by the GUI
     * @return true if tool is now active, false if it has already finished.
     */
    protected boolean onToolBegin(Bundle extras) {
        return true;
    }

    /***
     * Called when this tool has been asked to end, either directly or by the user selecting a new
     * tool or possibly a new toolbar.
     */
    protected void onToolEnd() {
    }

    /**
     * Called only by the parent during final cleanup activities.
     */
    abstract public void dispose();

    /***
     * Used by ToolbarLibrary to start a tool. Shouldn't be called directly, instead send a
     * BEGIN_TOOL intent or use the requestBeginTool() methods which sends such an intent.
     * 
     * @param extras extra params that were passed by the GUI
     * @return true if tool is now active, false if it has already finished or didn't run at all.
     */
    boolean beginTool(Bundle extras) {
        // we'll never want to begin a tool that's already begun, right?
        if (!getActive()) {
            boolean nowActive = onToolBegin(extras);
            setActive(nowActive);

            if (nowActive)
                _mapView.addOnKeyListener(this);

            return nowActive;
        }

        // we're running, so we should stay the active tool
        return true;
    }

    /***
     * Used by ToolbarLibrary to stop a running tool. Shouldn't be called directly, instead send an
     * END_TOOL intent or use the requestEndTool() method which sends such an intent.
     */
    protected void endTool() {
        // and we'll never want to end a tool that hasn't begun, right?
        if (getActive()) {
            setActive(false);
            onToolEnd();
        }

        _mapView.removeOnKeyListener(this);
        popAllMapListeners();
    }

    /**
     * Returns the identifier used to refer to this tool in intents.
     * 
     * @return
     */
    public String getIdentifier() {
        return _identifier;
    }

    /***
     * @param v
     * @param keyCode
     * @param event
     * @return
     */
    @Override
    public boolean onKey(final View v, final int keyCode,
            final KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK && shouldEndOnBack()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                // Wait for the UP, but don't allow other components to handle the click
                return !shouldCloseDropDown();
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                if (shouldCloseDropDown())
                    return false;

                requestEndTool();
                return true;
            }
        }

        return false;
    }

    private boolean shouldCloseDropDown() {
        return DropDownManager.getInstance().shouldCloseFirst();
    }

    /***
     * Returns whether this tool should end when the back button is pressed.
     * 
     * @return
     */
    public boolean shouldEndOnBack() {
        return true;
    }

    /**
     * Helper method to send the intent required to ask the ToolbarLibrary to make this tool active.
     * You can call this if you need to start your tool.
     */
    public void requestBeginTool() {
        Intent myIntent = new Intent();
        myIntent.setAction(ToolManagerBroadcastReceiver.BEGIN_TOOL);
        myIntent.putExtra("tool", _identifier);
        AtakBroadcast.getInstance().sendBroadcast(myIntent);
    }

    /**
     * Helper method to send the intent required to ask the ToolbarLibrary to make this tool
     * inactive. You can call this if you need to stop your tool while it's running.
     */
    public void requestEndTool() {
        Intent myIntent = new Intent();
        myIntent.setAction(ToolManagerBroadcastReceiver.END_TOOL);
        myIntent.putExtra("tool", _identifier);
        AtakBroadcast.getInstance().sendBroadcast(myIntent);
    }

    /***
     * Returns whether the tool is currently active, ie, onToolBegin has been called but onToolEnd
     * has not yet been called. In most cases the tool's button's selected state should reflect this
     * value.
     * 
     * @return
     */
    public boolean getActive() {
        return _active;
    }

    /***
     * Called to set whether a tool is actively being used. Generally, this should be automatically
     * handled by the onToolBegin(), onToolEnd() life cycle. Override to update GUI elements to
     * reflect the new value.
     * 
     * @return
     */
    protected void setActive(boolean active) {
        _active = active;
    }

    /***
     * Clears all listeners from the map except for those involved with fundamental map operations,
     * ie pan and pinch.
     */
    protected void clearExtraListeners() {
        // clear all the listeners listening for a click
        _mapView.getMapEventDispatcher().clearUserInteractionListeners(false);
    }

    /**
     * Push a new set of map listeners while tracking the current push count
     */
    protected void pushMapListeners() {
        _mapView.getMapEventDispatcher().pushListeners();
        _mapListenerCount++;
    }

    /**
     * Pop a set of map listeners while tracking the current push count
     */
    protected void popMapListeners() {
        _mapView.getMapEventDispatcher().popListeners();
        _mapListenerCount--;
    }

    /**
     * Pop all map listeners that were pushed with this tool
     */
    protected void popAllMapListeners() {
        while (_mapListenerCount > 0)
            popMapListeners();
    }

    /**
     * Helper method that finds a point given a map event (i.e. map click)
     * @param event Map event
     * @return Point or null if not found
     * TODO: Probably should get the actual touch point of both the terrain and the model as two different things so that the user of this tool can decide what makes the most sense to use.
     */
    protected GeoPointMetaData findPoint(MapEvent event) {
        if (event == null)
            return null;

        GeoPointMetaData point = null;
        PointF eventPoint = event.getPointF();
        String type = event.getType();

        MapItem mi = event.getItem();
        if (mi != null && !type.startsWith("item_drg")) {
            if (mi instanceof PointMapItem)
                point = ((PointMapItem) mi).getGeoPointMetaData();
            else if (mi instanceof Shape)
                point = GeoPointMetaData.wrap(((Shape) mi).getClickPoint());
        }

        if (point == null)
            point = _mapView.inverseWithElevation(eventPoint.x, eventPoint.y);

        return point;
    }
}
