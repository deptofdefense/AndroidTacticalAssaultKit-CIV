
package com.atakmap.android.toolbars;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.toolbar.ButtonTool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.UUID;

public class RangeAndBearingTool extends ButtonTool implements
        MapEventDispatcher.MapEventDispatchListener {

    public static final String TAG = "RangeAndBearingTool";
    public static final String TOOL_NAME = "range_and_bearing_tool";

    private PointMapItem _pt1;
    private PointMapItem _pt2;
    private Marker _startPt;

    private final MapGroup _rabGroup;

    private boolean _complete;

    public RangeAndBearingTool(MapView mapView, ImageButton button) {
        super(mapView, button, TOOL_NAME);
        _rabGroup = _mapView.getRootGroup().findMapGroup("Range & Bearing");
        ToolManagerBroadcastReceiver.getInstance()
                .registerTool(TOOL_NAME, this);
        if (button != null)
            button.setOnLongClickListener(onLongClickListener);
    }

    @Override
    public void dispose() {
        super.dispose();
        //Wrong - clears the whole group, not just the R+B lines
        //Ends up breaking things like the R+B circle tool
        /*if (_rabGroup != null)
            _rabGroup.clearGroups();*/
        _pt1 = null;
        _pt2 = null;
        _startPt = null;
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        _pt1 = null;
        _pt2 = null;
        if (extras.containsKey("startingUID")) {
            MapItem endpoint = _mapView.getMapItem(extras
                    .getString("startingUID"));
            if (endpoint instanceof PointMapItem)
                _pt1 = (PointMapItem) endpoint;
            else if (endpoint instanceof Shape) {
                GeoPoint touchPoint = ((Shape) endpoint).getClickPoint();
                if (touchPoint != null) {
                    GeoPointMetaData point = GeoPointMetaData.wrap(touchPoint);
                    _pt1 = createRabEndpoint(point);
                    createStartMarker(point);
                }
            }
        }

        _complete = false;
        _mapView.getMapEventDispatcher().pushListeners();
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_CLICK);
        _mapView.getMapEventDispatcher()
                .clearListeners(MapEvent.MAP_LONG_PRESS);
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.ITEM_CLICK);
        _mapView.getMapEventDispatcher().clearListeners(
                MapEvent.ITEM_LONG_PRESS);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_LONG_PRESS, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_LONG_PRESS, this);
        _mapView.getMapTouchController().setToolActive(true);

        if (_pt1 == null)
            prompt(R.string.rb_prompt);
        else {
            TextContainer.getInstance().closePrompt();
            prompt(R.string.rb_measure_prompt);
        }
        return true;
    }

    View.OnLongClickListener onLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
            Toast.makeText(_mapView.getContext(), R.string.rb_line_tip,
                    Toast.LENGTH_SHORT).show();
            return true;
        }
    };

    @Override
    public void onToolEnd() {

        // if the tool got canceled, clean up
        if (!_complete) {
            _rabGroup.removeItem(_pt1);
            _rabGroup.removeItem(_pt2);
        }

        if (_startPt != null)
            _startPt.removeFromGroup();
        _startPt = null;

        _mapView.getMapTouchController().setToolActive(false);
        _mapView.getMapEventDispatcher().clearListeners();
        _mapView.getMapEventDispatcher().popListeners();
        TextContainer.getInstance().closePrompt();
    }

    void makeRabWidget() {
        if (_pt1 != null && _pt2 != null) {
            String rabUUID = UUID.randomUUID().toString();
            RangeAndBearingMapItem rab = RangeAndBearingMapItem
                    .createOrUpdateRABLine(rabUUID, _pt1, _pt2);
            if (rab != null) {
                rab.setMetaString("entry", "user");
                RangeAndBearingMapComponent.getGroup().addItem(rab);
                rab.persist(_mapView.getMapEventDispatcher(), null,
                        this.getClass());
            }
        }
        _complete = true;
    }

    @Override
    public void onMapEvent(MapEvent event) {
        GeoPointMetaData point = findPoint(event);
        switch (event.getType()) {
            case MapEvent.MAP_CLICK:
            case MapEvent.MAP_LONG_PRESS:
                selectPoint(point);
                break;

            case MapEvent.ITEM_CLICK: {
                MapItem item = event.getItem();

                // Use anchor point if available
                // Or not - see ATAK-8457
                /*if (item instanceof AnchoredMapItem) {
                MapItem anchor = ((AnchoredMapItem) item).getAnchorItem();
                if (anchor != null)
                    item = anchor;
                }*/

                Log.d(TAG,
                        " IN RangeAndBearingTool's onMapItemEventOccurred(...) and item is: "
                                + item);
                if (item instanceof PointMapItem) {
                    Log.d(TAG,
                            " It's a PointMapItem. _pt1=" + _pt1 + " and _pt2="
                                    + _pt2);
                    if (_pt1 == null) {
                        _pt1 = (PointMapItem) item;
                        TextContainer.getInstance().closePrompt();
                        prompt(R.string.rb_prompt3);
                    } else if (_pt2 == null) {
                        if (item != _pt1) {
                            _pt2 = (PointMapItem) item;
                            makeRabWidget();
                            requestEndTool();
                        }
                    }
                } else if (item instanceof Shape) {
                    // Shape selected from deconfliction menu
                    selectPoint(point);
                } else {
                    // Tell the event dispatcher we didn't handle the item click, so that it can be
                    // handled as a map click!
                    event.getExtras().putBoolean("eventNotHandled", true);
                }
                break;
            }
            case MapEvent.ITEM_LONG_PRESS: {
                MapItem item = event.getItem();
                if (item instanceof PointMapItem) {
                    _pt1 = (PointMapItem) _mapView.getRootGroup().deepFindItem(
                            "uid", _mapView.getSelfMarker().getUID());
                    if (_pt1 != null) {
                        if (item != _pt1) {
                            _pt2 = (PointMapItem) item;
                            makeRabWidget();
                            requestEndTool();
                        }
                    }
                }
                break;
            }
        }
    }

    private RangeAndBearingEndpoint createRabEndpoint(GeoPointMetaData gp) {
        if (gp == null)
            return null;
        RangeAndBearingEndpoint pt = new RangeAndBearingEndpoint(
                gp, UUID.randomUUID().toString());
        pt.setMetaString("menu", "menus/rab_endpoint_menu.xml");
        _rabGroup.addItem(pt);
        return pt;
    }

    private void createStartMarker(GeoPointMetaData gp) {
        if (_startPt != null)
            _startPt.removeFromGroup();
        _startPt = new Marker(gp, UUID.randomUUID().toString());
        _startPt.setType("shape_marker");
        _startPt.setShowLabel(false);
        _startPt.setClickable(false);
        _startPt.setMetaBoolean("addToObjList", false);
        _startPt.toggleMetaData("nevercot", true);
        _startPt.setColor(Color.RED);
        _rabGroup.addItem(_startPt);
    }

    private void selectPoint(GeoPointMetaData point) {
        if (_pt1 == null) {
            _pt1 = createRabEndpoint(point);
            createStartMarker(point);
            prompt(R.string.rb_measure_prompt);
        } else if (_pt2 == null) {
            _pt2 = createRabEndpoint(point);
            makeRabWidget();
            requestEndTool();
        }
    }

    synchronized protected void prompt(int stringId) {
        TextContainer.getInstance().displayPrompt(
                _mapView.getResources()
                        .getString(stringId));
    }
}
