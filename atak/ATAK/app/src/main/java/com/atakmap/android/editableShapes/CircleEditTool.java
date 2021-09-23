
package com.atakmap.android.editableShapes;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher.MapEventDispatchListener;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.toolbar.ButtonTool;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.EditAction;
import com.atakmap.android.util.Undoable;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.Stack;

public class CircleEditTool extends ButtonTool implements Undoable,
        MapItem.OnGroupChangedListener, MapEventDispatchListener {

    /************************ FIELDS ****************************/

    protected DrawingCircle _circle;

    private final Object actionLock = new Object();
    private boolean twoListenerPushesDeep;

    private final String MAIN_PROMPT;
    private final String TAP_CIRCLE_PROMPT;
    private final String TAP_CENTER_PROMPT;

    protected final Button _undoButton;
    private final Stack<EditAction> _undoStack = new Stack<>();
    public static final String TOOL_IDENTIFIER = "com.atakmap.android.drawing.tools.CircleEditTool";

    /************************ CONSTRUCTORS ****************************/

    public CircleEditTool(MapView mapView, Button button, Button undoButton) {
        super(mapView, button, TOOL_IDENTIFIER);

        MAIN_PROMPT = mapView.getContext().getString(
                R.string.circle_resize_prompt);
        TAP_CIRCLE_PROMPT = mapView.getContext().getString(
                R.string.circle_new_rad);
        TAP_CENTER_PROMPT = mapView.getContext().getString(
                R.string.circle_new_center);

        _undoButton = undoButton;
    }

    /************************ INHERITED METHODS ****************************/

    @Override
    public void dispose() {
        super.dispose();
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        _circle.setVisible(true);// make sure the circle is visible and clickable
        _circle.setClickable(true);

        _circle.addOnGroupChangedListener(this);// listen for this item being deleted in
                                                // order to end tool

        twoListenerPushesDeep = false;// set up the state for the map listeners, fresh set of
                                      // listeners
        _mapView.getMapEventDispatcher().pushListeners();
        this.clearExtraListeners();

        // set up this tools listeners to handle the item
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_LONG_PRESS,
                this);

        // display the main prompt
        TextContainer.getInstance().displayPrompt(MAIN_PROMPT);

        return super.onToolBegin(extras);
    }

    @Override
    public void onToolEnd() {
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(MapMenuReceiver.HIDE_MENU));
        _circle.removeOnGroupChangedListener(this);
        _mapView.getMapEventDispatcher().popListeners();
        if (twoListenerPushesDeep)
            _mapView.getMapEventDispatcher().popListeners();
        TextContainer.getInstance().closePrompt();
        _undoStack.clear();
        super.onToolEnd();
    }

    @Override
    public boolean run(EditAction action) {
        if (getActive()) {
            synchronized (actionLock) {
                boolean success = action.run();
                if (success) {
                    _undoStack.push(action);
                    if (_undoButton != null)
                        _undoButton.setEnabled(true);
                }
                return success;
            }
        }
        return false;
    }

    @Override
    public void undo() {
        if (getActive()) {
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

    /************************ LISTENERS ****************************/

    @Override
    public void onMapEvent(final MapEvent event) {
        MapItem item = event.getItem();
        if (item == null)
            return;

        MapItem shape = ATAKUtilities.findAssocShape(item);
        if (shape != _circle)
            return;

        // Get ready to add a new listener
        _mapView.getMapEventDispatcher().pushListeners();
        clearExtraListeners();
        twoListenerPushesDeep = true;

        if (item == _circle.getCenterMarker()) {
            MapEventDispatchListener center = new MapEventDispatchListener() {
                @Override
                public void onMapEvent(MapEvent event) {
                    GeoPointMetaData point = findPoint(event);
                    if (point == null)
                        return;

                    run(new MovePointAction(_circle, point));

                    // return to previous listener state
                    _mapView.getMapEventDispatcher().popListeners();
                    twoListenerPushesDeep = false;
                    TextContainer.getInstance().displayPrompt(
                            MAIN_PROMPT);
                    event.getExtras().putBoolean("eventNotHandled",
                            false);
                }
            };
            TextContainer.getInstance().displayPrompt(TAP_CENTER_PROMPT);
            _mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.MAP_CLICK, center);
            _mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.ITEM_CLICK, center);
            _mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.MAP_LONG_PRESS,
                    center);
        } else {
            // The item is the circle itself
            GeoPointMetaData point = findPoint(event);
            if (point == null)
                return;
            double fromCenter = _circle.getCenterPoint().distanceTo(
                    point.get());
            final int ring = (int) Math.round(fromCenter / _circle.getRadius());
            MapEventDispatchListener radius = new MapEventDispatchListener() {
                @Override
                public void onMapEvent(MapEvent event) {
                    GeoPointMetaData point = findPoint(event);
                    if (point == null)
                        return;

                    double newRadius = _circle.getCenterPoint().distanceTo(
                            point.get()) / ring;

                    // Set radius and store undo
                    run(new EditRadiusAction(_circle, newRadius));

                    // return to previous listener state
                    _mapView.getMapEventDispatcher().popListeners();
                    twoListenerPushesDeep = false;
                    TextContainer.getInstance().displayPrompt(MAIN_PROMPT);
                    event.getExtras().putBoolean("eventNotHandled", false);
                }
            };
            TextContainer.getInstance().displayPrompt(TAP_CIRCLE_PROMPT);
            _mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.MAP_CLICK, radius);
            _mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.ITEM_CLICK, radius);
            _mapView.getMapEventDispatcher().addMapEventListener(
                    MapEvent.MAP_LONG_PRESS,
                    radius);
        }
    }

    @Override
    public void onItemAdded(final MapItem item, final MapGroup newParent) {
    }

    @Override
    public void onItemRemoved(final MapItem item, final MapGroup oldParent) {
        requestEndTool();
    }

    public static class MovePointAction extends EditAction {

        private final DrawingCircle _circle;
        private final GeoPointMetaData _oldPoint, _newPoint;

        public MovePointAction(DrawingCircle circle,
                GeoPointMetaData newPoint) {
            _circle = circle;
            _oldPoint = circle.getCenter();
            _newPoint = newPoint;
        }

        @Override
        public boolean run() {
            _circle.setCenterPoint(_newPoint);
            return true;
        }

        @Override
        public void undo() {
            _circle.setCenterPoint(_oldPoint);
        }

        @Override
        public String getDescription() {
            return null;
        }
    }

    public static class EditRadiusAction extends EditAction {
        private final DrawingCircle _circle;
        private final double _oldRadius, _newRadius;

        public EditRadiusAction(DrawingCircle circle, double newRadius) {
            _circle = circle;
            _oldRadius = circle.getRadius();
            _newRadius = newRadius;
        }

        @Override
        public boolean run() {
            _circle.setRadius(_newRadius);
            return true;
        }

        @Override
        public void undo() {
            _circle.setRadius(_oldRadius);
        }

        @Override
        public String getDescription() {
            return null;
        }
    }
}
