
package com.atakmap.android.toolbars;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.ArrayList;
import java.util.UUID;

public class DynamicRangeAndBearingTool extends Tool implements
        MapEventDispatcher.MapEventDispatchListener, View.OnClickListener,
        View.OnLongClickListener {

    public static final String TAG = "DynamicRangeAndBearingTool";
    public static final String TOOL_NAME = "dynamic_range_and_bearing_tool";

    protected PointMapItem _pt1;
    protected PointMapItem _pt2;
    private RangeAndBearingMapItem _rab;
    private final String _rabUUID = UUID.randomUUID().toString();

    protected final Context _context;
    protected final MapGroup _rabGroup;

    private boolean _complete;
    private ArrayList<ImageButton> _buttons;

    private static DynamicRangeAndBearingTool _instance;

    protected int pops = 0;

    synchronized public static DynamicRangeAndBearingTool getInstance(
            MapView mapView, ImageButton button) {
        if (_instance == null)
            _instance = RangeAndBearingCompat
                    .getDynamicRangeAndBearingInstance(mapView, button);
        else
            _instance.addButton(button);

        return _instance;
    }

    protected DynamicRangeAndBearingTool(MapView mapView, ImageButton button) {
        super(mapView, TOOL_NAME);
        _context = mapView.getContext();
        if (_buttons == null)
            _buttons = new ArrayList<>();
        addButton(button);
        _rabGroup = _mapView.getRootGroup().findMapGroup("Range & Bearing");
        ToolManagerBroadcastReceiver.getInstance()
                .registerTool(TOOL_NAME, this);

    }

    protected void addButton(ImageButton button) {
        if (button != null) {
            button.setOnClickListener(this);
            button.setOnLongClickListener(this);
            _buttons.add(button);
        }
    }

    @Override
    public void dispose() {
        if (_buttons != null) {
            for (ImageButton button : _buttons) {
                button.setOnClickListener(null);
                button.setOnLongClickListener(null);
            }
        }
        _buttons = null;

        _pt1 = null;
        _pt2 = null;
    }

    @Override
    public boolean onToolBegin(Bundle extras) {

        if (_pt1 != null && _pt2 != null) {
            return true;
        } else if (_pt2 != null) {
            // error occurred, cleanup
            Log.d(TAG, "an unfortunate error has occurred, stopping this tool");
            requestEndTool();
            return false;
        }

        _pt1 = null;
        _pt2 = null;
        if (extras.containsKey("startingUID")) {
            MapItem endpoint = _mapView.getMapItem(extras
                    .getString("startingUID"));
            if (endpoint instanceof PointMapItem)
                _pt1 = (PointMapItem) endpoint;
            else if (endpoint instanceof DrawingShape)
                _pt1 = ((DrawingShape) endpoint).getAnchorItem();
        }

        _complete = false;
        _mapView.getMapEventDispatcher().pushListeners();
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_CLICK);
        _mapView.getMapEventDispatcher().pushListeners();
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.ITEM_CLICK);
        pops = 2;

        _mapView.getMapTouchController().setToolActive(true);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK, this);
        prompt(R.string.rb_dynamic_prompt);

        for (ImageButton button : _buttons)
            button.setOnClickListener(this);

        return true;
    }

    @Override
    public void onClick(View v) {

        // end if we're active, begin if we're not
        if (getActive() && !_complete) {
            requestEndTool();
        } else if (_complete) {
            if (_pt1 != null) {
                _rabGroup.removeItem(_pt1);
                _pt1 = null;
            }
            if (_pt2 != null) {
                _rabGroup.removeItem(_pt2);
                _pt2 = null;
            }
            if (_rab != null) {
                _rab.removeFromGroup();
            }
            for (ImageButton button : _buttons)
                button.setSelected(false);
            _complete = false;
            if (getActive())
                requestEndTool();
        } else {
            requestBeginTool();
            for (ImageButton button : _buttons)
                button.setSelected(true);
        }
    }

    @Override
    public boolean onLongClick(View view) {
        Toast.makeText(_context, R.string.dynamic_rb_tip,
                Toast.LENGTH_SHORT).show();
        return true;
    }

    @Override
    public void setActive(final boolean active) {
        super.setActive(active);
        if (active) {
            for (ImageButton button : _buttons)
                button.setSelected(active);
        } else if (!_complete) {
            if (_pt1 != null) {
                _rabGroup.removeItem(_pt1);
            }
            if (_pt2 != null) {
                _rabGroup.removeItem(_pt2);
            }
            if (_rab != null) {
                _rab.removeFromGroup();
            }
            for (ImageButton button : _buttons)
                button.setSelected(active);
        } else {
            Log.d(TAG,
                    "attempting to turn off a tool that is not active and not complete");
        }
    }

    @Override
    public void onToolEnd() {
        for (int i = 0; i < pops; ++i)
            _mapView.getMapEventDispatcher().popListeners();
        pops = 0;
        _mapView.getMapTouchController().setToolActive(false);
        if (_pt2 == null) {
            TextContainer.getInstance().closePrompt();
            AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    MapMenuReceiver.HIDE_MENU));
        }
    }

    protected synchronized void makeRabWidget() {
        if (_pt1 != null && _pt2 != null) {
            _rab = RangeAndBearingMapItem
                    .createOrUpdateRABLine(_rabUUID, _pt1, _pt2, false);

            _rab.setMetaString("menu", "menus/drab_menu.xml");
            _rab.setTitle("Dynamic R&B Line");
            _rab.setMetaBoolean("removable", true);
            _rab.setMetaString("entry", "user");
            _mapView.getMapEventDispatcher().addMapItemEventListener(
                    _rab, _mapItemEventListener);
            RangeAndBearingMapComponent.getGroup().addItem(_rab);
            _complete = true;
        }
    }

    private final MapEventDispatcher.OnMapEventListener _mapItemEventListener = new MapEventDispatcher.OnMapEventListener() {
        @Override
        public void onMapItemMapEvent(final MapItem item,
                final MapEvent event) {
            if (event.getType().equals(MapEvent.ITEM_REMOVED)) {
                Log.d(TAG,
                        "ending the dynamic range and bearing tool because the r&b line was removed.");
                clearListeners();
                _mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        _complete = false;
                        DynamicRangeAndBearingTool.this.setActive(false);
                        _pt1 = null;
                        _pt2 = null;
                        _rab = null;
                    }
                });
            }
        }
    };

    @Override
    public void onMapEvent(MapEvent event) {
        String type = event.getType();

        if (type.equals(MapEvent.MAP_CLICK)
                || type.equals(MapEvent.ITEM_CLICK)) {
            final GeoPointMetaData gp = findPoint(event);

            if (_pt1 == null) {
                _pt1 = new DynamicRangeAndBearingEndpoint(_mapView, gp,
                        UUID.randomUUID().toString());
                _pt1.setMetaString("menu", "menus/drab_endpoint_menu.xml");
                _pt1.setMetaString("entry", "user");
                _rabGroup.addItem(_pt1);
                prompt(R.string.rb_measure_prompt);
            } else if (_pt2 == null) {
                _pt2 = new DynamicRangeAndBearingEndpoint(_mapView, gp,
                        UUID.randomUUID().toString());
                _pt2.setMetaString("menu", "menus/drab_endpoint_menu.xml");
                _pt2.setMetaString("entry", "user");
                _rabGroup.addItem(_pt2);
                _mapView.getMapEventDispatcher().addMapItemEventListener(_pt1,
                        _dragListener);
                _mapView.getMapEventDispatcher().addMapItemEventListener(_pt2,
                        _dragListener);
                AtakBroadcast.getInstance().registerReceiver(_backListener,
                        new DocumentedIntentFilter(
                                DropDownManager.CLOSE_DROPDOWN,
                                "Close R&B prompt on back button press"));
                prompt(R.string.rb_dynamic_second_prompt);
                makeRabWidget();

                _mapView.getMapEventDispatcher().popListeners();
                pops = 1;
                requestEndTool();
            }
        }
    }

    private void clearListeners() {
        TextContainer.getInstance().closePrompt(_context.getString(
                R.string.rb_dynamic_second_prompt));
        AtakBroadcast.getInstance().unregisterReceiver(_backListener);
        if (_pt1 != null)
            _mapView.getMapEventDispatcher().removeMapItemEventListener(
                    _pt1, _dragListener);
        if (_pt2 != null)
            _mapView.getMapEventDispatcher().removeMapItemEventListener(
                    _pt2, _dragListener);
    }

    protected final MapEventDispatcher.OnMapEventListener _dragListener = new MapEventDispatcher.OnMapEventListener() {
        @Override
        public void onMapItemMapEvent(MapItem item, MapEvent event) {
            if (event.getType().equals(MapEvent.ITEM_DRAG_STARTED))
                clearListeners();
        }
    };

    protected final BroadcastReceiver _backListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            clearListeners();
        }
    };

    synchronized protected void prompt(int stringId) {
        TextContainer.getInstance().displayPrompt(_context
                .getString(stringId));
    }
}
