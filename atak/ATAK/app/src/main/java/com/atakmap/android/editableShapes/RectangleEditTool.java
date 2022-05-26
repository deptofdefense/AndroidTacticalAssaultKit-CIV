
package com.atakmap.android.editableShapes;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.os.Bundle;
import android.widget.Button;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTouchController.DeconflictionListener;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.toolbar.ButtonTool;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.EditAction;
import com.atakmap.android.util.Undoable;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.SortedSet;
import java.util.Stack;

public class RectangleEditTool extends ButtonTool implements Undoable,
        MapItem.OnGroupChangedListener, Rectangle.OnMoveListener,
        DeconflictionListener {

    /************************ CONSTRUCTORS ****************************/

    public RectangleEditTool(MapView mapView, Button button,
            Button undoButton) {
        super(mapView, button, TOOL_IDENTIFIER);
        _undoButton = undoButton;
    }

    /************************ INHERITED METHODS ****************************/

    private ManualRectangleEditReceiver _manualRectangleEditReceiver = null;

    @Override
    public boolean onToolBegin(Bundle extras) {
        _rectangle.setVisible(true);
        _rectangle.setEditable(true);
        _rectangle.addOnGroupChangedListener(this);
        _rectangle.addOnMovedListener(this);
        _deviceUID = _mapView.getSelfMarker().getUID();// get this device's uid so we can
                                                       // detect a click on it

        twoListenerPushesDeep = false;
        // create an extra instance of the current map listeners
        _mapView.getMapEventDispatcher().pushListeners();
        // clear all of the superfluous map listeners
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_CLICK);

        _mapView.getMapEventDispatcher()
                .clearListeners(MapEvent.MAP_LONG_PRESS);
        _mapView.getMapEventDispatcher().clearListeners(
                MapEvent.ITEM_LONG_PRESS);

        // still want item_press and item_release to work

        // add all of the listeners for the tool
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_LONG_PRESS,
                _itemPressListener);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_DRAG_CONTINUED,
                _dragListener);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_DRAG_STARTED,
                _dragListener);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_DRAG_DROPPED,
                _dragListener);
        _mapView.getMapTouchController().addDeconflictionListener(this);

        // display the main prompt
        TextContainer.getInstance()
                .displayPrompt(getString(R.string.rectangle_main_prompt));

        // com.atakmap.android.maps.MANUAL_POINT_RECTANGLE_EDIT
        if (_manualRectangleEditReceiver == null) {
            _manualRectangleEditReceiver = new ManualRectangleEditReceiver(
                    _mapView);
            DocumentedIntentFilter manualRectangleEditReceiverFilter = new DocumentedIntentFilter();
            manualRectangleEditReceiverFilter
                    .addAction(
                            "com.atakmap.android.maps.MANUAL_POINT_RECTANGLE_EDIT");
            AtakBroadcast.getInstance().registerReceiver(
                    _manualRectangleEditReceiver,
                    manualRectangleEditReceiverFilter);
        }

        return super.onToolBegin(extras);
    }

    private String getString(final int res) {
        return _mapView.getContext().getString(res);
    }

    public class ManualRectangleEditReceiver extends BroadcastReceiver {

        final MapView _entry;

        public ManualRectangleEditReceiver(MapView remove) {

            _entry = remove;
        }

        @Override
        public void onReceive(final Context context, final Intent intent) {
            String pointUID, pointLat, pointLon, pointAlt;
            GeoPoint dropPoint;
            pointUID = intent.getStringExtra("uid");
            pointLat = intent.getStringExtra("lat");
            pointLon = intent.getStringExtra("lon");
            pointAlt = intent.getStringExtra("alt");

            dropPoint = new GeoPoint(Double.parseDouble(pointLat),
                    Double.parseDouble(pointLon),
                    Double.parseDouble(pointAlt));

            for (int i = 0; i < _rectangle.getNumPoints(); i++) {
                if (_rectangle.getPointAt(i).getUID().equals(pointUID))
                    doRun(i, GeoPointMetaData.wrap(dropPoint));
            }
        }
    }

    private void doRun(int index, GeoPointMetaData dropPoint) {
        if (dropPoint == null)
            return;

        PointMapItem itemToMove = _rectangle.getPointAt(index);

        GeoPoint gp = itemToMove.getPoint();
        _rectangle.setPoint(itemToMove,
                GeoPointMetaData.wrap(
                        new GeoPoint(gp.getLatitude(),
                                gp.getLongitude(),
                                dropPoint.get().getAltitude(),
                                gp.getCE(),
                                gp.getLE()),
                        GeoPointMetaData.USER,
                        GeoPointMetaData.USER));
        EditAction move = new Rectangle.MovePointAction(
                itemToMove, dropPoint, _rectangle);
        run(move);
    }

    @Override
    public void onToolEnd() {
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(MapMenuReceiver.HIDE_MENU));
        if (_manualRectangleEditReceiver != null) {
            AtakBroadcast.getInstance().unregisterReceiver(
                    _manualRectangleEditReceiver);
            _manualRectangleEditReceiver = null;
        }
        _rectangle.setEditable(false);
        _rectangle.removeOnGroupChangedListener(this);
        _rectangle.removeOnMovedListener(this);
        _rectangle.removeMetaData("dragInProgress");
        _mapView.getMapEventDispatcher().popListeners();
        if (twoListenerPushesDeep)
            _mapView.getMapEventDispatcher().popListeners();
        _mapView.getMapTouchController().removeDeconflictionListener(this);
        TextContainer.getInstance().closePrompt();
        _undoStack.clear();
        super.onToolEnd();
    }

    @Override
    public boolean run(EditAction action) {
        final boolean active = getActive();
        if (active) {
            synchronized (actionLock) {
                boolean success = action.run();
                if (success) {
                    _undoStack.push(action);
                    if (_undoButton != null)
                        _undoButton.setEnabled(true);
                }

                return success;
            }
        } else {
            return false;
        }
    }

    @Override
    public void undo() {
        final boolean active = getActive();
        if (active) {
            synchronized (actionLock) {
                // pop of editaction from top of stack and undo it
                if (_undoStack.size() > 0) {
                    _undoStack.pop().undo();
                }
                if (_undoStack.size() == 0) {
                    // disable the undobutton if the stack is empty
                    if (_undoButton != null)
                        _undoButton.setEnabled(false);
                }
            }
        }
    }

    private GeoPointMetaData[] getCorners() {
        GeoPointMetaData[] points = _rectangle.getGeoPoints();
        return new GeoPointMetaData[] {
                points[0], points[1], points[2], points[3]
        };
    }

    /************************ LISTENERS ****************************/

    private final MapEventDispatcher.MapEventDispatchListener _itemPressListener = new MapEventDispatcher.MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            // only want the corner/side markers
            if (!(event.getItem() instanceof PointMapItem)) {
                return;
            }

            PointMapItem point = (PointMapItem) event.getItem();

            if (!_rectangle.hasPoint(point))
                return;

            final PointMapItem itemToMove = point;

            twoListenerPushesDeep = true;

            // get a fresh slate of map listeners
            _mapView.getMapEventDispatcher().pushListeners();
            clearExtraListeners();

            MapEventDispatcher.MapEventDispatchListener l = new MapEventDispatcher.MapEventDispatchListener() {
                @Override
                public void onMapEvent(MapEvent event) {
                    GeoPointMetaData point = null;
                    if (event.getType().equals(MapEvent.MAP_CLICK)
                            || event.getType()
                                    .equals(MapEvent.MAP_LONG_PRESS)) {
                        point = _mapView.inverseWithElevation(
                                event.getPointF().x, event.getPointF().y);
                    } else if (event.getType().equals(MapEvent.ITEM_CLICK)
                            && event.getItem() instanceof PointMapItem &&
                            _deviceUID.equals(event.getItem().getUID())) {
                        point = ((PointMapItem) event.getItem())
                                .getGeoPointMetaData();
                        event.getExtras().putBoolean("eventNotHandled", false);
                    } else {
                        event.getExtras().putBoolean("eventNotHandled", true);
                    }

                    if (point != null) {
                        if (itemToMove == _rectangle.getAnchorItem()) {
                            moveRect(point);
                        } else {
                            EditAction move = new Rectangle.MovePointAction(
                                    itemToMove, point, _rectangle);
                            run(move);
                        }
                        // return to previous listener state
                        _mapView.getMapEventDispatcher().popListeners();
                        twoListenerPushesDeep = false;
                        TextContainer.getInstance().displayPrompt(
                                getString(R.string.rectangle_main_prompt));
                    }
                }
            };
            _mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.ITEM_CLICK, l);
            _mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.MAP_CLICK, l);
            _mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.MAP_LONG_PRESS, l);
            TextContainer.getInstance()
                    .displayPrompt(getString(R.string.rectangle_tap_prompt));
        }
    };

    // listener to handle all of the drags for the rectangle
    private final MapEventDispatcher.MapEventDispatchListener _dragListener = new MapEventDispatcher.MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            MapItem mi = event.getItem();
            if (!(mi instanceof PointMapItem))
                return;
            PointMapItem pmi = (PointMapItem) mi;
            GeoPointMetaData point = _mapView.inverseWithElevation(
                    event.getPointF().x, event.getPointF().y);
            if (!point.get().isValid())
                return;
            boolean dragCenter = pmi == _rectangle.getAnchorItem();
            boolean dragInProgress = _rectangle.hasMetaValue("dragInProgress");
            switch (event.getType()) {
                case MapEvent.ITEM_DRAG_STARTED:
                    if (!dragInProgress) {
                        _rectangle.setMetaBoolean("dragInProgress", true);
                        // Unfocus the map to dismiss a possible FINE_ADJUST action
                        Intent intent = new Intent();
                        intent.setAction("com.atakmap.android.maps.UNFOCUS");
                        AtakBroadcast.getInstance().sendBroadcast(intent);
                        // If we are dragging
                        _startCorners = getCorners();
                    }
                    break;
                case MapEvent.ITEM_DRAG_CONTINUED:
                    if (dragInProgress) {
                        if (dragCenter)
                            moveRect(point);
                        else
                            _rectangle.setPoint(pmi, point);
                        dropPoint = point;
                    }
                    break;
                case MapEvent.ITEM_DRAG_DROPPED:
                    if (dragInProgress) {
                        // Set the association markers to be visible
                        EditAction act = new Rectangle.MovePointAction(
                                (PointMapItem) mi, dropPoint,
                                _rectangle, _startCorners);
                        run(act);
                    }
                    _rectangle.removeMetaData("dragInProgress");

                    if (twoListenerPushesDeep) {
                        // return to previous listener state
                        _mapView.getMapEventDispatcher().popListeners();
                        twoListenerPushesDeep = false;
                        TextContainer.getInstance().displayPrompt(
                                getString(R.string.rectangle_main_prompt));
                    }
                    break;
            }
        }
    };

    // Listener to end the tool if the rectangle is deleted

    @Override
    public void onItemAdded(MapItem item, MapGroup newGroup) {
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup oldGroup) {
        requestEndTool();
    }

    @Override
    public void onMoved(Rectangle r, GeoPointMetaData[] oldPoints,
            GeoPointMetaData[] newPoints) {
        if (r.hasMetaValue("dragInProgress"))
            return;
        PointMapItem center = r.getAnchorItem();
        EditAction act = new Rectangle.MovePointAction(
                center, center.getGeoPointMetaData(), _rectangle, oldPoints);
        run(act);
    }

    @Override
    public void onConflict(final SortedSet<MapItem> hitItems) {
        MapItem hitItem = null;
        for (MapItem item : hitItems) {
            hitItem = item;
            if (hitItem == _rectangle || ATAKUtilities.findAssocShape(
                    hitItem) == _rectangle)
                break;
        }
        hitItems.clear();
        if (hitItem != null)
            hitItems.add(hitItem);
    }

    /**
     * Move the entire rectangle (anchored by center) to new point
     * @param point New center point
     */
    private void moveRect(GeoPointMetaData point) {
        GeoPoint center = GeoCalculations.computeAverage(
                _rectangle.getPoints(),
                _mapView.isContinuousScrollEnabled());

        _rectangle
                .move(GeoPointMetaData.wrap(center, GeoPointMetaData.CALCULATED,
                        GeoPointMetaData.CALCULATED), point);
    }

    /************************ FIELDS ****************************/

    protected Rectangle _rectangle;
    private final Object actionLock = new Object();
    private GeoPointMetaData dropPoint;
    private GeoPointMetaData[] _startCorners;
    private String _deviceUID;
    private boolean twoListenerPushesDeep;

    public static final String TOOL_IDENTIFIER = "com.atakmap.android.drawing.tools.RectangleEditTool";

    protected final Button _undoButton;
    private final Stack<EditAction> _undoStack = new Stack<>();

}
