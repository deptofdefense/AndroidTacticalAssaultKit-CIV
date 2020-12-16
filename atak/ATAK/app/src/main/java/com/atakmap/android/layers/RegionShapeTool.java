
package com.atakmap.android.layers;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import com.atakmap.android.drawing.DrawingPreferences;
import com.atakmap.android.drawing.DrawingToolsMapComponent;
import com.atakmap.android.drawing.mapItems.DrawingRectangle;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.tools.ActionBarView;
import com.atakmap.android.util.EditAction;
import com.atakmap.android.util.Undoable;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.Stack;
import java.util.UUID;

/**
 * Tool for the creation of a rectangular map region.
 *
 * Once the user has finished creating a shape with the tool, returns the uid
 * of the newly created Shape in an intent specified by the user by setting
 * the "callback" parcelable when starting the tool.   This is an 
 * intent to fire when the tool is completed.
 */
public class RegionShapeTool extends Tool implements Undoable,
        MapEventDispatcher.MapEventDispatchListener,
        View.OnClickListener {

    public static final String TOOL_ID = "com.atakmap.android.layers.RegionShapeTool";

    public static final String TAG = "RegionShapeTool";

    private final MapView _mapView;
    private final Context _context;
    private final DrawingPreferences _prefs;
    private final ActionBarView _toolbar;
    private final Button _undoButton, _endButton;
    private final Stack<EditAction> _undoStack = new Stack<>();
    private MapGroup _drawingGroup;
    private boolean _freeform;
    private Intent _callback;
    private DrawingShape _shape;
    private Marker _firstPoint;
    private DrawingRectangle.Builder _builder;

    public RegionShapeTool(MapView mapView) {
        super(mapView, TOOL_ID);
        _mapView = mapView;
        _context = mapView.getContext();
        _prefs = new DrawingPreferences(mapView);
        _toolbar = (ActionBarView) LayoutInflater.from(_context).inflate(
                R.layout.drawing_toolbar_view, _mapView, false);
        for (int i = 0; i < _toolbar.getChildCount(); i++)
            _toolbar.getChildAt(i).setVisibility(View.GONE);
        _undoButton = _toolbar.findViewById(R.id.undoButton);
        _undoButton.setVisibility(View.VISIBLE);
        _undoButton.setOnClickListener(this);
        _endButton = _toolbar.findViewById(R.id.doneButton);
        _endButton.setVisibility(View.VISIBLE);
        _endButton.setOnClickListener(this);
        ToolManagerBroadcastReceiver.getInstance().registerTool(TOOL_ID, this);
    }

    @Override
    public void dispose() {
        ToolManagerBroadcastReceiver.getInstance().unregisterTool(TOOL_ID);
    }

    @Override
    protected boolean onToolBegin(Bundle extras) {
        _drawingGroup = DrawingToolsMapComponent.getGroup();
        _freeform = extras.getBoolean("freeform", false);
        _callback = extras.getParcelable("callback");
        _builder = null;
        _shape = null;

        _mapView.getMapEventDispatcher().pushListeners();
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.ITEM_CLICK);
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_CLICK);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, this);
        _mapView.getMapTouchController().skipDeconfliction(true);

        _undoStack.clear();
        _undoButton.setEnabled(false);
        ActionBarReceiver.getInstance().setToolView(_toolbar);
        TextContainer.getInstance().displayPrompt(_context.getString(
                _freeform ? R.string.region_select_route_prompt
                        : R.string.select_area_prompt));
        DropDownManager.getInstance().hidePane();

        return super.onToolBegin(extras);
    }

    @Override
    protected void onToolEnd() {
        if (_callback != null) {
            Shape shp = null;
            if (_freeform && _shape != null) {
                _shape.removeMarker(_firstPoint);
                if (_firstPoint != null)
                    _drawingGroup.removeItem(_firstPoint);
                shp = _shape;
            } else if (_builder != null) {
                try {
                    _drawingGroup.addItem(shp = _builder.build());
                } catch (IndexOutOfBoundsException eb) {
                    Log.e(TAG,
                            "rectangle is not defined correctly, builder is not valid");
                    _builder.dispose();
                    if (_firstPoint != null) {
                        MapGroup mg = _firstPoint.getGroup();
                        if (mg != null)
                            mg.removeItem(_firstPoint);

                    }
                }
            }
            Intent i = new Intent(_callback);
            if (shp != null) {
                shp.setMetaBoolean("addToObjList", false);
                shp.setMetaBoolean("layerDownload", true);
                shp.setMetaBoolean("nevercot", true);
                i.putExtra("uid", shp.getUID());
            }

            AtakBroadcast.getInstance().sendBroadcast(i);
        }
        TextContainer.getInstance().closePrompt();
        _mapView.getMapEventDispatcher().popListeners();
        _mapView.getMapTouchController().skipDeconfliction(false);
        _undoStack.clear();
        _undoButton.setEnabled(false);
        DropDownManager.getInstance().unHidePane();
        ActionBarReceiver.getInstance().setToolView(null, false);
        super.onToolEnd();
    }

    @Override
    public void onClick(View v) {
        if (v == _undoButton)
            undo();
        else if (v == _endButton)
            requestEndTool();
    }

    @Override
    public boolean run(EditAction action) {
        final boolean active = getActive();
        if (active) {
            synchronized (_undoStack) {
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
            synchronized (_undoStack) {
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

    @Override
    public void onMapEvent(MapEvent event) {
        GeoPointMetaData point = findPoint(event);
        if (point == null)
            return;
        if (_freeform) {
            if (_shape != null && _firstPoint != null
                    && event.getItem() == _firstPoint) {
                // Close shape
                if (_shape.getNumPoints() < 3)
                    return;
                _shape.setClosed(true);
                int c = _shape.getStrokeColor();
                int alpha = _prefs.getFillAlpha();
                _shape.setColor(c);
                _shape.setFillColor(Color.argb(alpha, Color.red(c),
                        Color.green(c), Color.blue(c)));
                requestEndTool();
            } else
                run(new AddPointAction(point));
        } else {
            if (_builder == null) {
                String name = _context.getString(R.string.select_area);
                MapGroup grp = _drawingGroup.addGroup(name);
                grp.setMetaBoolean("addToObjList", false);
                _builder = new DrawingRectangle.Builder(grp,
                        DrawingRectangle.Builder.Mode.START_END_CORNERS);
                _builder.setFirstPoint(point);
                TextContainer.getInstance().displayPrompt(_context.getString(
                        R.string.select_area_prompt2));
            } else {
                _builder.setSecondPoint(point);
                requestEndTool();
            }
        }
    }

    private class AddPointAction extends EditAction {

        private final GeoPointMetaData _point;

        private AddPointAction(GeoPointMetaData gp) {
            _point = gp;
        }

        @Override
        public boolean run() {
            if (_shape == null) {
                _shape = new DrawingShape(_mapView, _drawingGroup,
                        UUID.randomUUID().toString());
                _shape.setMetaBoolean("addToObjList", false);
                _shape.addPoint(_point);
                _firstPoint = new Marker(_point, UUID.randomUUID().toString());
                _firstPoint.setType("shape_marker");
                _firstPoint.setMetaBoolean("addToObjList", false);
                _firstPoint.setMetaBoolean("nevercot", true);
                _shape.setMarker(0, _firstPoint);
                _shape.setColor(_prefs.getShapeColor());
                _shape.setStrokeWeight(_prefs.getStrokeWeight());
                _drawingGroup.addItem(_firstPoint);
                _drawingGroup.addItem(_shape);
                return true;
            }
            _shape.addPoint(_point);
            return true;
        }

        @Override
        public void undo() {
            if (_shape == null || _firstPoint == null)
                return;
            int numPoints = _shape.getNumPoints();
            if (numPoints > 1)
                _shape.removePoint(numPoints - 1);
            else {
                if (_shape != null)
                    _drawingGroup.removeItem(_shape);
                if (_firstPoint != null)
                    _drawingGroup.removeItem(_firstPoint);
                _shape = null;
                _firstPoint = null;
            }
        }

        @Override
        public String getDescription() {
            return "Undo a step in the creation process";
        }
    }
}
