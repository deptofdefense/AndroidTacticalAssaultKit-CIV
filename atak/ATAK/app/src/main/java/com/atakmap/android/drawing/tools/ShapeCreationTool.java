
package com.atakmap.android.drawing.tools;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import android.content.Intent;

import com.atakmap.android.drawing.DrawingPreferences;
import com.atakmap.android.drawing.DrawingToolsMapReceiver;
import com.atakmap.android.drawing.DrawingToolsToolbar;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.user.CamLockerReceiver;
import com.atakmap.android.util.EditAction;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.Stack;
import java.util.UUID;

/**
 * Tool for the creation of a (possibly incomplete) polygonal map region.
 *
 * Once the user has finished creating a shape with the tool, returns the uid
 * of the newly created Shape in an intent specified by the user by setting
 * the "callback" parcelable when starting the tool.   This is an
 * intent to fire when the tool is completed.
 */
public class ShapeCreationTool extends Tool {

    protected final Stack<EditAction> undoStack = new Stack<>();

    public static final String TAG = "ShapeCreationTool";
    protected final MapGroup _drawingGroup;
    private DrawingShape _shape;
    private String _shapeTitle;
    private int _color = Color.WHITE;
    private int _fillColor = Color.argb(150, 255, 255, 255); // Transparent white
    private final static int lineStyle = 0;

    // The first marker placed in the shape
    private Marker _startMarker;

    // The first marker clicked - for closing the shape when we tap that marker
    private PointMapItem _firstClicked;

    private Intent _callback;

    private final DrawingToolsToolbar _drawingToolsToolbar;
    private final TextContainer _container;
    private final ImageButton _shapeButton;
    private final Button _undoButton;
    private final Button _doneButton;
    private final MapView _mapView;
    private final DrawingPreferences _prefs;

    public static final String TOOL_IDENTIFIER = "com.atakmap.android.drawing.SHAPE_TOOL";

    public ShapeCreationTool(MapView mapView, MapGroup drawingGroup,
            ImageButton shapeButton,
            DrawingToolsToolbar drawingToolsToolbar,
            Button undoButton,
            Button doneButton,
            Context ignored) {
        super(mapView, TOOL_IDENTIFIER);

        _mapView = mapView;
        _prefs = new DrawingPreferences(_mapView);
        _drawingGroup = drawingGroup;
        _drawingToolsToolbar = drawingToolsToolbar;
        _shapeButton = shapeButton;
        _doneButton = doneButton;
        _undoButton = undoButton;
        _container = TextContainer.getInstance();

        initButton();
    }

    @Override
    public void dispose() {
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return super.onKey(v, keyCode, event);
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        super.onToolBegin(extras);
        _callback = extras.getParcelable("callback");

        Intent intent = new Intent();

        intent.setAction(CamLockerReceiver.UNLOCK_CAM);
        AtakBroadcast.getInstance().sendBroadcast(intent);

        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(
                        ActionBarReceiver.REFRESH_ACTION_BAR));

        _container.displayPrompt(_mapView.getResources().getString(
                R.string.polygon_prompt));

        _drawingToolsToolbar.toggleShapeButtons(false);
        _shapeButton.setVisibility(Button.GONE);
        _doneButton.setVisibility(Button.VISIBLE);
        _doneButton.setEnabled(true);
        _undoButton.setVisibility(Button.VISIBLE);
        _undoButton.setEnabled(false);
        _firstClicked = null;

        //Pushes the listeners so we can catch the events first
        _mapView.getMapEventDispatcher().pushListeners();

        //Clear listerners we do not want
        this.clearExtraListeners();

        // find first unused shape name; start with the count of the new shape
        int count = 1;
        for (MapItem i : _drawingGroup.getItems()) {
            if (i instanceof DrawingShape) {
                count++;
            }
        }

        // then find the first unused name from there on
        while (_drawingGroup.findItem("title", getTitlePrefix()
                + " " + count) != null)
            count++;

        // Fix for bug 6244: Shape creation is now delayed until the initial point is created. The
        // old implementation tried to keep reusing the same shape object, but that object would be
        // deleted if creating the first point is undone. Continuing to create a shape from there
        // would cause the deleted shape to be reused, which causes the bug to occur since the shape
        // cannot be re-deleted later. This way, you get a fresh shape object every time you start a
        // new shape, be it because you undid the creation or are just creating a new one.
        _shapeTitle = getTitlePrefix() + " " + count;

        // set up the gestures that will be used to place the point
        _mapView.getMapEventDispatcher()
                .addMapEventListener(MapEvent.MAP_CLICK, clickListener);
        _mapView.getMapEventDispatcher()
                .addMapEventListener(MapEvent.MAP_LONG_PRESS, clickListener);
        _mapView.getMapEventDispatcher()
                .addMapEventListener(MapEvent.ITEM_CLICK, itemClickListener);

        // Use first hit item
        _mapView.getMapTouchController().skipDeconfliction(true);

        // for deconfliction icon
        _mapView.getMapTouchController().setToolActive(true);
        return true;
    }

    private void initShape(String title) {
        _shape = new DrawingShape(_mapView, _drawingGroup, UUID.randomUUID()
                .toString());
        _shape.setTitle(title);
        _color = _prefs.getShapeColor();
        _shape.setColor(_color);
        _shape.setStrokeWeight(_prefs.getStrokeWeight());
        _shape.setStrokeStyle(_prefs.getStrokeStyle());
        _fillColor = _prefs.getFillColor();

        _shape.setEditable(false);
        _shape.setMetaString("entry", "user");
        _shape.setMetaBoolean("creating", true);
        _drawingGroup.addItem(_shape);
    }

    private void addStartMarker(GeoPointMetaData point) {
        _undoButton.setEnabled(true);
        removeStartMarker();
        initShape(_shapeTitle);
        if (_startMarker == null) {
            _startMarker = new Marker(point, UUID.randomUUID().toString());
            _startMarker.setType("b-m-p-w");
            _startMarker.setMetaBoolean("drag", false);
            _startMarker.setMetaBoolean("editable", true);
            _startMarker.setMetaBoolean("addToObjList", false); // always hide these in overlays
            _startMarker.setMetaString("how", "h-g-i-g-o"); // don't autostale it
            _startMarker.setZOrder(Double.NEGATIVE_INFINITY);
        }
        if (!_drawingGroup.containsItem(_shape))
            _drawingGroup.addItem(_shape);
        _drawingGroup.addItem(_startMarker);

        run(_shape.new InsertPointAction(_startMarker));
    }

    private void removeStartMarker() {
        if (_startMarker != null) {
            if (_shape != null)
                _shape.removeMarker(_startMarker);

            if (_drawingGroup.containsItem(_startMarker))
                _drawingGroup.removeItem(_startMarker);
            _startMarker = null;
        }
    }

    private final MapEventDispatcher.MapEventDispatchListener clickListener = new MapEventDispatcher.MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            // move to the point user tapped
            if (undoStack.size() > 0) {
                _undoButton.setEnabled(true);
            }
            GeoPointMetaData geoPoint = _mapView.inverseWithElevation(
                    event.getPointF().x, event.getPointF().y);
            _handlePoint(geoPoint);
        }
    };

    private final MapEventDispatcher.MapEventDispatchListener itemClickListener = new MapEventDispatcher.MapEventDispatchListener() {

        @Override
        public void onMapEvent(MapEvent event) {
            MapItem item = event.getItem();
            if (_shape != null && _shape.getNumPoints() >= 3
                    && (item == _startMarker || item == _firstClicked
                            && _firstClicked.getPoint()
                                    .equals(_startMarker.getPoint()))) {
                // close and end!
                event.getExtras().putBoolean("eventNotHandled", false);
                _shape.setLineStyle(lineStyle);
                _shape.setStrokeStyle(_prefs.getStrokeStyle());
                _shape.setFilled(true);
                _shape.setClosed(true);
                _shape.setColor(_color);
                _shape.setFillColor(_fillColor);
                if (_shape.getShapeMarker() != null)
                    _shape.getShapeMarker().refresh(_mapView
                            .getMapEventDispatcher(), null, getClass());
                requestEndTool();
                // check for pointmapitem to narrow down the click search, then check the device uid
                // string
            } else if (item instanceof PointMapItem) {
                PointMapItem pmi = (PointMapItem) item;
                if (_shape == null)
                    _firstClicked = pmi;
                GeoPointMetaData point = pmi.getGeoPointMetaData();
                _handlePoint(point);
                event.getExtras().putBoolean("eventNotHandled", false);
            } else {
                event.getExtras().putBoolean("eventNotHandled", true);
            }
        }
    };

    @Override
    public void onToolEnd() {
        super.onToolEnd();

        String shapeUID = null;
        if (_shape != null) {
            // If shape has < 2 points, remove it
            if (_shape.getNumPoints() < 2) {
                _drawingGroup.removeItem(_shape);
                removeStartMarker();
            } else {
                // hide first point
                removeStartMarker();

                // If properly built then show the details for the item
                Intent intent = new Intent();
                intent.setAction(DrawingToolsMapReceiver.ZOOM_ACTION);
                intent.putExtra(DrawingToolsMapReceiver.EXTRA_CREATION_MODE,
                        true);
                intent.putExtra("uid", _shape.getUID());
                AtakBroadcast.getInstance().sendBroadcast(intent);
                _shape.removeMetaData("creating");
                _shape.persist(_mapView.getMapEventDispatcher(), null,
                        this.getClass());
                shapeUID = _shape.getUID();
            }
            _shape = null;
        }
        _firstClicked = null;

        // Broadcast that the tool has completed its action
        if (_callback != null) {
            Intent i = new Intent(_callback);
            if (shapeUID != null)
                i.putExtra("uid", shapeUID);
            AtakBroadcast.getInstance().sendBroadcast(i);
        }

        _drawingToolsToolbar.toggleShapeButtons(true);
        _undoButton.setVisibility(Button.GONE);
        _undoButton.setEnabled(false);
        _doneButton.setVisibility(Button.GONE);
        _doneButton.setEnabled(false);
        _shapeButton.setVisibility(Button.VISIBLE);

        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(
                        ActionBarReceiver.REFRESH_ACTION_BAR));

        _mapView.getMapEventDispatcher().clearListeners();
        _mapView.getMapEventDispatcher().popListeners();
        _mapView.getMapTouchController().skipDeconfliction(false);
        _mapView.getMapTouchController().setToolActive(false);

        //Close the display prompt
        _container.closePrompt();

        // clear undo stack
        undoStack.clear();
    }

    @Override
    public boolean shouldEndOnBack() {
        return true;
    }

    public boolean run(EditAction action) {
        if (getActive()) {
            boolean success = action.run();
            if (success) {
                undoStack.push(action);
            }

            return success;
        }

        // somehow an edit tried to happen while the tool isn't running
        return false;
    }

    public void undo() {
        if (getActive() && undoStack.size() > 0) {
            undoStack.pop().undo();
        }
        if (undoStack.size() == 0) {
            _undoButton.setEnabled(false);
        }
    }

    private String getTitlePrefix() {
        return "Shape";
    }

    private void _handlePoint(GeoPointMetaData point) {
        // Place a new point where user tapped

        if (_shape == null || _shape.getNumPoints() == 0) {
            addStartMarker(point);
        } else {
            run(_shape.new InsertPointAction(point));
        }

        // This needs to come after addStartMarker now, since _shape is null until then
        if (_shape != null) {
            _shape.setLineStyle(lineStyle);
            _shape.setStrokeStyle(_prefs.getStrokeStyle());
        }
    }

    @Override
    protected void setActive(boolean active) {
        _shapeButton.setSelected(active);
        super.setActive(active);
    }

    @Override
    protected void clearExtraListeners() {
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_PRESS);
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_RELEASE);

        super.clearExtraListeners();
    }

    protected void initButton() {
        if (_shapeButton != null) {
            _shapeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requestBeginTool();
                }
            });
            _shapeButton.setOnLongClickListener(onLongClickListener);

            _doneButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requestEndTool();
                }
            });
        }
    }

    View.OnLongClickListener onLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
            Toast.makeText(_mapView.getContext(), R.string.shape_tip,
                    Toast.LENGTH_SHORT).show();
            return true;
        }
    };
}
