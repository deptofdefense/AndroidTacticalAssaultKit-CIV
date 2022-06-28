
package com.atakmap.android.editableShapes;

import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.toolbar.ButtonTool;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.util.DragMarkerHelper;
import com.atakmap.android.util.EditAction;
import com.atakmap.android.util.Undoable;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.CameraController;

import java.util.Stack;

public class EditablePolylineEditTool extends ButtonTool
        implements Undoable, MapEventDispatcher.MapEventDispatchListener,
        EditablePolyline.OnEditableChangedListener {

    protected final Context _context;
    protected EditablePolyline _poly;
    protected boolean twoListenerPushesDeep = false;
    protected int _insertPointIndex;
    private GeoPointMetaData startPoint;
    private int dragHitIndex;
    private Marker dragMarker;
    private final Stack<EditAction> undoStack = new Stack<>();
    protected Button _undoButton;
    protected boolean _vertsVisible = false;
    protected boolean _handleUndo = true;

    protected TextContainer container;
    protected String MAIN_PROMPT = MapView._mapView.getResources().getString(
            R.string.circle_move_shape);
    protected String TAP_PROMPT = MapView._mapView.getResources().getString(
            R.string.circle_tap_new_route);

    // Lock to prevent weird stuff like doing the same undo twice
    private final Object actionLock = new Object();

    public static final String TAG = "EditablePolylineEditTool";

    public EditablePolylineEditTool(MapView mapView, Button button,
            Button undoButton,
            String identifier) {
        super(mapView, button, identifier);
        _context = mapView.getContext();
        _undoButton = undoButton;
        container = TextContainer.getInstance();
    }

    @Override
    public void dispose() {
        super.dispose();
        _undoButton = null;
        container = null;
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        // Clear out the map listeners we don't want
        _mapView.getMapEventDispatcher().pushListeners();
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_CLICK);
        // we still want the Item_Click handler, cause we still want radial menus (at very least on
        // the route's points)

        _mapView.getMapEventDispatcher()
                .clearListeners(MapEvent.MAP_LONG_PRESS);
        _mapView.getMapEventDispatcher().clearListeners(
                MapEvent.ITEM_LONG_PRESS);

        // zoom map to fit in all the waypoints of this route
        if (extras.getBoolean("scaleToFit", true)) {
            _scaleToFit(_poly, _mapView.getWidth(), _mapView.getHeight());
        }

        // make sure the user can see the lines if this route was previously hidden
        _poly.setVisible(true);

        // style this route to be editable, and make it's points visible so they can be tapped
        _poly.setEditable(true);
        _poly.addOnEditableChangedListener(this);

        _handleUndo = extras.getBoolean("handleUndo", true);
        if (_handleUndo)
            _poly.setUndoable(this);

        _poly.addOnGroupChangedListener(associationSetDeletionListener);

        _vertsVisible = _poly.shouldDisplayVertices(_mapView.getMapScale());

        showMainPrompt();

        // set up the gestures that will be used to edit the route
        initEditListeners();

        // show the end/done button on the toolbar
        if (_button != null)
            _button.setVisibility(Button.VISIBLE);

        if (_undoButton != null) {
            // undo button starts off disabled; undo stack is empty
            _undoButton.setEnabled(false);
        }

        return super.onToolBegin(extras);
    }

    @Override
    public void onToolEnd() {

        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(MapMenuReceiver.HIDE_MENU));

        _poly.removeMetaData("dragInProgress");
        _poly.removeOnEditableChangedListener(this);
        if (_poly.getEditable())
            _poly.setEditable(false);

        _mapView.getMapEventDispatcher().popListeners();
        if (twoListenerPushesDeep) // we're two pushes deep, so pop twice
            _mapView.getMapEventDispatcher().popListeners();
        removeDragMarker();

        if (_poly.getUndoable() == this)
            _poly.setUndoable(null);

        _poly.removeOnGroupChangedListener(associationSetDeletionListener);

        if (_button != null)
            _button.setVisibility(Button.GONE);
        if (_undoButton != null) {

            // undo button disabled; undo stack is empty
            _undoButton.setEnabled(false);
        }

        container.closePrompt();

        // clear undo stack
        undoStack.clear();

        Bundle persistExtras = new Bundle();
        persistExtras.putBoolean("doNotRecreate", true);
        persistExtras.putBoolean("internal", true);

        if (_poly.getGroup() != null) {
            _poly.removeMetaData("creating");
            _poly.persist(_mapView.getMapEventDispatcher(), persistExtras,
                    this.getClass());
        }
    }

    @Override
    public void onEditableChanged(EditablePolyline polyline) {
        if (_poly != polyline) {
            polyline.removeOnEditableChangedListener(this);
            return;
        }
        // In case editable state was changed somewhere else
        if (!_poly.getEditable())
            requestEndTool();
    }

    protected void showMainPrompt() {
        String prompt = MAIN_PROMPT;
        if (!_vertsVisible)
            prompt += "\n" + _context.getString(R.string.route_zoom_in_prompt);
        container.displayPrompt(prompt);
    }

    private void initEditListeners() {
        // move points when you longpress them
        _poly.mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_LONG_PRESS,
                new MapEventDispatcher.MapEventDispatchListener() {

                    @Override
                    public void onMapEvent(MapEvent event) {

                        // if we're not being edited,return!
                        if (!_poly.getEditable())
                            return;

                        final GeoPointMetaData startPoint;
                        final int indexToMove;
                        final boolean insertPoint;

                        if (event.getItem() instanceof PointMapItem) {

                            PointMapItem item = (PointMapItem) event.getItem();

                            // check if this point is in the route being edited, if not do nothing
                            if (!_poly.hasMarker(item))
                                return;

                            startPoint = item.getGeoPointMetaData();
                            indexToMove = _poly.getIndexOfMarker(item);
                            insertPoint = false;
                        } else if (event.getItem().equals(_poly)) {
                            String hitType = _poly.getMetaString(
                                    "hit_type", "");
                            if (hitType.equals("point")
                                    && !multipleVerticesHit()) {
                                // Move existing point
                                indexToMove = _poly.getMetaInteger("hit_index",
                                        -1);
                                startPoint = _poly.getPoint(indexToMove);
                                insertPoint = false;
                            } else if (hitType.equals("line")) {
                                // Insert new point
                                indexToMove = _poly.getMetaInteger(
                                        "hit_index", 0) + 1;
                                startPoint = GeoPointMetaData
                                        .wrap(_poly.getClickPoint());
                                insertPoint = true;
                            } else
                                return;
                        } else {
                            return;
                        }

                        dragMarker = DragMarkerHelper.createDragMarker();
                        dragMarker.setPoint(startPoint);
                        _mapView.getRootGroup().addItem(dragMarker);

                        // set this flag so if the tool ends now we pop both sets of listeners we've
                        // pushed
                        twoListenerPushesDeep = true;

                        container.displayPrompt(_mapView.getResources()
                                .getString(R.string.move_point_prompt)
                                + _mapView.getResources().getString(
                                        R.string.move_this_point_prompt));

                        // push all the dispatch listeners
                        _mapView.getMapEventDispatcher().pushListeners();

                        // clear all the listeners listening for a click
                        clearExtraListeners();

                        final MapEventDispatcher.MapEventDispatchListener longPressDrag = new MapEventDispatcher.MapEventDispatchListener() {
                            @Override
                            public void onMapEvent(MapEvent event) {

                                final String type = event.getType();
                                final MapItem item = event.getItem();
                                GeoPointMetaData gp = _mapView
                                        .inverseWithElevation(
                                                event.getPointF().x,
                                                event.getPointF().y);
                                if (!gp.get().isValid())
                                    return;
                                Log.d(TAG,
                                        "shb: " + type + " "
                                                + item.getClass());
                                if (type.equals(MapEvent.ITEM_DRAG_CONTINUED)
                                        || type.equals(
                                                MapEvent.ITEM_DRAG_STARTED)) {

                                    if (insertPoint
                                            && type.equals(
                                                    MapEvent.ITEM_DRAG_STARTED)) {
                                        // Insert new point when drag started
                                        EditAction act = _poly.new InsertPointAction(
                                                gp, indexToMove);
                                        run(act);
                                        _poly.setMetaInteger(
                                                "hit_index",
                                                indexToMove);
                                        _poly.setMetaString("hit_type",
                                                "point");
                                    }

                                    // calculate the new ground point
                                    _poly.setMetaBoolean(
                                            "dragInProgress", true);
                                    _poly.setPoint(indexToMove, gp);
                                    if (dragMarker != null)
                                        dragMarker.setPoint(gp);
                                } else if (type
                                        .equals(MapEvent.ITEM_DRAG_DROPPED)) {
                                    _poly.removeMetaData("dragInProgress");
                                    _poly.setPoint(indexToMove, gp,
                                            false);
                                    if (!insertPoint) {
                                        // move to the point user tapped
                                        EditAction act = _poly.new MovePointAction(
                                                indexToMove,
                                                startPoint,
                                                gp);
                                        run(act);
                                    }

                                    _mapView.getMapEventDispatcher()
                                            .popListeners();

                                    // anything else we need to do to update a point's location?
                                    // we're back to only one layer deep
                                    twoListenerPushesDeep = false;

                                    // remove text
                                    showMainPrompt();

                                    // remove drag marker
                                    removeDragMarker();
                                }
                            }
                        };

                        _mapView.getMapEventDispatcher().addMapEventListener(
                                MapEvent.ITEM_DRAG_STARTED, longPressDrag);
                        _mapView.getMapEventDispatcher().addMapEventListener(
                                MapEvent.ITEM_DRAG_CONTINUED, longPressDrag);
                        _mapView.getMapEventDispatcher().addMapEventListener(
                                MapEvent.ITEM_DRAG_DROPPED, longPressDrag);

                        _mapView.getMapEventDispatcher()
                                .addMapEventListener(
                                        MapEvent.MAP_CLICK,
                                        new MapEventDispatcher.MapEventDispatchListener() {
                                            @Override
                                            public void onMapEvent(MapEvent e) {
                                                GeoPointMetaData gp = _mapView
                                                        .inverseWithElevation(
                                                                e.getPointF().x,
                                                                e.getPointF().y);
                                                EditAction act;
                                                if (insertPoint)
                                                    // Insert new point
                                                    act = _poly.new InsertPointAction(
                                                            gp,
                                                            indexToMove);

                                                else
                                                    // Move existing point
                                                    act = _poly.new MovePointAction(
                                                            indexToMove,
                                                            gp);
                                                run(act);

                                                _mapView.getMapEventDispatcher()
                                                        .popListeners();

                                                // anything else we need to do to update a point's location?

                                                // we're back to only one layer deep
                                                twoListenerPushesDeep = false;

                                                // remove text
                                                showMainPrompt();

                                                // remove drag marker
                                                removeDragMarker();
                                            }
                                        });

                    }
                });

        // listener for item long press on assoc markers OR on the line itself, insert a new point
        /*_mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_LONG_PRESS,
                new MapEventDispatcher.MapEventDispatchListener() {
                    @Override
                    public void onMapEvent(MapEvent event) {
                        MapItem marker = event.getItem();
        
                        if (marker.equals(_poly)
                                && marker.getMetaString("hit_type", "").equals(
                                        "line")) {
                            // do the insert
                            insertPoint(_poly,
                                    marker.getMetaInteger("hit_index", 0));
                        }
        
                    }
                });*/
        // create a listener for dragging items
        MapEventDispatcher.MapEventDispatchListener dragListener = new MapEventDispatcher.MapEventDispatchListener() {
            @Override
            public void onMapEvent(MapEvent event) {
                if (twoListenerPushesDeep)
                    return;
                GeoPointMetaData gp = _mapView.inverseWithElevation(
                        event.getPointF().x, event.getPointF().y);
                if (!gp.get().isValid())
                    return;
                MapItem mi = event.getItem();
                switch (event.getType()) {
                    case MapEvent.ITEM_DRAG_STARTED:
                        dragHitIndex = -1;
                        if (mi instanceof PointMapItem) {
                            PointMapItem pmi = (PointMapItem) mi;

                            // check if this point is in the route being edited, if not do nothing
                            if (!_poly.hasMarker(pmi))
                                return;

                            dragHitIndex = _poly.getIndexOfMarker(pmi);
                            _poly.setMetaInteger("hit_index", dragHitIndex);
                        } else if (mi.equals(_poly) && _poly.getMetaString(
                                "hit_type", "").equals("point")) {
                            if (multipleVerticesHit())
                                return;
                            dragHitIndex = _poly.getMetaInteger("hit_index",
                                    -1);
                        }

                        if (dragHitIndex != -1) {
                            // Unfocus the map to dismiss a possible FINE_ADJUST action
                            Intent intent = new Intent();
                            intent.setAction(
                                    "com.atakmap.android.maps.UNFOCUS");
                            AtakBroadcast.getInstance().sendBroadcast(intent);
                            // If we are dragging
                            startPoint = _poly.getPoint(dragHitIndex);
                            _poly.setMetaBoolean("dragInProgress", true);
                        } else if (event.getExtras() != null)
                            event.getExtras().putBoolean("eventNotHandled",
                                    true);
                        break;
                    case MapEvent.ITEM_DRAG_CONTINUED:
                        if (dragHitIndex != -1)
                            _poly.setPoint(dragHitIndex, gp);
                        break;
                    case MapEvent.ITEM_DRAG_DROPPED:
                        // Use saved hitIndex because on on mapItem may have changed (_testOrthoHit is
                        // called on release)
                        _poly.removeMetaData("dragInProgress");
                        if (dragHitIndex != -1) {
                            _poly.setPoint(dragHitIndex, gp, false);
                            EditAction act = _poly.new MovePointAction(
                                    dragHitIndex, startPoint,
                                    gp);
                            run(act);
                        }
                        break;
                }
            }
        };
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_DRAG_STARTED,
                dragListener);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_DRAG_CONTINUED,
                dragListener);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_DRAG_DROPPED,
                dragListener);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_SCALE, this);

        // XXX - not carrying this code over as it appears that the
        // MapGroup is never set on the event during an ITEM_CLICK

        // enable menus when tapping on the lines in the association set while editing
        /*
         * _poly.getChildItemMapGroup().addOnMapEventListener(new OnMapEventListener() {
         * @Override public void onMapGroupMapEventOccurred(MapEvent mapEvent) { MapItem item =
         * mapMapEvent.getItem(event); if (mapEvent.getType().equals(MapEvent.ITEM_CLICK) && item instanceof
         * Association) { Intent showMenu = new Intent();
         * showMenu.setAction("com.atakmap.android.maps.SHOW_MENU"); showMenu.putExtra("uid",
         * item.getData().getString("uid")); } } });
         */
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (event.getType().equals(MapEvent.MAP_SCALE)) {
            boolean vertsVisible = _poly.shouldDisplayVertices(
                    _mapView.getMapScale());
            if (_vertsVisible != vertsVisible) {
                _vertsVisible = vertsVisible;
                // Show the "zoom in" prompt so the user knows
                showMainPrompt();
            }
        }
    }

    protected void insertPoint(EditablePolyline route, int index) {

        twoListenerPushesDeep = true;

        _insertPointIndex = index + 1;

        // Tell the user we're ready to insert
        container.displayPrompt(TAP_PROMPT);

        // push all the dispatch listeners
        route.mapView.getMapEventDispatcher().pushListeners();

        // clear all the listeners listening for a click
        clearExtraListeners();

        // place a waypoint where the user long tapped
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_LONG_PRESS,
                new MapEventDispatcher.MapEventDispatchListener() {
                    @Override
                    public void onMapEvent(MapEvent event) {
                        // They're defining the route, drop a visible waypoint
                        if (event.getItem() == null) {
                            GeoPointMetaData geoPoint = _mapView
                                    .inverseWithElevation(
                                            event.getPointF().x,
                                            event.getPointF().y);

                            EditAction act = _poly.new InsertPointAction(
                                    geoPoint,
                                    _insertPointIndex);
                            run(act);

                            twoListenerPushesDeep = false;
                            showMainPrompt();
                            _mapView.getMapEventDispatcher().popListeners();
                        }
                    }
                });

        // on a non-long map press place an invisible waypoint
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK,
                new MapEventDispatcher.MapEventDispatchListener() {
                    @Override
                    public void onMapEvent(MapEvent event) {
                        // They're defining the route, drop an invisible waypoint
                        if (event.getItem() == null) {

                            GeoPointMetaData geoPoint = _mapView
                                    .inverseWithElevation(
                                            event.getPointF().x,
                                            event.getPointF().y);

                            EditAction act = _poly.new InsertPointAction(
                                    geoPoint,
                                    _insertPointIndex);
                            run(act);

                            twoListenerPushesDeep = false;
                            showMainPrompt();
                            _mapView.getMapEventDispatcher().popListeners();
                        }
                    }
                });

        // add our own listener
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK,
                new MapEventDispatcher.MapEventDispatchListener() {
                    @Override
                    public void onMapEvent(MapEvent event) {

                        // Get device uid to click on yourself
                        final String deviceUID = _mapView.getSelfMarker()
                                .getUID();

                        // Don't want to do anything special if the user taps a big shape,
                        // but incorporating new function of using item point only if item is
                        // yourself for survey tools consistency
                        if (event.getItem() instanceof PointMapItem
                                && event.getItem().getUID().equals(deviceUID)) {
                            // get user gps data
                            GeoPointMetaData geoPoint = ((PointMapItem) event
                                    .getItem())
                                            .getGeoPointMetaData();

                            EditAction act = _poly.new InsertPointAction(
                                    geoPoint,
                                    _insertPointIndex);
                            run(act);

                            twoListenerPushesDeep = false;
                            showMainPrompt();
                            _mapView.getMapEventDispatcher().popListeners();

                            event.getExtras().putBoolean("eventNotHandled",
                                    false);
                        } else {
                            // Tell touch controller we didn't handle item clicks so we get the map
                            // click instead.
                            event.getExtras().putBoolean("eventNotHandled",
                                    true);
                        }
                    }
                });
    }

    // If set is deleted, stop editing it!
    private final MapItem.OnGroupChangedListener associationSetDeletionListener = new MapItem.OnGroupChangedListener() {

        @Override
        public void onItemAdded(MapItem item, MapGroup newParent) {
        }

        @Override
        public void onItemRemoved(MapItem item, MapGroup oldParent) {
            requestEndTool();
        }
    };

    // TODO: push this into it's own tool? or something to handle button enabled state to disable
    // when there's nothing to undo.
    // how do we deal with each tool (edit/wrap) needing it's own undo tool though where there
    // might only be one button?
    // maybe there's one tool that queries the active tool?
    // AHA: Or maybe, RouteMapReceiver / the model portion of it if it's refactored should be
    // responsible for being undoable?

    @Override
    public boolean run(EditAction action) {
        final boolean active = getActive();
        if (active) {
            Undoable undo = _poly.getUndoable();
            if (undo != null && undo != this) {
                // In case the undoable has been set to something else
                return undo.run(action);
            }
            synchronized (actionLock) {
                boolean success = action.run();
                if (success) {
                    undoStack.push(action);
                    _mapView.post(new Runnable() {
                        @Override
                        public void run() {
                            if (_undoButton != null)
                                _undoButton.setEnabled(true);
                        }
                    });
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
            Undoable undo = _poly.getUndoable();
            if (undo != null && undo != this) {
                // In case the undoable has been set to something else
                undo.undo();
            } else {
                synchronized (actionLock) {
                    if (undoStack.size() > 0) {
                        try {
                            undoStack.pop().undo();
                        } catch (Exception e) {
                            Log.d(TAG, "error occurred attempting to undo.", e);
                        }
                    }

                    if (undoStack.size() == 0) {
                        // disable undo button; undo stack is empty
                        _mapView.post(new Runnable() {
                            @Override
                            public void run() {
                                if (_undoButton != null)
                                    _undoButton.setEnabled(false);
                            }
                        });
                    }
                }
                // In case the radial is still opened on the deleted point
                AtakBroadcast.getInstance().sendBroadcast(
                        new Intent(MapMenuReceiver.HIDE_MENU));
            }
        }
    }

    private void _scaleToFit(EditablePolyline route, int width, int height) {

        int numPoints = route.getNumPoints();

        GeoPointMetaData[] points = new GeoPointMetaData[numPoints];

        for (int i = 0; i < route.getNumPoints(); i++) {
            points[i] = route.getPoint(i);
        }

        GeoPoint center = GeoCalculations.centerOfExtremes(points, 0,
                points.length,
                _mapView.isContinuousScrollEnabled());

        // error has occurred computing the center of extremes
        if (center == null) {
            Log.e(TAG, "error has occurred computing the center of extremes");
            return;
        }
        CameraController.Programmatic.panTo(_mapView.getRenderer3(), center,
                true);

        try {
            // get the extremes in pixel-size so we can zoom to that size
            int[] e = GeoCalculations.findExtremes(points, 0, points.length,
                    false);
            PointF northWest = _mapView.forward(new GeoPoint(points[e[1]].get()
                    .getLatitude(),
                    points[e[0]].get()
                            .getLongitude()));
            PointF southEast = _mapView.forward(new GeoPoint(points[e[3]].get()
                    .getLatitude(),
                    points[e[2]].get()
                            .getLongitude()));

            double padding = width / 4d;
            width -= padding;
            height -= padding;
            double modelWidth = Math.abs(northWest.x - southEast.x);
            double modelHeight = Math.abs(northWest.y - southEast.y);

            double zoomFactor = width / modelWidth;
            if (zoomFactor * modelHeight > height) {
                zoomFactor = height / modelHeight;
            }

            PointF p = _mapView.forward(center);

            _mapView.getMapController().zoomBy(zoomFactor, p.x, p.y, true);
        } catch (Exception e) {
            // in the unlikely event that the result of findExtremes produces a value 
            // that is -1, go ahead and just catch the error and trod along.
        }
    }

    private void removeDragMarker() {
        if (dragMarker != null)
            dragMarker.removeFromGroup();
        dragMarker = null;
    }

    protected boolean multipleVerticesHit() {
        int hitCount = _poly.getMetaInteger("hit_count", 0);
        if (hitCount > 1 && _poly.getMetaString("hit_type", "")
                .equals("point")
                && !_poly.shouldDisplayVertices(
                        _mapView.getMapScale())) {
            // Vertices not visible but multiple points hit
            Toast.makeText(_context, "please zoom in",
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }
}
