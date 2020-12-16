
package com.atakmap.android.util;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTouchController.DeconflictionListener;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

/**
 * Abstract pick a map item from the map tool.    This encapsulates all of the actions required to
 * push and pop the map event dispatch stack.
 * This tool self registers, so the only thing that needs to be done is that it needs to be added to 
 * the corresponding MapComponent.
 *    Bundle extras = new Bundle();
 *    ToolManagerBroadcastReceiver.getInstance().startTool(
 *               TOOL_IDENTIFIER, extras);
 * 
 */
public abstract class AbstractMapItemSelectionTool extends Tool implements
        MapEventDispatcher.MapEventDispatchListener {

    private final static String TAG = "AbstractMapItemSelectionTool";

    private final String toolId, toolFinished, prompt, invalidSelection;

    private final Context _context;
    private final TextContainer _container;
    private MapItem _item;

    private final DeconflictionListener decon = new DeconflictionListener() {
        public void onConflict(final SortedSet<MapItem> hitItems) {
            List<MapItem> list = new ArrayList<>(hitItems);
            hitItems.clear();

            for (MapItem mi : list) {
                if (isItem(mi))
                    hitItems.add(mi);
            }
        }
    };

    /**
     * The implementation of the map item selection tool appropriately manages the stack and provides
     * the appropriate level of feedback when an incorrect item is selected.
     * @param mapView The mapView.
     * @param toolId the unique identifier used to start the tool.   This should be unique across
     *               the application and the recommendation is to make use of the package name and
     *               class for the concrete implementation.   This intent is also fired when the
     * @param toolFinished the intent action that is fired with the result when the map item is
     *                     selected or when the tool is ended.    If there was an item selected
     *                     the "uid" string extra is set.
     * @param prompt the prompt to show the user on the screen.
     * @param invalidSelection the toast shown when an invalid item is selected.
     */
    public AbstractMapItemSelectionTool(final MapView mapView,
            final String toolId,
            final String toolFinished,
            final String prompt, final String invalidSelection) {
        super(mapView, toolId);

        this.toolId = toolId;

        this.toolFinished = toolFinished;
        this.prompt = prompt;
        this.invalidSelection = invalidSelection;

        _context = mapView.getContext();
        _container = TextContainer.getInstance();
        ToolManagerBroadcastReceiver.getInstance().registerTool(
                toolId, this);

    }

    /**
     * Must call dispose when the isim is no longer being used.    After this object is disposed,
     * it will no lonfger respond to intents.
     */
    public void dispose() {
        ToolManagerBroadcastReceiver.getInstance().unregisterTool(
                toolId);
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        _item = null;
        _mapView.getMapEventDispatcher().pushListeners();
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.ITEM_CLICK);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK, this);

        _container.displayPrompt(prompt);
        _mapView.getMapTouchController().skipDeconfliction(true);
        _mapView.getMapTouchController().addDeconflictionListener(decon);
        return true;
    }

    @Override
    public void onToolEnd() {
        _container.closePrompt();
        _mapView.getMapEventDispatcher().popListeners();
        _mapView.getMapTouchController().removeDeconflictionListener(decon);
        _mapView.getMapTouchController().skipDeconfliction(false);
        Log.d(TAG, "returning an item: " + _item + " from: " + toolId);
        Intent i = new Intent(toolFinished);
        if (_item != null) {
            i.putExtra("uid", _item.getUID());
        }
        AtakBroadcast.getInstance().sendBroadcast(i);
    }

    /**
     * Implementation of this should decide if the map item is appropriate.
     * @param mi the map item selected.
     * @return true if the map selected item is appropriate
     */
    protected abstract boolean isItem(MapItem mi);

    @Override
    public void onMapEvent(MapEvent event) {
        if (event.getType().equals(MapEvent.ITEM_CLICK)) {
            // Find item click point
            _item = event.getItem();

            if (!isItem(_item)) {
                toast(invalidSelection);
                _item = null;
                return;
            }
            requestEndTool();
        }
    }

    private void toast(final String str) {
        Toast.makeText(_context, str,
                Toast.LENGTH_LONG).show();
    }
}
